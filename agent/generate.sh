#!/usr/bin/env bash
#
# Daily mission generation — the AI "brain" driver (M3). Runs on the VPS via cron (04:30 UTC).
#
# Reads the mod's exported state (state/world_state.json + state/context/<uuid>.json), the curated knowledge
# packs (CATALOG.md = the modded content that exists here + what spawns vs not; CAST.md = the NPCs of
# Aincrad incl. Grandma the rumor-keeper; SPAWNS.md = LIVE spawn rules + difficulty multipliers, auto-built
# by build-knowledge.sh from the server's In Control config), plus per-player signals distilled from raw
# vanilla data: MOD-USAGE (what mods they play with), ADV (advancements done) and POWER (progression tier +
# milestones, used to scale difficulty). It asks headless Claude Code to author 3 daily missions per player,
# writes them to the daily/<UTC-date>/<uuid>/ drop, then triggers an in-game reload. The mod's
# DailyIngestTask assigns them to each player on their next login. Brain-swappable: replace the `claude -p`
# block with any generator that writes the same JSON (e.g. an Anthropic API sidecar) and nothing else changes.
#
# AUTH: needs headless Claude Code logged in as the cron user (root). Either:
#   - interactive once:  ssh -t mcserver claude   then  /login        (persists /root/.claude credentials)
#   - or a token:        put  CLAUDE_CODE_OAUTH_TOKEN=...  in /etc/mcserver/claude.env (chmod 600)
set -uo pipefail

SERVER_DIR="${SERVER_DIR:-/opt/crafty/servers/19aa7f07-e3bd-481d-b029-1f3f6eac5f28}"
GEN_DIR="${GEN_DIR:-/opt/custommissions-gen}"     # this script + PROMPT.md + DSL.md + CATALOG.md + CAST.md live here
CFG="$SERVER_DIR/config/custommissions"
STATE="$CFG/state"
STATS_DIR="${STATS_DIR:-$SERVER_DIR/world/stats}" # raw vanilla per-player stats (records MODDED ids too)
TODAY="$(date -u +%F)"
DEST="${BRAIN_DEST:-$CFG/daily/$TODAY}"   # BRAIN_DEST: override drop dir for cheap test runs
CRAFTY_UID="${CRAFTY_UID:-1000}"; CRAFTY_GID="${CRAFTY_GID:-0}"
export HOME="${HOME:-/root}"                       # cron: claude reads ~/.claude credentials
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"
[ -f /etc/mcserver/claude.env ] && . /etc/mcserver/claude.env   # optional CLAUDE_CODE_OAUTH_TOKEN

log() { echo "[$(date -u '+%F %H:%M:%S')] $*"; }

command -v claude >/dev/null 2>&1 || { log "no 'claude' binary on PATH; abort"; exit 0; }
command -v jq     >/dev/null 2>&1 || { log "no 'jq' on PATH; abort"; exit 0; }
command -v mc-cmd >/dev/null 2>&1 || { log "no 'mc-cmd' on PATH; abort"; exit 0; }

WORLD_STATE="$STATE/world_state.json"
[ -f "$WORLD_STATE" ] || { log "no world_state.json yet (is the server up + the mod running?)"; exit 0; }

# Refresh the live spawn/difficulty knowledge pack (SPAWNS.md) from the server's In Control rules etc. so the
# brain always authors against CURRENT spawns + difficulty buffs. Skip with NO_KNOWLEDGE_REFRESH=1.
if [ -z "${NO_KNOWLEDGE_REFRESH:-}" ] && [ -x "$GEN_DIR/build-knowledge.sh" ]; then
    "$GEN_DIR/build-knowledge.sh" >/dev/null 2>>"$GEN_DIR/gen.log" || log "warn: build-knowledge.sh failed (using stale SPAWNS.md)"
fi

