# CustomMissions

An AI-driven mission system for the **Realm Gates** (summerBuddies) Minecraft Forge 1.20.1 server.

- **Daily missions** — 3 per player, regenerated every 24h by an AI brain that reads each player's data
  and the server's lore stage, written in a JSON **mission language** the mod hot-loads.
- **Primary & secondary missions** — authored story/side quests that drive the lore forward.
- Objectives **listen to live server events** (kills, pickups, NPC talks, dimension entry, advancements,
  blocks) and custom **signals** from KubeJS / EasyNPC / other in-house mods.
- **Quest markers** on screen (compass + distance), with an optional JourneyMap waypoint upgrade.
- Rewards: items, XP, commands, **StoryKit cutscenes**, **Realm Gates** unlocks, **Custom Companions**, and
  server-wide **lore-stage** advancement.

In-house mod, sibling of realmgates / StoryKit / CustomCompanions / Bosses.
Mod id `custommissions`, package `com.summerbuddies.custommissions`, both-side, Forge 47.4.0.

## Docs

- **`DSL.md`** — the mission language (schema + examples). The source of truth for authors and the AI.
- **`Documentation.md`** — technical/architecture reference.
- **`STATUS.md`** — what's done, what's pending, changelog.
- **`agent/PROMPT.md`** — the daily-mission AI brain's job spec; **`agent/generate.sh`** — its run wrapper.

## Build

```
./gradlew build      # -> build/libs/custommissions-0.1.0.jar (reobfuscated, shippable)
```

Both-side mod — players need it too. Ship via `mc-mods-sync` or copy to `~/MCserver/mods/`, then the
standard MCserver flow (`git push` + `ssh mcserver mc-update`). Never restart the server without OK.
