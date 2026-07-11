# CustomMissions — Technical Reference

A server-driven, AI-extensible mission system for the Realm Gates (summerBuddies) Forge 1.20.1 server.
Missions are plain JSON (the "mission language", see `DSL.md`). Authored chains live in
`config/custommissions/missions/`; AI-authored dailies are dropped into `config/custommissions/daily/`
and hot-loaded. This document is the architecture reference; progress lives in `STATUS.md`.

Mod id `custommissions`, package `com.summerbuddies.custommissions`, both-side, Forge 47.4.0.

## Overview

Five decoupled subsystems:

1. **Catalog** (`mission/`) — `MissionManager` reads + hot-reloads mission JSON into an immutable map.
2. **Objective engine** (`event/`, `objective/`) — Forge events and custom `/mission signal`s are
   normalized into a `MissionEvent` and funneled through `MissionEventBus` to each active mission's
   objectives. Adding an objective type touches only the enum + factory (+ one Forge hook if needed).
3. **State** (`state/`) — per-player progress in persistent NBT; a server-wide lore stage as SavedData;
   `MissionTracker` owns the lifecycle (assign/complete/abandon/expire) and reward granting.
4. **Rewards + bridges** (`reward/`, `bridge/`) — item/xp/command/cutscene/unlock/companion/lore rewards;
   cross-mod ones run via commands behind `ModList.isLoaded` guards.
5. **Waypoints** (`waypoint/`, `net/`, `client/`) — `reach_location` objectives push an S2C marker the
   client renders as a compass + distance (the always-available fallback for JourneyMap waypoints).

## Package map

```
custommissions/
  CustomMissionsMod         @Mod entry; registers MissionNet
  Constants                 MODID, LOG
  mission/
    Mission, MissionCategory, MissionLocation, Giver, Prerequisites, LoadResult
    MissionManager          hot-reload loader (missions/ + today's daily/); accessors get/all/dailiesFor
    json/                   GiverDto, PrerequisitesDto, LocationDto, ObjectiveDto, RewardDto, MissionDto
  objective/
    Objective (iface), ObjectiveType (enum), Objectives (records), ObjectiveFactory
  event/
    MissionEvent (sealed), MissionEventBus (dispatch funnel)
    ForgeEventHooks         Forge listeners + login/logout + periodic housekeeping/exports
    LocationScanner         throttled position sampler (reach_location)
  state/
    PlayerMissions, PlayerMissionStore   per-player progress in PERSISTED_NBT_TAG
    MissionTracker          assign/complete/abandon/expireDailies/available
    LoreState               overworld SavedData: chapter, stage, flags, unlockedGates
    KnownPlayers            overworld SavedData: roster uuid->name (gossip target list)
    GossipLedger            overworld SavedData: per-player reputation bias + rumors (by uuid, works offline)
  reward/
    Reward (iface), RewardType (enum), Rewards (records), RewardFactory, RewardContext
  waypoint/
    WaypointService (facade), MarkerFallback (S2C backend)
  net/
    MissionNet (channel), MarkerS2C, MissionSyncS2C, RequestMissionsC2S
    OpenDescribeS2C, SubmitDescriptionC2S, SaveDescriptionDraftC2S   self-description flow
    OpenTravelerChatS2C, TravelerChatC2S, TravelerReplyS2C           Flugel chat flow
    OpenGossipS2C, GossipSubmitC2S                                   gossip picker flow
  command/MissionCommand    /mission tree (incl. op gossip|flugel; no player /traveler)
  bridge/
    StoryKitBridge, RealmGatesBridge, CompanionsBridge, EasyNpcBridge
  intro/
    IntroCutscene            first describe-close → StoryKit epilogue (once, per-player flag)
  client/ (@Dist.CLIENT)
    ClientMissions, MarkerOverlay, MissionsScreen (M log), MissionHudOverlay, MissionKeybind
    DescribeScreen           "tell us about yourself" multiline box (persistent draft)
    TravelerClient, TravelerChatScreen   Flugel 1:1 chat window
    GossipClient, GossipScreen, GuiSkinCache   gossip picker (target+skin) + Mojang skin fetcher
  ai/
    MissionContextExporter, WorldStateExporter, DailyIngestTask
    TravelerChat             Flugel chat + gossip brain (file-contract bridge to the Sonnet sidecar)
```