PROMPT="$(cat "$GEN_DIR/PROMPT.md"  2>/dev/null || true)"
DSL="$(cat "$GEN_DIR/DSL.md"        2>/dev/null || true)"
CATALOG="$(cat "$GEN_DIR/CATALOG.md" 2>/dev/null || true)"
CAST="$(cat "$GEN_DIR/CAST.md"      2>/dev/null || true)"
SPAWNS="$(cat "$GEN_DIR/SPAWNS.md"  2>/dev/null || true)"   # auto-built: what spawns where/when + difficulty buffs
WS="$(cat "$WORLD_STATE")"
[ -n "$PROMPT"  ] || { log "missing $GEN_DIR/PROMPT.md; abort"; exit 0; }
[ -n "$CATALOG" ] || log "warn: no $GEN_DIR/CATALOG.md — missions will only know vanilla ids"
[ -n "$CAST"    ] || log "warn: no $GEN_DIR/CAST.md — missions will not know the NPC cast"
[ -n "$SPAWNS"  ] || log "warn: no $GEN_DIR/SPAWNS.md — missions won't know live spawn rules/difficulty"

# Distill a player's MOD USAGE from their raw vanilla stats file. Vanilla records killed/used/mined/crafted
# with their real ids, INCLUDING modded ones — so this is the "which mods is this player actually into"
# signal that lets dailies lean into what they play with. Compact output; '{}' if no stats file.
mod_usage() {  # uuid -> compact JSON
    local f="$STATS_DIR/$1.json"
    [ -f "$f" ] || { echo '{}'; return; }
    python3 - "$f" <<'PY' 2>/dev/null || echo '{}'
import sys, json, collections
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print('{}'); sys.exit()
stats = d.get('stats', {})
cats = {'minecraft:killed':'killed', 'minecraft:used':'used',
        'minecraft:mined':'mined', 'minecraft:crafted':'crafted'}
ns_total = collections.Counter()
rows = []
for cat, label in cats.items():
    for k, v in stats.get(cat, {}).items():
        ns = k.split(':', 1)[0]
        if ns == 'minecraft' or not isinstance(v, int):
            continue
        ns_total[ns] += v
        rows.append((v, label, k))
rows.sort(reverse=True)
print(json.dumps({
    'favoriteMods': [f"{n} ({c})" for n, c in ns_total.most_common(6)],
    'topModdedActivity': [f"{label}:{k}={v}" for v, label, k in rows[:18]],
}))
PY
}

# Distill a player's advancements to the compact list of EARNED advancement ids, dropping the per-criterion
# timestamps that bloat the raw file to 200-300 KB (a veteran's is the single biggest input). Recipe-unlock
# advancements are noise, so they are excluded. Keeps the "what have they accomplished" signal at a fraction
# of the tokens. '{}' if no file.
adv_done() {  # uuid -> compact JSON {"done":[ids...]}
    local f="$GEN_DIR/context/player-advancements/$1.json"
    [ -f "$f" ] || { echo '{}'; return; }
    python3 - "$f" <<'PY' 2>/dev/null || echo '{}'
import sys, json
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print('{}'); sys.exit()
def is_recipe(k):  # recipe-unlock advancements (any namespace) are noise: '<ns>:recipes/...'
    path = k.split(':', 1)[1] if ':' in k else k
    return path.startswith('recipes/')
done = sorted(k for k, v in d.items()
              if isinstance(v, dict) and v.get('done') is True and not is_recipe(k))
print(json.dumps({'done': done}))
PY
}

