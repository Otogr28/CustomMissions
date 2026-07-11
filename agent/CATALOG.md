# CATALOG.md — What actually lives on this server (for the daily-mission brain)

> Real, **verified** ids for THIS server (Forge 1.20.1, "Realm Gates" / summerBuddies). Use them to make
> dailies feel modded and specific instead of generic vanilla. Two hard distinctions:
> - **WILD** = spawns naturally → fine for `kill_entity` / hunt dailies. Always name WHERE it spawns.
> - **NOT WILD** (boss / structure / summon / event) → NEVER a casual kill daily; players can't find them.
>
> Rule: only use ids that appear in this file, in `WORLD_STATE` (dimensions/givers/flags), or a plain
> `minecraft:` id. Never invent a modded id. When unsure, fall back to vanilla or `custom_signal`.

## Dimensions
- `minecraft:overworld` — HOME. Call it **Aincrad** in player-facing text. Terralith + Biomes We've Gone +
  vanilla biomes; Alex's Caves biomes are UNDERGROUND here; the corruption biome `realmgates:wasteland` sits
  inside it.
- `realmgates:heatdeath` — tier-1 heat realm (lethal **Warmth** mechanic). Gated — only send a player here if
  it is in `WORLD_STATE.knownDimensions`/`unlockedGates` and their `loreStage` allows it.
- Ignore `realmgates:v1` (retired) and `sb:wither_realm` (not reachable).

## WILD mobs — safe `kill_entity` / hunt targets (always name the place)
- **Creeper Overhaul** (Aincrad, per biome, replaces vanilla creeper): `creeperoverhaul:desert_creeper`,
  `badlands_creeper`, `jungle_creeper`, `swamp_creeper`, `cave_creeper`, `dripstone_creeper`,
  `snowy_creeper`, `savannah_creeper`, `ocean_creeper`, `beach_creeper`, `spruce_creeper`, `dark_oak_creeper`.
- **Born in Chaos** (Aincrad, at NIGHT, any biome): `born_in_chaos_v1:decaying_zombie`, `zombie_fisherman`,
  `decrepit_skeleton`, `baby_spider`, `dread_hound`, `zombie_bruiser`, `skeleton_thrasher`, `mr_pumpkin`,
  `phantom_creeper`. (Time-gated rares like lifestealer/krampus/pumpkinhead are NOT reliable dailies.)
- **Ice and Fire** (Aincrad, biome-tied — NO wild dragons here): `iceandfire:cyclops` (caves/beach),
  `iceandfire:troll` (caves/mountains), `iceandfire:deathworm` (deserts), `iceandfire:sea_serpent` (ocean),
  `iceandfire:siren` (ocean), `iceandfire:amphithere` (jungle), `iceandfire:stymphalian_bird`,
  `iceandfire:cockatrice` (warning: petrifies), dread undead near mausoleums (`iceandfire:dread_thrall`,
  `dread_ghoul`, `dread_beast`). Hippogryph/hippocampus are tameable mounts (use for "find/tame" flavor).
- **Alex's Caves** (Aincrad, only INSIDE the matching underground cave biome — tell them to delve there):
  Primordial: `alexscaves:tremorsaurus`, `grottoceratops`, `vallumraptor`, `relicheirus`. Toxic:
  `alexscaves:nucleeper`, `brainiac`, `raycat`. Magnetic: `alexscaves:boundroid`, `teletor`, `ferrouslime`.
  Abyssal: `alexscaves:deep_one`, `hullbreaker`. Forlorn: `alexscaves:underzealot`, `vesper`, `gloomoth`.
  Candy: `alexscaves:caniac`, `gummy_bear`, `gum_worm`.
- **Desert biomes** (Aincrad deserts + heatdeath): `desert_tomb:scorpion`, `desert_tomb:mummy`.
- **`realmgates:heatdeath`** (Warmth waves + ambient): `iceandfire:deathworm` (scales with Warmth),
  `born_in_chaos_v1:dread_hound`/`zombie_bruiser`/`skeleton_thrasher`/`seared_spirit`, plus husk/skeleton.
- **`realmgates:wasteland` biome** (inside Aincrad): ONLY `realmgates:shade` spawns; everything else blocked.
- **Vanilla** mobs spawn normally in Aincrad — fine to use, just keep them varied.

## NOT WILD — never a "go kill one" daily (no natural spawn here)
- **All `cataclysm:*`** — Cataclysm structures are OFF (datapack empties them + namespace blocked). Its
  bosses (`netherite_monstrosity`, `ender_guardian`, `ignis`, `the_leviathan`, `the_harbinger`,
  `ancient_remnant`, `scylla`) and dungeon mobs (`deepling`, `draugr`, `koboleton`…) cannot be found.