## Config layout (under `config/custommissions/`)

```
missions/                              authored chains (git-deployed). Edit + /mission reload.
daily/<YYYY-MM-DD>/<player-uuid>/NN.json   AI-authored dailies (hot-loaded; today's date, UTC).
state/context/<uuid>.json              per-player context EXPORTED by the mod for the AI brain.
state/world_state.json                 server lore + roster EXPORTED by the mod for the AI brain.
README.txt                             auto-written quick reference.
```

`config/custommissions/` resolves via `FMLPaths.CONFIGDIR` to the live Crafty server dir on the VPS, so
the mod and the on-VPS brain share the files. `mc-update` syncs `config/` without `--delete`, so runtime
`daily/`+`state/` survive deploys.

## Mission file format

See `DSL.md` for the complete schema and examples. In short: one JSON object per mission with `id`,
`category` (daily|primary|secondary), `title`/`description`/`lore`, `giver`, `prerequisites`,
`objectives[]`, `rewards[]`, optional `onAccept[]`/`onComplete[]`, optional `location`, and (daily)
`expiryHours`. The loader is forgiving: unknown fields are ignored, an unknown objective/reward `type`
warns and is skipped (the mission still loads), and every numeric field defaults.

## Objective reference (event-matchers)

| type | advances on | key fields |
|---|---|---|
| `kill_entity` | player kill (direct or pet/projectile) | `entity` (id or `#tag`), `count`. Summoned minions are excluded (scoreboard tag contains "summon", e.g. `cc_summon`). |
| `collect_item` | item pickup | `item`, `count` |
| `reach_location` | position sample | `dimension`,`x`,`y`,`z`,`radius`,`waypoint`,`waypointColor` |
| `talk_to_npc` | interact with an `easy_npc:*` entity | `npcUuid` and/or `npcName` |
| `enter_dimension` | dimension change / being there | `dimension` |
| `advancement` | advancement earned | `advancement` |
| `use_block` | right-click a block | `block`, `count` |
| `place_block` | place a block | `block`, `count` |
| `deliver_item_to_npc` | interact with NPC while holding items (consumes them) | `npc*`, `item`, `count` |
| `custom_signal` | `/mission signal <name>` | `signal`, `count` |

## Reward reference

| type | fields | mechanism |
|---|---|---|
| `item` | `item`, `count` | give stack (drop overflow) |
| `xp` | `amount` | `giveExperiencePoints` |
| `command` | `command`, `asPlayer` | server/player command, `{player}` substituted |
| `cutscene` | `script` | `story play <script> <player>` (StoryKit) |
| `unlock` | `gate` | `realmgates unlock <player> <gate>` |
| `companion` | `companion` | `companion picto grant <id>` |
| `lore_stage_advance` | `to` (or +1) | `LoreState.setStage` |

`onAccept`/`onComplete` reuse the reward list, so a cutscene or command can fire on accept too.

## Triggers & custom signals

Native Forge events are wired automatically (`ForgeEventHooks`). For anything else, emit a signal:

```
/mission signal <name> [count] [players]
```

- **KubeJS** (server_scripts, IIFE-wrapped per the shared-scope rule):
  `e.server.runCommandSilent("mission signal altar_lit " + player.username)`
- **EasyNPC**: an `ON_INTERACTION → RUN_COMMAND` action running
  `mission signal talk_the-traveler {player}` (robust alternative to the auto-detected `talk_to_npc`).
- **Other mods** (Bosses, Realm Gates): emit `mission signal boss_<name> {player}` on death/crossing.

A `custom_signal` objective with the matching `signal` advances by `count`.

## AI generation contract

The mod EXPORTS context for the brain and IMPORTS the dailies it writes — it never calls an LLM itself,
so the brain is swappable. See `agent/PROMPT.md` for the brain's job spec.

- Mod writes `state/context/<uuid>.json` (per player: `player.description` self-description, lore stage,
  dimension, active/completed missions, vanilla stat snapshot) and `state/world_state.json` (lore
  chapter/stage, flags, gates, dimensions, givers, online roster). Refreshed on logout + every ~30s online.
