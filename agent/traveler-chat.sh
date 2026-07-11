#!/usr/bin/env bash
#
# Traveler chat brain — answers in-game "talk to the Traveler" messages with Claude (Haiku), via the same
# file contract as the daily brain. Runs as a systemd service on the VPS HOST (root, using ~/.claude creds),
# because the Minecraft server runs inside Docker without the `claude` binary.
#
# Contract (shared with the mod's ai.TravelerChat):
#   reads   $CFG/chat/requests/<reqId>.json   {player{uuid,name}, message, history[], loreStage, dimension}
#   writes  $CFG/chat/responses/<reqId>.json  {reqId, reply, error}
# The mod polls for the response, shows it, and deletes both files.
#
# AUTH: same as the daily brain — root logged into Claude Code (`ssh -t mcserver claude` then /login), or a
#       CLAUDE_CODE_OAUTH_TOKEN in /etc/mcserver/claude.env.
set -uo pipefail

SERVER_DIR="${SERVER_DIR:-/opt/crafty/servers/19aa7f07-e3bd-481d-b029-1f3f6eac5f28}"
GEN_DIR="${GEN_DIR:-/opt/custommissions-gen}"
CFG="$SERVER_DIR/config/custommissions"
REQ="$CFG/chat/requests"
RESP="$CFG/chat/responses"
MODEL="${TRAVELER_MODEL:-haiku}"
CRAFTY_UID="${CRAFTY_UID:-1000}"; CRAFTY_GID="${CRAFTY_GID:-0}"
export HOME="${HOME:-/root}"
export PATH="/usr/local/bin:/usr/bin:/bin:$PATH"
[ -f /etc/mcserver/claude.env ] && . /etc/mcserver/claude.env

log() { echo "[$(date -u '+%F %H:%M:%S')] $*"; }

command -v claude >/dev/null 2>&1 || { log "no 'claude' on PATH; exiting"; exit 1; }
command -v jq     >/dev/null 2>&1 || { log "no 'jq' on PATH; exiting"; exit 1; }

PERSONA_FALLBACK='You are the Traveler, a mysterious wandering guide NPC. Stay in character, English, 2-5 sentences, reveal no secrets. The overworld is named Aincrad — always call it Aincrad.'

mkdir -p "$REQ" "$RESP"
chown "$CRAFTY_UID:$CRAFTY_GID" "$CFG/chat" "$REQ" "$RESP" 2>/dev/null || true

write_response() {  # reqId, reply, error(true|false)
    jq -n --arg id "$1" --arg r "$2" --argjson e "$3" '{reqId:$id, reply:$r, error:$e}' > "$RESP/$1.json"
    chmod 644 "$RESP/$1.json"
    chown "$CRAFTY_UID:$CRAFTY_GID" "$RESP/$1.json" 2>/dev/null || true
}

# Gossip mode: the model JUDGES a player's tip (how credible/substantive, not how kind) and returns a verdict
# JSON the mod parses directly: {credibility 0..1, sentiment -1..1, delta -20..20, note}. Cheap insults score
# near zero. NOT wrapped in the {reply} envelope — the response file IS the verdict object.
handle_gossip() {
    local f="$1" reqId="$2" gname tname tself who text prompt out json
    gname="$(jq -r '.gossiper.name // "someone"' "$f" 2>/dev/null)"
    tname="$(jq -r '.target.name // "someone"' "$f" 2>/dev/null)"
    tself="$(jq -r '.target.self // false' "$f" 2>/dev/null)"
    text="$(jq -r '.text // ""' "$f" 2>/dev/null)"
    if [ -z "${text// }" ]; then rm -f "$f"; return; fi
    if [ "$tself" = "true" ]; then who="themselves (the player \"$gname\")"; else who="the player \"$tname\""; fi
    log "gossip from $gname about $tname: ${text:0:50}"

    prompt="You are the hidden rumor-judge of a fantasy Minecraft server. The player \"$gname\" just whispered a tip to the market gossip about $who.
Judge how CREDIBLE and SUBSTANTIVE the tip is — NOT whether it is kind. A specific, plausible, detailed claim scores HIGH credibility; a vague slur, a generic insult, name-calling, or spam scores NEAR ZERO. Judge sentiment: does it make the subject look good (+1) or bad (-1)?

Tip: \"$text\"

Output ONLY a compact JSON object — no prose, no markdown, no code fence:
{\"credibility\":0.0,\"sentiment\":0.0,\"delta\":0,\"note\":\"one short neutral sentence summarizing the rumor\"}
where delta = round(credibility * sentiment * 20), an integer from -20 to 20."

    out="$(printf '%s' "$prompt" | timeout 120 claude -p --model "$MODEL" 2>>"$GEN_DIR/traveler.log")"
    json="$(printf '%s' "$out" | python3 -c 'import sys; t=sys.stdin.read(); i=t.find("{"); j=t.rfind("}"); sys.stdout.write(t[i:j+1] if (i>=0 and j>i) else "")' 2>/dev/null)"
    if printf '%s' "$json" | jq -e 'type=="object" and has("credibility")' >/dev/null 2>&1; then
        printf '%s' "$json" > "$RESP/$reqId.json"
    else
        log "  gossip: bad/empty verdict for $gname (see $GEN_DIR/traveler.log)"
        printf '{"error":true,"credibility":0,"sentiment":0,"delta":0,"note":""}' > "$RESP/$reqId.json"
    fi
    chmod 644 "$RESP/$reqId.json"
    chown "$CRAFTY_UID:$CRAFTY_GID" "$RESP/$reqId.json" 2>/dev/null || true
    rm -f "$f"
}

handle() {
    local f="$1" reqId mode name dim dimlabel stage chapter persona msg transcript recent prompt reply bias rep repline
    [ -f "$f" ] || return
    reqId="$(basename "$f" .json)"
    mode="$(jq -r '.mode // "direct"' "$f" 2>/dev/null)"
    if [ "$mode" = "gossip" ]; then handle_gossip "$f" "$reqId"; return; fi
    name="$(jq -r '.player.name // "traveler"' "$f" 2>/dev/null)"
    dim="$(jq -r '.dimension // "minecraft:overworld"' "$f" 2>/dev/null)"
    stage="$(jq -r '.loreStage // 0' "$f" 2>/dev/null)"
    chapter="$(jq -r '.loreChapter // 0' "$f" 2>/dev/null)"
    msg="$(jq -r '.message // ""' "$f" 2>/dev/null)"
    if [ -z "${msg// }" ]; then rm -f "$f"; return; fi
    # Re-read the persona each request, so edits to traveler-persona.md apply without a restart.
    persona="$(cat "$GEN_DIR/traveler-persona.md" 2>/dev/null || printf '%s' "$PERSONA_FALLBACK")"
    # The overworld is ALWAYS Aincrad in Flugel's mouth.
    case "$dim" in
        minecraft:overworld) dimlabel="Aincrad" ;;
        *) dimlabel="$dim" ;;
    esac
    bias="$(jq -r '.gossipBias // 0' "$f" 2>/dev/null)"
    rep="$(jq -r '.rumors[]?' "$f" 2>/dev/null | tr '\n' ';')"
    repline="REPUTATION of $name: gossipBias $bias (negative = the market speaks ill of them; positive = well-regarded). Colour your warmth accordingly; never quote rumors aloud."
    if [ -n "${rep// }" ]; then repline="$repline Whispers heard: $rep"; fi

    if [ "$mode" = "ambient" ]; then
        # chat-listener: the Traveler OVERHEARS public chat and chimes in (broadcast to everyone)
        recent="$(jq -r '.recentChat[]?' "$f" 2>/dev/null)"
        log "chat-mention by $name: ${msg:0:60}"
        prompt="$persona