# Distill a player's PROGRESSION / POWER from advancements + vanilla stats: how far along the game they are, so
# the brain can SCALE difficulty (a netherite-geared, dragon-slaying veteran gets harder mobs; a fresh player
# gets basics). We can't read live gear/enchant NBT here (no nbtlib on the host), but milestone advancements
# (netherite armor, enchanting, nether/end access, beacons, boss kills) are a strong, free proxy for "how
# equipped/advanced" a player is. Output: {tier, milestones[], mobKills, deaths}.
power_signal() {  # uuid -> compact JSON
    local af="$GEN_DIR/context/player-advancements/$1.json" sf="$STATS_DIR/$1.json"
    python3 - "$af" "$sf" <<'PY' 2>/dev/null || echo '{}'
import sys, json
def load(p):
    try: return json.load(open(p))
    except Exception: return {}
adv, st = load(sys.argv[1]), load(sys.argv[2]).get('stats', {})
done = set(k for k, v in adv.items() if isinstance(v, dict) and v.get('done') is True)
def has(*subs): return any(any(s in k for s in subs) for k in done)
checks = [
    ('netherite_gear', ['netherite_armor', 'obtain_netherite', 'unlock_netherite']),
    ('enchanting',     ['story/enchant_item', 'enchant_grave_key']),
    ('nether',         ['nether/root', 'explore_nether', 'find_fortress', 'obtain_blaze_rod']),
    ('beacon',         ['create_beacon', 'create_full_beacon']),
    ('wither',         ['summon_wither', 'get_wither_skull']),
    ('the_end',        ['end/root', 'enter_end', '/elytra', 'levitate', 'kill_dragon']),
    ('modded_boss',    ['block_factorys_bosses:kill', 'cataclysm', 'iceandfire/dragonbone']),
    ('totem',          ['totem_of_undying']),
    ('village_hero',   ['hero_of_the_village']),
]
milestones = [name for name, subs in checks if has(*subs)]
def cust(k): return int((st.get('minecraft:custom') or {}).get('minecraft:' + k, 0) or 0)
mob_kills, deaths = cust('mob_kills'), cust('deaths')
endgame = {'netherite_gear', 'beacon', 'the_end', 'wither'}
if endgame & set(milestones):              tier = 'endgame'
elif {'nether', 'enchanting'} & set(milestones) or mob_kills > 500: tier = 'mid'
elif done:                                 tier = 'early'
else:                                      tier = 'fresh'
print(json.dumps({'tier': tier, 'milestones': milestones, 'mobKills': mob_kills, 'deaths': deaths}))
PY
}

# Compact a player's raw vanilla stats to the top-N entries per category (by count). A veteran's raw stats file
# is ~50 KB of every counter ever touched; the brain only needs the highlights. Used by the context-compaction
# step below when a player's inline context blows past CONTEXT_BUDGET. '{}' if no file.
compact_stats() {  # uuid -> compact JSON: {killed:{...}, mined:{...}, used:{...}, crafted:{...}, custom:{...}}
    local f="$STATS_DIR/$1.json"
    [ -f "$f" ] || { echo '{}'; return; }
    python3 - "$f" <<'PY' 2>/dev/null || echo '{}'
import sys, json
try:
    stats = json.load(open(sys.argv[1])).get('stats', {})
except Exception:
    print('{}'); sys.exit()
out = {}
for cat in ('minecraft:killed', 'minecraft:mined', 'minecraft:used', 'minecraft:crafted', 'minecraft:custom'):
    rows = sorted(((v, k) for k, v in stats.get(cat, {}).items() if isinstance(v, int)), reverse=True)
    if rows:
        out[cat.split(':')[-1]] = {k: v for v, k in rows[:12]}
print(json.dumps(out))
PY
}