- **Curated knowledge packs (inline, on the VPS under `agent/`)** — `generate.sh` injects three inputs the
  mod does NOT export, so the brain writes modded, varied, spawn-aware dailies instead of vanilla
  kill/reach filler: `CATALOG.md` (the real modded ids on this server — which mobs are **WILD** vs
  boss/structure/summon-only so it never quests for unspawnable mobs; gather items, buildable/usable blocks,
  explorable structures, dimensions, and the coin rewards), `CAST.md` (the NPCs of Aincrad incl. **Grandma
  the rumor-keeper**), and **`PLAYER_MOD_USAGE`** (per player, distilled in `generate.sh` from the raw
  `world/stats/<uuid>.json` → `favoriteMods` + `topModdedActivity`, so dailies lean into the mods the player
  actually plays with). The packs are shared across players, so per-player token cost stays ~flat.
- **Player self-description** — on login (and every login until submitted) the mod opens a multiline text
  box (`DescribeScreen`) asking the player who they are; their dailies are authored from it. Stored in
  persistent NBT (`PlayerMissions.description`); an accidental close saves a `draftDescription` that is
  restored next time, so nothing is lost until "Send". Reopen/edit any time with `/mission describe`. The
  brain should weave `player.description` into the missions (see `agent/PROMPT.md`).
- **Intro cutscene on first describe-close** (`intro/IntroCutscene`) — the first time a brand-new player
  closes the describe screen (Send OR Esc/"Later"), the mod fires the StoryKit `epilogue` cutscene for that
  player via `StoryKitBridge.playCutscene` (`/story play epilogue <player>`). One-shot, guarded by the
  persistent `PlayerMissions.introCinematicPlayed` flag. Established players (who already have a description)
  are pre-marked on login so re-editing never replays it; a player who disconnects mid-close stays unmarked
  and retries next login. This replaces any login-timer approach — the describe-close is both the natural
  "new player" gate and the moment the world has finished loading.
- Brain writes `daily/<today>/<uuid>/NN.json` (3 per player) in the daily DSL, then runs
  `mc-cmd 'mission reload'`. `DailyIngestTask` auto-assigns each player's dailies on login/periodically.

## Flugel the Traveler + the Market Gossip (AI)

The central guide NPC is **Flugel the Traveler** (display name; internal identifiers/uuid stay `traveler`).
Both the chat and the gossip system are brained by an off-process Claude (Sonnet) over the SAME file
contract as the daily brain — the mod never calls an LLM itself. Three request modes share `chat/requests/`
+ `chat/responses/`, polled by `TravelerChat.poll` (server tick, 90s timeout):

- **`direct` — Flugel 1:1 chat (button).** There is **no player `/traveler` command**. An EasyNPC dialogue
  button on Flugel (`d48a3f45-…`) "talk more personally" runs `RUN_COMMAND mission flugel` (perm 2, run
  elevated by EasyNPC), which sends `OpenTravelerChatS2C` → `TravelerChatScreen`. **Limited to 2 messages per
  10-minute window per player** (`TravelerChat` rolling timestamps); over the limit, Flugel declines.
- **`ambient` — public chat-listener.** `ForgeEventHooks.onServerChat` (`@SubscribeEvent(priority=LOWEST,
  receiveCanceled=true)` — needed because `voicetrans` cancels `ServerChatEvent`) buffers the last 8 lines;
  a message containing "flugel" **or** "traveler" (8s cooldown) → `mode:"ambient"` with the last ~5 lines,
  reply **broadcast** as `✦ Flugel the Traveler ✦ …`.
- **`gossip` — market gossip → reputation.** The gossip NPC is **Grandma** (`9ed8a056-…`, the village
  rumor-keeper; see `npcs/grandma.md` + `agent/CAST.md`) with a button "I've got gossip"
  (`RUN_COMMAND mission gossip`, perm 2) → `OpenGossipS2C` with the `KnownPlayers` roster (self first) →
  `GossipScreen`: pick a target (face via `GuiSkinCache`, the Mojang skin = what CustomSkinLoader shows) +
  200-char tip → `GossipSubmitC2S` → `TravelerChat.gossip` writes a `mode:"gossip"` request. The sidecar asks
  Sonnet to **judge credibility** (specific/plausible = high; cheap insults ≈ 0) and returns
  `{credibility 0..1, sentiment -1..1, delta -20..20, note}`. `applyGossip` nudges the **target's**
  `GossipLedger` bias by `delta` (strong) and the **gossiper's** by `~delta/4`; records the `note` as a rumor
  if `|delta|≥4`. Stored by uuid → works for **offline** targets.
