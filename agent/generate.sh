#!/usr/bin/env bash
#
# Daily mission generation wrapper (the swappable "brain" driver).
#
# STATUS: NOT YET DEPLOYED. The VPS currently has no `node`/`claude` binary (only a stale /root/.claude).
# This is the reference implementation for when a headless brain is installed on the VPS. The mod side is
# already done — it just reads/writes the JSON files this script orchestrates.
#
# What it does, once a day (intended cron: 04:30 UTC):
#   1. Reads the exported state the mod wrote (world_state.json + per-player context).
#   2. For each online player in the roster, asks the brain (headless Claude Code) to author 3 daily
#      missions in the CustomMissions DSL, writing them under daily/<today>/<uuid>/.
#   3. chown 1000:0 the new files (Crafty runs as uid 1000) and triggers an in-game reload via mc-cmd.
#
# Swap the brain by replacing the BRAIN call (step 2) with an Anthropic API sidecar that writes the same
# files — nothing else changes.

set -euo pipefail

# --- paths (the live Crafty server config dir on the VPS) -------------------------------------------
SERVER_DIR="${SERVER_DIR:-/opt/crafty/servers/19aa7f07-e3bd-481d-b029-1f3f6eac5f28}"
CFG="$SERVER_DIR/config/custommissions"
STATE="$CFG/state"
TODAY="$(date -u +%F)"                       # UTC, matches the mod's catalog
DEST="$CFG/daily/$TODAY"
REPO="${REPO:-$HOME/Documents/Repos/proyectos/CustomMissions}"   # for DSL.md + PROMPT.md
CRAFTY_UID="${CRAFTY_UID:-1000}"
CRAFTY_GID="${CRAFTY_GID:-0}"

WORLD_STATE="$STATE/world_state.json"
[ -f "$WORLD_STATE" ] || { echo "no world_state.json yet ($WORLD_STATE) — is the mod running?"; exit 0; }

# --- roster (online players the mod last exported) -------------------------------------------------
# Requires jq. The roster is names; we map name -> uuid via the context files' "player" block.
mapfile -t NAMES < <(jq -r '.onlineRoster[]?' "$WORLD_STATE")
[ "${#NAMES[@]}" -gt 0 ] || { echo "no players online — nothing to generate"; exit 0; }

generated=0
for name in "${NAMES[@]}"; do
    # find the context file whose player.name == $name
    ctx="$(grep -rl "\"name\": \"$name\"" "$STATE/context" 2>/dev/null | head -n1 || true)"
    [ -n "$ctx" ] || { echo "no context for $name — skipping"; continue; }
    uuid="$(jq -r '.player.uuid' "$ctx")"
    out="$DEST/$uuid"
    mkdir -p "$out"

    # --- BRAIN CALL (replace with your installed headless Claude Code, or an API sidecar) ----------
    # The brain must read PROMPT.md + DSL.md + $WORLD_STATE + $ctx and write 01.json,02.json,03.json
    # into $out. Example with Claude Code headless (once installed on the VPS):
    #
    #   claude -p "$(cat "$REPO/agent/PROMPT.md")
    #
    #   WORLD_STATE:
    #   $(cat "$WORLD_STATE")
    #
    #   PLAYER_CONTEXT:
    #   $(cat "$ctx")
    #
    #   DSL:
    #   $(cat "$REPO/DSL.md")
    #
    #   Write exactly three files: $out/01.json $out/02.json $out/03.json" \
    #     --allowedTools 'Write' --permission-mode acceptEdits
    #
    echo "TODO: brain call for $name ($uuid) -> $out"
    generated=$((generated + 1))
done

# --- hand off to the game -------------------------------------------------------------------------
chown -R "$CRAFTY_UID:$CRAFTY_GID" "$DEST" 2>/dev/null || true
# mc-cmd reads /etc/mcserver/crafty.env (root); triggers MissionManager.reload + DailyIngestTask.
mc-cmd 'mission reload' || echo "warn: could not run mc-cmd 'mission reload' (run it manually)"

echo "done: prepared dailies for $generated player(s) on $TODAY"
