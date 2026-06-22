#!/usr/bin/env bash
#
# Daily mission generation — the AI "brain" driver (M3). Runs on the VPS via cron (04:30 UTC).
#
# Reads the mod's exported state (state/world_state.json + state/context/<uuid>.json), asks headless
# Claude Code to author 3 daily missions per known player in the CustomMissions DSL, writes them to the
# daily/<UTC-date>/<uuid>/ drop, then triggers an in-game reload. The mod's DailyIngestTask assigns them
# to each player on their next login. Brain-swappable: replace the `claude -p` block with any generator
# that writes the same JSON (e.g. an Anthropic API sidecar) and nothing else changes.
#
# AUTH: needs headless Claude Code logged in as the cron user (root). Either:
#   - interactive once:  ssh -t mcserver claude   then  /login        (persists /root/.claude credentials)
#   - or a token:        put  CLAUDE_CODE_OAUTH_TOKEN=...  in /etc/mcserver/claude.env (chmod 600)
set -uo pipefail

SERVER_DIR="${SERVER_DIR:-/opt/crafty/servers/19aa7f07-e3bd-481d-b029-1f3f6eac5f28}"
GEN_DIR="${GEN_DIR:-/opt/custommissions-gen}"     # this script + PROMPT.md + DSL.md live here on the VPS
CFG="$SERVER_DIR/config/custommissions"
STATE="$CFG/state"
TODAY="$(date -u +%F)"
DEST="$CFG/daily/$TODAY"
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

PROMPT="$(cat "$GEN_DIR/PROMPT.md" 2>/dev/null || true)"
DSL="$(cat "$GEN_DIR/DSL.md" 2>/dev/null || true)"
WS="$(cat "$WORLD_STATE")"
[ -n "$PROMPT" ] || { log "missing $GEN_DIR/PROMPT.md; abort"; exit 0; }

shopt -s nullglob
made=0
# Generate for every player that has an exported context (i.e. has played), not just whoever is online now.
for ctx in "$STATE"/context/*.json; do
    uuid="$(jq -r '.player.uuid // empty' "$ctx")"
    name="$(jq -r '.player.name // empty' "$ctx")"
    [ -n "$uuid" ] && [ -n "$name" ] || { log "skip unreadable context $ctx"; continue; }
    CTX="$(cat "$ctx")"
    out="$DEST/$uuid"
    mkdir -p "$out"

    log "generating dailies for $name ($uuid)"
    resp="$(claude -p "$PROMPT

=== WORLD_STATE (LIVE source of truth) ===
$WS

=== PLAYER_CONTEXT ===
$CTX

=== DSL (the mission language) ===
$DSL

=== EXTRA CONTEXT (optional — read files under $GEN_DIR/context/ if useful) ===
You may read, with your tools, to enrich the missions:
  context/docs/npcs/*.md      mission givers (names, uuids, coords, personalities; the Traveler is the main giver)
  context/docs/lore-bible.md, context/docs/Story/   tone + current lore stage (do NOT reveal hidden plot)
  context/docs/infomods/      per-mod content catalog (real mob/item/biome/dimension ids + stats)
  context/player-stats/$uuid.json, context/player-advancements/$uuid.json   this player's raw stats/advancements
  context/state/world_state.json   LIVE truth (knownDimensions, lore stage, givers)
Docs may be STALE — use ONLY ids confirmed in context/state/world_state.json or vanilla minecraft: ids.

TASK: Output ONLY a JSON array of exactly 3 daily mission objects (no prose, no markdown code fences).
Each object must be valid per the DSL, with: \"category\":\"daily\", \"assignTo\":[\"$name\"],
\"expiryHours\":24, and a unique \"id\" like \"daily_${TODAY}_${name}_01\" (..._02, ..._03)." \
        --add-dir "$GEN_DIR/context" --add-dir "$SERVER_DIR/world" --add-dir "$SERVER_DIR/config/custommissions" \
        --allowedTools Read Glob Grep </dev/null \
        2>>"$GEN_DIR/gen.log")"

    # With file tools, Claude may wrap the array in narration/fences. Extract the outermost JSON array
    # (first '[' .. last ']') so prose around it doesn't matter.
    resp="$(printf '%s' "$resp" | python3 -c 'import sys; t=sys.stdin.read(); i=t.find("["); j=t.rfind("]"); sys.stdout.write(t[i:j+1] if (i>=0 and j>i) else "")')"

    if ! printf '%s' "$resp" | jq -e 'type=="array" and length>0' >/dev/null 2>&1; then
        log "  bad/empty AI output for $name (see $GEN_DIR/gen.log) — skipped"
        continue
    fi
    i=0
    while IFS= read -r mission; do
        i=$((i + 1))
        printf '%s\n' "$mission" | jq '.' > "$out/$(printf '%02d' "$i").json" 2>/dev/null || true
    done < <(printf '%s' "$resp" | jq -c '.[]')
    made=$((made + 1))
done

chown -R "$CRAFTY_UID:$CRAFTY_GID" "$CFG/daily" 2>/dev/null || true
mc-cmd 'mission reload' >/dev/null 2>&1 || log "warn: 'mc-cmd mission reload' failed (run it by hand)"
log "done: prepared dailies for $made player(s) on $TODAY"