- **Reputation feeds back:** `gossipBias` + `rumors` ride along in `direct`/`ambient` requests (Flugel's tone)
  and in `MissionContextExporter` (`player.gossipBias`/`player.rumors` → personal missions, see PROMPT rule 8).
- **Sidecar (host, not Docker) — running:** `agent/traveler-chat.sh` (systemd `traveler-chat`) per `mode` runs
  `claude -p --model sonnet` with `agent/traveler-persona.md` (Flugel; in-character, English, **no hidden
  plot**, overworld = "Aincrad", chapter-gated) + `--add-dir context/`. Persona re-read per request (hot).
  Disable with `systemctl disable --now traveler-chat`.

## Commands

`/mission` — players: `list`, `progress`, `accept <id>`, `abandon <id>`, `describe`,
`signal <name> [count] [players]`.
Ops (perm 2): `reload`, `assign <player> <id>`, `complete <player> <id>`, `daily regen <players>`,
`lorestage get|set <chapter> <stage>`, `flag add|remove <name>`, `waypoint debug <players>`,
`export world|player <players>`, and the NPC-triggered `gossip` / `flugel` (open the gossip picker / Flugel
chat — wired to the NPC dialogue buttons, not meant to be typed).

## Build & deploy

- Build: `./gradlew build` → `build/libs/custommissions-0.1.0.jar` (reobfuscated).
- Runtime can't be verified in-sandbox (`runServer` is blocked); verify on the real server.
- Ship via `mc-mods-sync` (add to its set) or copy the jar to `~/MCserver/mods/`, then the standard
  MCserver flow: `git push` + `ssh mcserver mc-update`. ⚠️ Never restart without explicit owner OK.

## Conventions

- English-only (code, strings, comments, commits), like the other in-house mods.
- Mirrors StoryKit/Realm Gates patterns: GSON DTO + hot-reload + atomic volatile swap + warnings;
  per-player `PERSISTED_NBT_TAG`; overworld `SavedData`; S2C overlay via `DistExecutor`.
- Keep this file and `STATUS.md` in sync with changes; `DSL.md` is the source of truth for the schema.

## Changelog

- **brain: models swapped + context compaction** (2026-06-28, `agent/` data, no jar) — daily generation now
  defaults to **`sonnet`** (`BRAIN_MODEL:-sonnet`, better quality/creativity, fewer hallucinated CATALOG ids) and
  the Flugel chat sidecar to **`haiku`** (`TRAVELER_MODEL:-haiku`, cheaper). To keep the now-pricier daily prompt
  focused and cheap as a player's history grows, `generate.sh` **compacts old/bulky context**: it always collapses
  the unbounded list of completed *daily* ids to a count (`completedDailiesCount`; authored primary/secondary
  completions stay), and when a player's inline context exceeds `CONTEXT_BUDGET` (default 20 KB) it shrinks the raw
  `PLAYER_STATS` (a veteran's is ~50 KB) to a top-12-per-category summary via `compact_stats()` (otogr: 52 KB → ~2 KB).
- **reward display** (2026-06-28, jar — ship via `mc-mods-sync` + restart) — the mission screen showed reward
  rewards as raw strings (`/xp add {player} 1 levels`, `1x lightmanscurrency:coin_copper`), breaking immersion.
  `Rewards.java` now formats `describe()` for players: the `+1 level` command reward (matched by the regex
  `xp add <x> N levels?`) shows as **"+1 level"**, and item rewards show the item's real **display name**
  ("Copper Coin"), with a `prettyId()` fallback. Server-side only (describe strings are sent to the client via
  `MissionSync`), and `displayTest=IGNORE_SERVER_VERSION` so clients need no update. The command reward still
  works on any jar — old jar shows the raw text, new jar shows the pretty text (no brain/data change needed).
- **brain upgrade** (2026-06-28, `agent/` data + new helper, no jar) — dailies now reward **+1 player level**
  (a `command` reward `xp add {player} 1 levels`, which raises the level regardless of current level) instead
  of xp points, plus a copper coin — both guaranteed by jq in `generate.sh`. The brain got three new inputs:
  **PLAYER_POWER** (`power_signal()` — tier fresh/early/mid/endgame + milestones from advancements+stats, used
  to scale difficulty), **SPAWNS** (live per-dimension spawn rules + In Control difficulty multipliers, e.g.
  buffed overworld hostiles / Warden ×10), and stronger creativity + difficulty-scaling RULES. SPAWNS is built
  by the new **`agent/build-knowledge.sh`** (deterministic, compiles `config/incontrol/spawn.json`), exposed as
  the admin command **`mc-missions-knowledge`** and auto-run before each daily batch so the brain stays current
  on difficulty changes. (Showing "+1 level" in the M-screen reward list instead of the raw `/xp add ...` would
  need a dedicated `level` RewardType — not done; that requires a jar ship + restart.)
- **brain fix** (2026-06-27, `agent/` data only, no jar) — **dailies stopped generating for veteran players**
  (claude E2BIG: the prompt with the raw 266 KB advancements blob exceeded the 128 KiB single-arg limit). Fixed
  by feeding the prompt on STDIN; also distill advancements to earned non-recipe ids, and a guaranteed copper coin.
- **0.1.10** (2026-06-27) — **big banner now PRIMARY-only; dailies/secondaries are chat-only.** The
  loud "new mission" cue from 0.1.8 (on-screen title + fanfare + `=== NEW MISSION ===` block) fired for
  *every* assigned mission, so the auto-assigned dailies spammed the screen banner. `announceNewMission`
  now routes by `category`: `PRIMARY` keeps the full banner + `UI_TOAST_CHALLENGE_COMPLETE` fanfare
  (`announcePrimaryMission`), while `DAILY` and `SECONDARY` get a quiet **chat-only** block + a soft
  `EXPERIENCE_ORB_PICKUP` ping (`announceMinorMission`), tagged/colored per the in-UI scheme (daily =
  green `[Daily]`, side quest = aqua `[Side Quest]`). No DSL/data change — the `category` field already
  drove this; only the announce path changed.
- **0.1.9** (2026-06-27) — **`autoAccept` (forced auto-accept for important missions).** New optional mission
  field `autoAccept` (DSL): when `true`, the mission is force-assigned to a player the moment its
  `prerequisites` are met, instead of waiting in the accept pool. New `MissionTracker.autoAssignAvailable`
  loops `available()` and assigns the flagged ones (reusing the loud `assign` cue from 0.1.8). Re-checked in
  `ForgeEventHooks` on login, on `PlayerChangedDimensionEvent`, on the ~30s housekeeping tick, and at the end
  of `MissionTracker.complete` (so a chain's next link pops automatically). The 4 primary story missions
  (`primary_flugel_needs_you` → `primary_read_lectern` → `primary_defeat_kraken` + `primary_defeat_yeti`) were
  flagged `autoAccept:true` so the whole main line flows on its own. Record `Mission` gained an `autoAccept`
  component; `MissionDto` an `autoAccept` field.
- **brain fix** (2026-06-27, `agent/` data only, no jar) — **dailies stopped generating for veteran players.**
  `generate.sh` passed the whole prompt as a single `claude -p "<…>"` argv; with the raw per-player advancements
  JSON inlined (a veteran's is 200-300 KB) the string blew past Linux's 128 KiB single-arg limit (`MAX_ARG_STRLEN`)
  → `exec` died with `E2BIG` ("Argument list too long"), claude never ran, and that player silently got no dailies
  (broken since 0.1.7 added the big inline context). Fix: **feed the prompt on STDIN** (`printf … | claude -p`,
  no arg-size limit). Also added `adv_done()` to distill the advancements blob to earned non-recipe ids (266 KB →
  ~6 KB, big haiku-token saving), a jq net guaranteeing every daily rewards a `lightmanscurrency:coin_copper`, and
  an optional `ONLY_UUID` for targeted re-runs. The mod-side assignment path (`DailyIngestTask`/`MissionTracker`)
  was never at fault — the files just weren't being written.
- **0.1.8** (2026-06-26) — **louder "new mission" cue.** `MissionTracker.assign` no longer announces a new
  mission with a single easy-to-miss chat line. It now layers three channels (new `announceNewMission`): a
  big on-screen **title** ("New Mission" / the mission title, ~3.5s), a **sound** (`UI_TOAST_CHALLENGE_COMPLETE`,
  player-only), and a **highlighted chat block** with a bold `=== NEW MISSION ===` header. Same for daily
  auto-assign and `/mission assign` (both route through `assign`).
- **0.1.7** (2026-06-25) — **modded-aware, varied dailies + economy + cast (data/prompt only, no Java).**
  The daily brain gets three new inline inputs from `generate.sh`: `agent/CATALOG.md` (real modded ids; WILD
  vs boss/structure-only; gather items, structures, coins), `agent/CAST.md` (the Aincrad NPC cast incl.
  Grandma the rumor-keeper), and `PLAYER_MOD_USAGE` (distilled from `world/stats/<uuid>.json` — tailors
  dailies to the mods a player actually uses). Rewrote the RULES + `PROMPT.md`: allow modded ids from
  CATALOG, require 3 different objective types with ≥1 modded, never `kill_entity` a NOT-WILD mob, preserve
  the Grandma/gossip reputation effect, reward Lightman's Currency coins (`lightmanscurrency:coin_*`).
  Economy mod added to the modpack (Lightman's Currency) with a KubeJS lock so coins aren't craftable from
  items (`kubejs/server_scripts/economy_coins.js`). Deploy: sync `agent/` → `/opt/custommissions-gen`.
- **0.1.6** (2026-06-24) — **Intro cutscene on first describe-close.** New `intro/IntroCutscene`: the first
  time a brand-new player closes the "describe your character" screen (Send or Esc/"Later"), fire StoryKit's
  `epilogue` via `StoryKitBridge.playCutscene`. One-shot per player (`PlayerMissions.introCinematicPlayed`,
  persistent NBT); established players are pre-marked on login so editing never replays it; disconnect
  mid-close leaves them unmarked (retries). Hooked in `SubmitDescriptionC2S` + `SaveDescriptionDraftC2S`,
  seeded in `ForgeEventHooks.onLogin`. Replaces a planned login-timer approach in StoryKit.
- **0.1.5** (2026-06-23) — **Flugel the Traveler** rename (display only) + **market gossip → reputation**:
  gossip NPC button → `GossipScreen` (pick target w/ Mojang skin via `GuiSkinCache`, 200-char tip) → Sonnet
  judges credibility (cheap insults ≈ 0) → `GossipLedger` bias (by uuid, offline-safe) for target (strong) +
  gossiper (~¼); bias + rumors feed Flugel's chat tone and personal missions. Removed player `/traveler`;
  Flugel's "talk more personally" button opens the 1:1 chat, **limited 2 msgs/10 min**. Public chat-listener
  now triggers on "flugel" or "traveler". New `KnownPlayers` roster + `mission gossip|flugel` (op,
  NPC-triggered). Sidecar gained a `gossip` mode.
- **0.1.4** (2026-06-23) — resizable mission HUD: a "HUD SIZE" drag bar in the M screen scales the left
  tracker overlay (0.5x–2.0x), persisted client-side in `config/custommissions-client.json`
  (`client.MissionClientConfig`); overlay scaled via `PoseStack` pivoted on the left-mid edge. Also fixed the
  chat-listener: `onServerChat` now uses `EventPriority.LOWEST` + `receiveCanceled=true` because the
  `voicetrans` translation mod cancels `ServerChatEvent` (a plain listener never saw it).
- **0.1.3** (2026-06-22) — Traveler chat-listener: `ServerChatEvent` watches public chat and, on a "traveler"
  mention (rate-limited), broadcasts an in-character Sonnet reply to everyone (`mode:"ambient"`, last ~5 chat
  lines as context). Sidecar made dual-mode and **deployed/running on the VPS** (`traveler-chat` service +
  `inotify-tools`), so the `/traveler` GUI answers live. Fixed `DescribeScreen` hint/counter overlapping the
  buttons.
- **0.1.2** (2026-06-22) — talk-to-the-Traveler AI chat: `TravelerChatScreen` + `/traveler`, server brain
  `ai.TravelerChat` over a request/response file contract, and a host sidecar (`agent/traveler-chat.sh` +
  systemd unit + `traveler-persona.md`) running `claude -p --model sonnet`. Mod-side built; sidecar not
  deployed (needs owner OK).
- **0.1.1** (2026-06-22) — player self-description: login prompt + `DescribeScreen` multiline box with a
  persistent draft (anti-frustration), stored in NBT and exported as `player.description` for the brain;
  `/mission describe` to edit. Removed the temporary NPC-interact diagnostic log.
- **0.1.0** (2026-06-21) — initial M1 engine (see `STATUS.md`).
