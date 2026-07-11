#!/usr/bin/env bash
#
# build-knowledge.sh — compile the daily-mission brain's LIVE spawn/difficulty knowledge pack (SPAWNS.md)
# from the server's In Control spawn rules (config/incontrol/spawn.json). This is the "keep the brain updated
# on difficulty/spawn changes" tool: re-run it any time you change what spawns or how hard mobs are, and the
# next daily batch authors against the new truth. generate.sh also runs it automatically before each batch, so
# the brain is always current. Exposed to the admin as the `mc-missions-knowledge` helper.
#
# Deterministic (no LLM, no tokens): it just reformats the structured In Control rules into something the brain
# reads easily. CATALOG.md (hand-curated: which mobs are WILD, item/structure ids) complements this file.
set -uo pipefail

SERVER_DIR="${SERVER_DIR:-/opt/crafty/servers/19aa7f07-e3bd-481d-b029-1f3f6eac5f28}"
GEN_DIR="${GEN_DIR:-/opt/custommissions-gen}"
SPAWN_JSON="${SPAWN_JSON:-$SERVER_DIR/config/incontrol/spawn.json}"
OUT="${SPAWNS_OUT:-$GEN_DIR/SPAWNS.md}"
CRAFTY_UID="${CRAFTY_UID:-1000}"; CRAFTY_GID="${CRAFTY_GID:-0}"

now="$(date -u '+%Y-%m-%d %H:%M UTC')"

python3 - "$SPAWN_JSON" "$now" > "$OUT.tmp" <<'PY' && mv "$OUT.tmp" "$OUT" || { echo "[build-knowledge] FAILED to write $OUT" >&2; rm -f "$OUT.tmp"; exit 1; }
import sys, json

spawn_path, now = sys.argv[1], sys.argv[2]
try:
    rules = json.load(open(spawn_path))
    if not isinstance(rules, list):
        rules = []
except Exception:
    rules = []

def num(x):
    try:
        f = float(x)
        return f"{f:g}"
    except Exception:
        return str(x)

def scope_of(r):
    if 'dimension' in r and 'biome' in r:
        return f"{r['dimension']} (biome {r['biome']})"
    if 'dimension' in r:
        return r['dimension']
    if 'biome' in r:
        return f"biome {r['biome']}"
    return 'GLOBAL (any dimension)'

def who_of(r):
    parts = []
    if r.get('hostile'):
        parts.append('all hostile mobs')
    if r.get('passive'):
        parts.append('all passive mobs')
    parts += list(r.get('mob', []) or [])
    parts += [f"mod:{m}" for m in (r.get('mod', []) or [])]
    return parts or ['all mobs']

out = []
out.append(f"# SPAWNS — live spawn rules & difficulty (auto-built {now})")
out.append("")
out.append("Compiled from the server's In Control spawn rules. This is the LIVE truth for what spawns where/when.")
out.append("Rules apply top-to-bottom, first match wins: `allow` = spawns normally, `DENY` = never spawns.")
out.append("Mob stat multipliers below make mobs TOUGHER than vanilla, so size kill counts accordingly.")
out.append("")

if not rules:
    out.append("> (No In Control spawn rules found — fall back to CATALOG's WILD/NOT-WILD list and vanilla spawn sense.)")
    print("\n".join(out))
    sys.exit(0)

# --- difficulty multipliers ---
diff = [r for r in rules if r.get('healthmultiply') or r.get('damagemultiply')]
out.append("## Difficulty multipliers (mobs are tougher than vanilla)")
if diff:
    for r in diff:
        mult = []
        if r.get('healthmultiply'):
            mult.append(f"x{num(r['healthmultiply'])} HP")
        if r.get('damagemultiply'):
            mult.append(f"x{num(r['damagemultiply'])} damage")
        when = f" (when {r['when']})" if r.get('when') else ""
        out.append(f"- {scope_of(r)} — {', '.join(who_of(r))}: {', '.join(mult)}{when}")
else:
    out.append("- (none configured)")
out.append("")

# --- per-scope allow/deny, preserving evaluation order ---
out.append("## Spawn rules by dimension/biome (in order; first match wins)")
last = None
for r in rules:
    s = scope_of(r)
    if s != last:
        out.append("")
        out.append(f"### {s}")
        last = s
    verb = 'DENY ' if r.get('result') == 'deny' else 'allow'
    when = f" (when {r['when']})" if r.get('when') else ""
    out.append(f"- {verb}: {', '.join(who_of(r))}{when}")
out.append("")

out.append("## How to use this when authoring")
out.append("- Only set kill_entity on a mob shown as `allow` in a dimension the player can reach (check unlockedGates).")
out.append("- Never kill_entity a `DENY` mob, a boss, or a mob buffed to extremes (e.g. the Warden) — mention as lore only.")
out.append("- A bare `allow: mod:X` means most mobs of that mod spawn there; a `DENY` line below it carves out exceptions.")
out.append("- Heavier multipliers => smaller kill counts. Always name the dimension/biome (and time, if night-only).")
print("\n".join(out))
PY

chown "$CRAFTY_UID:$CRAFTY_GID" "$OUT" 2>/dev/null || true
echo "[build-knowledge] wrote $OUT ($(wc -l < "$OUT" 2>/dev/null || echo 0) lines) from $SPAWN_JSON"
