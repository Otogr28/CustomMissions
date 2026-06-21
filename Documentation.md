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
  reward/
    Reward (iface), RewardType (enum), Rewards (records), RewardFactory, RewardContext
  waypoint/
    WaypointService (facade), MarkerFallback (S2C backend)
  net/
    MissionNet (channel), MarkerS2C
  command/MissionCommand    /mission tree
  bridge/
    StoryKitBridge, RealmGatesBridge, CompanionsBridge, EasyNpcBridge
  client/ (@Dist.CLIENT)
    ClientMissions, MarkerOverlay
  ai/
    MissionContextExporter, WorldStateExporter, DailyIngestTask
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
| `kill_entity` | player kill (direct or pet/projectile) | `entity` (id or `#tag`), `count` |
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

- Mod writes `state/context/<uuid>.json` (per player: lore stage, dimension, active/completed missions,
  vanilla stat snapshot) and `state/world_state.json` (lore chapter/stage, flags, gates, dimensions,
  givers, online roster). Refreshed on logout + every ~30s while online.
- Brain writes `daily/<today>/<uuid>/NN.json` (3 per player) in the daily DSL, then runs
  `mc-cmd 'mission reload'`. `DailyIngestTask` auto-assigns each player's dailies on login/periodically.

## Commands

`/mission` — players: `list`, `progress`, `accept <id>`, `abandon <id>`, `signal <name> [count] [players]`.
Ops (perm 2): `reload`, `assign <player> <id>`, `complete <player> <id>`, `daily regen <players>`,
`lorestage get|set <chapter> <stage>`, `flag add|remove <name>`, `waypoint debug <players>`,
`export world|player <players>`.

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

- **0.1.0** (2026-06-21) — initial M1 engine (see `STATUS.md`).