=== SITUATION (you OVERHEAR public chat) ===
Players are chatting on the server and someone mentioned you by name. This is not a private talk; your voice
carries on the wind to everyone. $name is in $dimlabel.
STORY SO FAR: Chapter $chapter, Stage $stage. Speak ONLY of what has happened up to this chapter; reveal nothing from later chapters.
$repline
You MAY read files under context/docs/ for accuracy, but keep it VERY brief.
Reply in ONE or TWO short sentences, in character, reacting to what they're saying. Reveal NO hidden plot — hint and deflect.

=== RECENT CHAT (oldest first) ===
${recent:-(no recent chat)}

Reply with ONLY Flugel's spoken words — no quotes, no narration, no name prefix, no markdown:"
    else
        # direct: the /traveler GUI, a private 1:1 conversation
        transcript="$(jq -r '.history[]? | (if .role=="user" then "Player: " else "Flugel: " end) + .text' "$f" 2>/dev/null)"
        log "chat from $name: ${msg:0:60}"
        prompt="$persona

=== SITUATION ===
You are speaking with $name, here in $dimlabel.
STORY SO FAR: Chapter $chapter, Stage $stage. Speak ONLY of what has happened up to this chapter; reveal nothing from later chapters.
$repline
You MAY read files under context/docs/ (lore-bible.md, Story/, npcs/, infomods/) for accuracy, but keep it brief.
Reply in 2-5 sentences. Reveal NO hidden plot or secret identities — hint and deflect.

=== CONVERSATION SO FAR ===
${transcript:-(this is the start of the conversation)}

Player just said: \"$msg\"

Reply with ONLY Flugel's spoken words — no quotes, no narration, no markdown:"
    fi

    reply="$(printf '%s' "$prompt" | timeout 120 claude -p --model "$MODEL" \
                --add-dir "$GEN_DIR/context" --allowedTools Read Glob Grep 2>>"$GEN_DIR/traveler.log")"
    reply="$(printf '%s' "$reply" | sed 's/\r$//')"

    if [ -z "${reply// }" ]; then
        log "  empty reply for $name (see $GEN_DIR/traveler.log)"
        write_response "$reqId" "Flugel studies you in silence, then looks away." true
    else
        write_response "$reqId" "$reply" false
    fi
    rm -f "$f"
}

log "traveler-chat watching $REQ (model=$MODEL)"
# clear any backlog from before we started
for f in "$REQ"/*.json; do [ -e "$f" ] && handle "$f"; done

if command -v inotifywait >/dev/null 2>&1; then
    inotifywait -m -q -e close_write -e moved_to --format '%f' "$REQ" | while read -r fn; do
        case "$fn" in *.json) handle "$REQ/$fn" ;; esac
    done
else
    log "inotifywait not found (apt install inotify-tools for instant replies); polling every 2s"
    while true; do
        for f in "$REQ"/*.json; do [ -e "$f" ] && handle "$f"; done
        sleep 2
    done
fi