- **All `block_factorys_bosses:*`** (Bosses'Rise: `infernal_dragon`, `kraken`, `sandworm`,
  `underworld_knight`, `yeti`…) — admin `/bossrise place` only; their structures are warded.
- **In-house `bosses` mod** entities — `/boss spawn` only.
- **Ice and Fire apex/bosses** — `iceandfire:fire_dragon`/`ice_dragon`/`lightning_dragon` (roosts off in
  Aincrad), `iceandfire:hydra`, `iceandfire:gorgon`.
- **Alex's Caves kaiju/boss** — `alexscaves:tremorzilla`, `luxtructosaurus`, `forsaken`, `watcher`.
- **Born in Chaos bosses/events** — `lord_pumpkinhead_head`, `infernal_spirit`, `*_not_despawn`, plus the
  time-gated `lifestealer`, `nightmare_stalker`, `krampus`.
- **Ars Nouveau** `ars_nouveau:wilden_boss` (Chimera).
> You may reference these in mission TEXT as lore/foreshadow, but never as a `kill_entity` objective.

## Gather / craft items (collect / advancement / deliver dailies)
- **Ice and Fire:** `iceandfire:dragonbone`, `fire_dragon_blood`, `witherbone`, `sea_serpent_scales_blue`.
- **Alex's Caves:** `alexscaves:uranium`, `cave_map`, `cave_book`.
- **Born in Chaos drops:** `born_in_chaos_v1:seedof_chaos`, `shattered_skull`, `pieceofdarkmetal`,
  `ethereal_spirit`, `death_totem`.
- **Ars Nouveau:** `ars_nouveau:source_gem`, `magebloom`, `novice_spell_book`; passive mobs to find/charm:
  `ars_nouveau:starbuncle`, `drygmy`, `whirlisprig`.
- **Farm & Charm crops:** `farm_and_charm:strawberry`, `corn`, `tomato`, `onion`, `barley`, `flour`.
- **Candlelight food:** `candlelight:fillet_steak`, `bolognese`, `mushroom_soup`.
- **Supplementaries:** `supplementaries:flax`, `soap`, `quiver`, `bomb`, `slingshot`.
- **Modded gear:** `more_bows_and_arrows:diamond_bow`/`amethyst_bow`/`blaze_bow`, `mace_port:mace`,
  spears (spear-backport registers them as `minecraft:wooden_spear`/`iron_spear`/`diamond_spear`/`netherite_spear`).
- **Trial Chambers (NewTrials):** `ntrials:trial_key`, `ominous_trial_key`, `ntrials:mace`.

## Blocks to place / use (build & interact dailies)
- Build/decorate (`place_block`): `supplementaries:sconce`/`jar`/`sign_post`/`notice_board`/`safe`/`globe`,
  `suppsquared:copper_lantern`, `amendments:wall_lantern`/`tool_hook`, `tanukidecor:grandfather_clock`,
  `beautify:botanist_workbench`, `farm_and_charm:scarecrow`/`cooking_pot`, `candlelight:hearth`.
- Stations (`use_block`): `ars_nouveau:scribes_table`/`imbuement_chamber`/`arcane_pedestal`/`ritual_brazier`,
  `waystones:waystone`, `easy_villagers:trader`/`farmer`/`breeder`, `toms_storage:ts.storage_terminal`.

## Structures to explore (reach_location / enter / find — these DO generate)
- **When Dungeons Arise** (Aincrad surface/ocean): undead pirate ship, illager galley, airship blimp,
  coliseum, shiraz palace, thornborn towers, mushroom village, plague asylum, keep kayra.
- **Dungeon Crawl:** big layered underground dungeon. **NewTrials:** Trial Chambers (underground).
- **YUNG's:** Better Desert Temples, Better Strongholds, Better Nether Fortresses, Better End Island.
- **Ice and Fire:** gorgon temple, mausoleums, graveyards. **Desert Tomb:** the desert tomb (in deserts).
- Do NOT send players to find Cataclysm or Bosses'Rise structures (off / warded).

## Economy — coins as rewards (Lightman's Currency)
Coins are unique, admin/quest-only items. Use them as `reward: item`. Tiers (low → high):
`lightmanscurrency:coin_copper` < `coin_iron` < `coin_gold` < `coin_emerald` < `coin_diamond` < `coin_netherite`.
Daily rewards lean **copper/iron** (a few), **gold** for a tougher objective. Never hand a diamond/netherite
coin for a one-session daily.

## Variety menu — rotate objective TYPES (do not default to kill + reach)
`kill_entity` (WILD only) · `collect_item` (modded mats above) · `reach_location` (a real structure/biome) ·
`enter_dimension` · `advancement` · `place_block` (build) · `use_block` (a station) · `deliver_item_to_npc`
(to a giver) · `talk_to_npc` · `custom_signal`. Across a player's 3 dailies, use 3 DIFFERENT types.
