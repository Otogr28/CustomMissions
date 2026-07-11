# The CustomMissions Mission Language (DSL)

A mission is one JSON object in a `.json` file. Authored chains go in `config/custommissions/missions/`;
AI dailies go in `config/custommissions/daily/<YYYY-MM-DD>/<player-uuid>/NN.json` (one mission per file,
3 files per player per day). The loader is **forgiving**: unknown fields are ignored, an unknown
objective/reward `type` is skipped with a warning (the rest of the mission still loads), and every
numeric field has a default. Reload at runtime with `/mission reload` — no restart.

## Top-level object

| field | type | required | notes |
|---|---|---|---|
| `id` | string | yes (recommended) | unique. Defaults to the filename. Dailies: `daily_<date>_<name>_NN`. |
| `category` | string | yes | `daily` \| `primary` \| `secondary` (default `secondary`). |
| `title` | string | yes | short display title. |
| `description` | string | no | one or two sentences shown on accept. |
| `lore` | string | no | flavor text. |
| `giver` | object | no | `{ "npcUuid": "...", "npcName": "..." }` — the NPC offering it. |
| `prerequisites` | object | no | gating, see below. Empty = always available. |
| `objectives` | array | yes | 1+ objectives, see below. A mission with none is skipped. |
| `rewards` | array | no | granted on completion. |
| `onAccept` | array | no | rewards/commands fired when the mission is accepted. |
| `onComplete` | array | no | rewards/commands fired on completion (in addition to `rewards`). |
| `location` | object | no | headline marker `{dimension,x,y,z,radius,waypoint,waypointColor}`. |
| `expiryHours` | int | daily only | hours before an accepted daily expires (default 24). |
| `assignTo` | array | no | player names; informational. Dailies are owned by their folder's uuid. |
| `autoAccept` | bool | no | if `true`, the mission is force-assigned the instant its `prerequisites` are met (no manual accept) — for important/story missions that must not be missed. Re-checked on login, dimension change, every ~30s, and right after any completion (so a chain's next link pops by itself). Default `false`. |

### prerequisites

```json
"prerequisites": {
  "loreStage": 1,                         // min server-wide lore stage
  "flags": ["met_traveler"],              // required server-wide lore flags
  "priorMissions": ["primary_ch1_02"],    // missions this player must have completed
  "dimension": "realmgates:heatdeath"     // player must currently be in this dimension
}
```

## Objectives

Each objective is `{ "type": "...", "description": "...", "optional": false, ... }` plus type-specific
fields. `description` shows in the tracker; `count` defaults to 1.

| type | fields | meaning |
|---|---|---|
| `kill_entity` | `entity`, `count` | kill N of an entity id, or any in a tag if `entity` starts with `#`. Summoned minions never count (any entity with a scoreboard tag containing "summon", e.g. Custom Companions' `cc_summon`). |
| `collect_item` | `item`, `count` | pick up N of an item. |
| `reach_location` | `dimension`,`x`,`y`,`z`,`radius`,`waypoint`,`waypointColor` | go within `radius` (default 6) of a point. Drives the on-screen marker. |
| `talk_to_npc` | `npcUuid` and/or `npcName` | interact with that EasyNPC. |
| `enter_dimension` | `dimension` | enter (or be in) a dimension. |
| `advancement` | `advancement` | earn an advancement id. |
| `use_block` | `block`, `count` | right-click a block id N times. |
| `place_block` | `block`, `count` | place a block id N times. |
| `deliver_item_to_npc` | `npcUuid`/`npcName`, `item`, `count` | interact with the NPC while carrying N items; they're consumed. |
| `custom_signal` | `signal`, `count` | advance when `/mission signal <signal>` fires N times. The universal hook for KubeJS/EasyNPC/other mods. |

Ids are namespaced resource locations (`minecraft:diamond`, `iceandfire:fire_dragon`,
`realmgates:heatdeath`). Tags use `#namespace:path`. `waypointColor` is `"#RRGGBB"`.

## Rewards (also usable in `onAccept` / `onComplete`)

| type | fields | effect |
|---|---|---|
| `item` | `item`, `count` | give an item stack. |
| `xp` | `amount` | give experience points. |
| `command` | `command`, `asPlayer` | run a command (`{player}` → name); `asPlayer:true` runs as the player. |
| `cutscene` | `script` | play a StoryKit sequence (`/story play`). |
| `unlock` | `gate` | open a Realm Gates dimension for the player. |
| `companion` | `companion` | grant a Custom Companions companion. |
| `lore_stage_advance` | `to` | set the server lore stage to `to`, or +1 if omitted. |

Cross-mod rewards (`cutscene`, `unlock`, `companion`) are no-ops if that mod is absent.

**Coins (economy).** Coins are plain items (Lightman's Currency), so reward them with `item`:
`lightmanscurrency:coin_copper` < `coin_iron` < `coin_gold` < `coin_emerald` < `coin_diamond` <
`coin_netherite`. Coins are admin/quest-only (not craftable from materials), so missions are a primary way
players earn money. Dailies lean copper/iron; reserve higher tiers for primary-chain milestones.

## Examples

### Daily (AI-authored, expires)

```json
{
  "id": "daily_2026-06-21_LUCARDGO_01",
  "category": "daily",
  "title": "The Hounds at Dusk",
  "description": "Something restless prowls the wastes of v1. Thin the pack before it grows bold.",
  "lore": "Aincrad's dark does not wait for the brave to be ready.",
  "giver": { "npcUuid": "d48a3f45-0efd-46c6-9803-5e1256d95d33", "npcName": "the-traveler" },
  "assignTo": ["LUCARDGO"],
  "expiryHours": 24,
  "prerequisites": { "loreStage": 1 },
  "objectives": [
    { "type": "kill_entity", "entity": "born_in_chaos_v1:dread_hound", "count": 3, "description": "Slay dread hounds in v1" },
    { "type": "reach_location", "dimension": "realmgates:v1", "x": 120, "y": 90, "z": -340,
      "radius": 6, "waypoint": "Old Spire", "waypointColor": "#E8C170", "description": "Reach the Old Spire" }
  ],
  "rewards": [
    { "type": "command", "command": "xp add {player} 1 levels" },
    { "type": "item", "item": "lightmanscurrency:coin_copper", "count": 1 },
    { "type": "item", "item": "lightmanscurrency:coin_iron", "count": 1 }
  ],
  "onComplete": [ { "type": "cutscene", "script": "daily_reward_flash" } ]
}
```

### Primary (authored chain, advances the lore)

```json
{
  "id": "primary_ch1_03_the_first_gate",
  "category": "primary",
  "title": "The First Gate",
  "description": "Find the dormant altar and wake it.",
  "lore": "Before the realms split, one gate stood open...",
  "giver": { "npcUuid": "d48a3f45-0efd-46c6-9803-5e1256d95d33", "npcName": "the-traveler" },
  "prerequisites": { "loreStage": 0, "priorMissions": ["primary_ch1_02_the_warning"], "flags": ["met_traveler"] },
  "objectives": [
    { "type": "talk_to_npc", "npcUuid": "d48a3f45-0efd-46c6-9803-5e1256d95d33", "description": "Speak with the Traveler" },
    { "type": "place_block", "block": "realmgates:altar", "count": 1, "description": "Set the altar" },
    { "type": "custom_signal", "signal": "altar_lit", "count": 1, "description": "Ignite the altar" }
  ],
  "rewards": [
    { "type": "unlock", "gate": "realmgates:heatdeath" },
    { "type": "lore_stage_advance", "to": 1 }
  ],
  "onComplete": [ { "type": "cutscene", "script": "ch1_gate_opens" } ]
}
```

### Secondary (side quest, deliver + collect)

```json
{
  "id": "secondary_traveler_supplies",
  "category": "secondary",
  "title": "Supplies for the Road",
  "description": "Bring the Traveler what he needs to mend his pack.",
  "giver": { "npcName": "the-traveler", "npcUuid": "d48a3f45-0efd-46c6-9803-5e1256d95d33" },
  "prerequisites": { "loreStage": 0 },
  "objectives": [
    { "type": "collect_item", "item": "minecraft:leather", "count": 8, "description": "Gather leather" },
    { "type": "deliver_item_to_npc", "npcUuid": "d48a3f45-0efd-46c6-9803-5e1256d95d33",
      "item": "minecraft:leather", "count": 8, "description": "Deliver to the Traveler" }
  ],
  "rewards": [
    { "type": "companion", "companion": "wolf_scout" },
    { "type": "xp", "amount": 50 }
  ]
}
```

## Authoring rules of thumb (for the AI brain)

- Only use entity/item/dimension/block ids that exist on this server. The brain is fed `agent/CATALOG.md`
  (the real modded ids: which mobs are WILD vs boss/structure-only, gather items, structures, coins) plus
  `state/world_state.json → knownDimensions`. Never invent a modded id.
- Vary the objective types and lean modded: don't write three kill/reach dailies of vanilla mobs. Mix
  combat/explore/build/gather/deliver/use, and only `kill_entity` a mob CATALOG marks WILD.
- Respect each player's `loreStage`; never reference incomplete primary-chain steps.
- Keep dailies achievable in one session: small counts, nearby locations, the dimensions the player can
  reach. Lean on the player's recent stats/dimension from `state/context/<uuid>.json`.
- Give every `reach_location` a meaningful `waypoint` name and a fitting `waypointColor`.
- For anything the engine can't observe directly, use `custom_signal` and document who emits it.