shopt -s nullglob
made=0
# Generate for every player that has an exported context (i.e. has played), not just whoever is online now.
# BRAIN_LIMIT (optional): cap how many players to generate for (cheap test runs / a spend ceiling).
for ctx in "$STATE"/context/*.json; do
    [ -n "${BRAIN_LIMIT:-}" ] && [ "$made" -ge "$BRAIN_LIMIT" ] && { log "BRAIN_LIMIT=$BRAIN_LIMIT reached; stopping"; break; }
    uuid="$(jq -r '.player.uuid // empty' "$ctx")"
    name="$(jq -r '.player.name // empty' "$ctx")"
    [ -n "$uuid" ] && [ -n "$name" ] || { log "skip unreadable context $ctx"; continue; }
    # ONLY_UUID (optional): regenerate for just one player (targeted re-run / verification).
    [ -n "${ONLY_UUID:-}" ] && [ "$uuid" != "$ONLY_UUID" ] && continue
    out="$DEST/$uuid"
    # ONLY_MISSING (optional): skip players who already have dailies today (fill gaps without churning the rest).
    [ -n "${ONLY_MISSING:-}" ] && ls "$out"/*.json >/dev/null 2>&1 && continue
    CTX="$(cat "$ctx")"
    # Curated, inline player signals (no agentic vault reads — keeps token cost ~flat per player).
    STATS="$(cat "$GEN_DIR/context/player-stats/$uuid.json" 2>/dev/null || echo '{}')"
    ADV="$(adv_done "$uuid")"
    USAGE="$(mod_usage "$uuid")"
    POWER="$(power_signal "$uuid")"

    # --- CONTEXT COMPACTION: keep the prompt focused (and cheap, esp. on sonnet) as a player's history grows ---
    # 1) Always collapse the unbounded list of completed DAILY ids to a count. Authored (primary/secondary)
    #    completions stay — they gate story logic and "don't repeat" rules; old daily ids are pure noise.
    CTX="$(printf '%s' "$CTX" | jq -c '
        if (.completedMissions | type) == "array" then
          (.completedMissions | map(select(type=="string" and startswith("daily_"))) | length) as $nd
          | .completedMissions = (.completedMissions | map(select(type=="string" and (startswith("daily_") | not))))
          | .completedDailiesCount = $nd
        else . end
    ' 2>/dev/null || printf '%s' "$CTX")"
    # 2) If the player's inline context is still large (a veteran's raw stats are ~50 KB), compact the OLD/bulky
    #    part: PLAYER_MOD_USAGE already distills the useful "what mods" signal, so shrink raw PLAYER_STATS to a
    #    top-N-per-category summary. Threshold via CONTEXT_BUDGET (bytes).
    CONTEXT_BUDGET="${CONTEXT_BUDGET:-20000}"
    ctx_bytes=$(( ${#CTX} + ${#STATS} + ${#ADV} + ${#USAGE} + ${#POWER} ))
    if [ "$ctx_bytes" -gt "$CONTEXT_BUDGET" ]; then
        before=${#STATS}
        STATS="$(compact_stats "$uuid")"
        log "  compacted context for $name: ${ctx_bytes}B > ${CONTEXT_BUDGET}B (raw stats ${before}B -> ${#STATS}B)"
    fi

    mkdir -p "$out"

    log "generating dailies for $name ($uuid)"
    PROMPT_FULL="$PROMPT

=== WORLD_STATE (LIVE source of truth: dimensions, gates, flags, givers, roster) ===
$WS

=== PLAYER_CONTEXT (who they are: description, reputation, dimension, stats, missions) ===
$CTX

=== PLAYER_MOD_USAGE (which mods THIS player actually engages with — tailor dailies to it) ===
$USAGE

=== PLAYER_STATS (vanilla stat snapshot; top-N per category when the player's history is large) ===
$STATS

=== PLAYER_ADVANCEMENTS ===
$ADV

=== PLAYER_POWER (how advanced/geared the player is — SCALE difficulty to this) ===
$POWER

=== CATALOG (modded content that exists here: WILD vs NOT-WILD mobs, items, structures, coins) ===
$CATALOG

=== SPAWNS (LIVE: what spawns where/when in each dimension + difficulty multipliers — auto-built from the server) ===
$SPAWNS

=== CAST (the NPCs of Aincrad: Flugel the giver, Grandma the rumor-keeper, the merchants) ===
$CAST

=== DSL (the mission language) ===
$DSL

=== RULES ===
All the context you need is inline above; do NOT call tools or read files.
- Use ONLY ids you can confirm here: dimensions/givers/flags in WORLD_STATE, ids listed in CATALOG/SPAWNS, and
  vanilla minecraft: ids. NEVER invent a modded id. When unsure, use a vanilla id or a custom_signal.
- BE CREATIVE. The authored secondary missions set the bar — bring that flavour to dailies. Avoid formulaic
  'kill N / walk to point' filler: give each a small hook, a named place or foe, a reason. Across a player's 3
  dailies use 3 DIFFERENT objective types, at least ONE using modded content from CATALOG, and vary the shape
  (combat, explore, build, gather, deliver, use, talk). Make the three feel like a varied little set, not a list.
- SCALE DIFFICULTY to PLAYER_POWER.tier and milestones (this is important):
    * fresh/early  -> gentle: weak WILD mobs, small counts, nearby places, Aincrad basics.
    * mid          -> moderate: tougher WILD mobs, modded gather/build, light exploration.
    * endgame (netherite/beacon/end/wither, or many milestones) -> HARD: the strongest WILD mobs in SPAWNS,
      bigger counts, far/hostile dimensions they've unlocked, and tasks worthy of a geared veteran. Do not hand
      a dragon-slayer 'punch 3 cows'. A high-death player can get a slightly easier set.
- Respect SPAWNS (live spawn truth): only kill_entity a mob that SPAWNS in a dimension the player can reach,
  and name where/when it spawns. NEVER target a NOT-WILD/denied boss/structure/summon mob (lore mention only).
  Mind the difficulty multipliers in SPAWNS (e.g. buffed overworld hostiles, the brutal Warden) when sizing counts.
- Tailor to the player: lean into PLAYER_MOD_USAGE (the mods they play with most) and honor
  player.description; a fresh player gets Aincrad basics, a heatdeath grinder gets heat-realm dailies.
- The grandma effect: let player.gossipBias and player.rumors colour the missions (positive bias = trusted
  favours that matter; negative = warier proving tasks). Never quote rumors or accuse the player.
- Gate by lore: prerequisites.loreStage must be <= the player's loreStage; never send them somewhere their
  story has not opened (check unlockedGates for non-overworld dimensions).
- One-session sized: reachable places near lastSeenPos/currentDimension; counts that fit the player's tier.
- REWARDS: every daily MUST grant (1) +1 player level via a command reward
  {\"type\":\"command\",\"command\":\"xp add {player} 1 levels\"} — NOT an 'xp' point reward; and (2) at least
  one lightmanscurrency:coin_copper. Add more coins for harder work (coin_iron, or coin_gold for a tougher
  objective) plus the occasional themed item. Never use the 'xp' reward type.
- Voice: Flugel the Traveler's, English, evocative but short. No 'not X, but Y' antithesis; state things
  directly. Call the overworld Aincrad in text (keep minecraft:overworld as the id). Reveal NO hidden plot.

TASK: Output ONLY a JSON array of exactly 3 daily mission objects (no prose, no markdown code fences).
Each object must be valid per the DSL, with: \"category\":\"daily\", \"assignTo\":[\"$name\"],
\"expiryHours\":24, and a unique \"id\" like \"daily_${TODAY}_${name}_01\" (..._02, ..._03)."

    # Feed the prompt on STDIN, never as an argv. The assembled prompt (a veteran's stats+advancements can be
    # 300+ KB) easily exceeds the kernel's 128 KiB single-argument limit (MAX_ARG_STRLEN) → exec fails with
    # E2BIG ("Argument list too long") and claude never runs, so that player silently gets no dailies. A pipe
    # has no such limit. (This was the bug that starved every veteran of dailies from v0.1.7 onward.)
    resp="$(printf '%s' "$PROMPT_FULL" | claude -p --model "${BRAIN_MODEL:-sonnet}" 2>>"$GEN_DIR/gen.log")"

    # Claude may still wrap the array in narration/fences. Extract the outermost JSON array
    # (first '[' .. last ']') so prose around it doesn't matter.
    resp="$(printf '%s' "$resp" | python3 -c 'import sys; t=sys.stdin.read(); i=t.find("["); j=t.rfind("]"); sys.stdout.write(t[i:j+1] if (i>=0 and j>i) else "")')"

    if ! printf '%s' "$resp" | jq -e 'type=="array" and length>0' >/dev/null 2>&1; then
        log "  bad/empty AI output for $name (see $GEN_DIR/gen.log) — skipped"
        continue
    fi
    i=0
    while IFS= read -r mission; do
        i=$((i + 1))
        # Normalize rewards deterministically (owner's rules): drop xp-point rewards, GUARANTEE a +1 player
        # level (vanilla "xp add ... levels" via a command reward — goes 2->3, 30->31 regardless of level) and
        # at least one copper coin, even if the model forgot or used the old xp reward.
        printf '%s\n' "$mission" | jq '
            .rewards = ((.rewards // []) | map(select(.type != "xp")))
            | if any(.rewards[]?; .type == "command" and ((.command // "") | test("xp add .* levels?")))
              then . else .rewards += [{"type":"command","command":"xp add {player} 1 levels"}] end
            | if any(.rewards[]?; .type == "item" and ((.item // "") | test("lightmanscurrency:coin_copper")))
              then . else .rewards += [{"type":"item","item":"lightmanscurrency:coin_copper","count":1}] end
        ' > "$out/$(printf '%02d' "$i").json" 2>/dev/null || true
    done < <(printf '%s' "$resp" | jq -c '.[]')
    made=$((made + 1))
done

chown -R "$CRAFTY_UID:$CRAFTY_GID" "$CFG/daily" 2>/dev/null || true
mc-cmd 'mission reload' >/dev/null 2>&1 || log "warn: 'mc-cmd mission reload' failed (run it by hand)"
log "done: prepared dailies for $made player(s) on $TODAY"
