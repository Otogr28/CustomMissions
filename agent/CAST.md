# CAST.md — Who lives in Aincrad (for the daily-mission brain)

> The NPCs of the home village in **Aincrad** (the overworld). Use them to make dailies feel inhabited:
> attribute a quest to a giver, send a player to talk/deliver, reference a villager's trade. All
> player-facing text is **English**, in the giver's voice, short and evocative. **Never reveal the hidden
> plot** (see bottom). Full dialogues live in the `npcs/` vault; this is the curated digest.

## The giver
- **Flugel the Traveler** — uuid `d48a3f45-0efd-46c6-9803-5e1256d95d33`. The central plot NPC and your
  DEFAULT giver. A wandering, ageless guide: warm but weathered, wry, calm, a little cryptic. He KNOWS the
  threat and hides it on purpose; he nudges players toward courage and exploration. Most dailies are his.

## Grandma — the rumor-keeper (this is the "grandma effect" on dailies)
- **Grandma** — uuid `9ed8a056-be82-4cb3-a480-62b4e1285feb`. The warm old woman of the village, and its
  **gossip / rumor-keeper**. Players bring her tips about each other; what she hears becomes a player's
  **reputation**, surfaced in `PLAYER_CONTEXT` as `player.gossipBias` (-100 wary .. +100 trusted) and
  `player.rumors`. **Honor it when authoring:** a well-regarded player (positive bias) earns missions of
  trust and import (favours that matter, things Flugel or Grandma would only ask of someone reliable); a
  poorly-regarded one (negative bias) gets warier, proving-ground tasks. `rumors` are flavour only — let
  them colour the framing, but NEVER quote them or accuse the player. Near 0 = neutral; ignore it. You may
  attribute a gossip-flavoured daily to Grandma ("Grandma asks a quiet favour…").

## Merchants (deliver / "earn coin" / trade-flavoured dailies)
- **Satella** — uuid `c2b47de1-34f6-4144-8425-0f54da1d14df`. The village alchemist-merchant: a sweet, shy,
  lonely girl (Emilia-like, NOT a villain) who sells brewing reagents and potions for metals/gems. She
  senses "something is coming." Good for gather-for-her / deliver dailies.
- **Verity** — uuid `838c8980-8e21-48b4-ad6f-48f653c793c5`. The "helper" who trades MAPS and COMPASSES to
  structures; kind on the surface, quietly possessive underneath; hints "something is coming." Natural giver
  for explore / find-a-structure dailies.

## Villagers of Aincrad (flavour; talk / deliver targets)
- **Kirigaya** — uuid `eaf9b48a-7a24-4d61-a399-20eec9f0414b`. The village chief: warm, big-hearted host,
  deep reassuring voice. Genuinely IGNORANT of any threat — clean warmth, zero foreshadowing; never let him
  hint at danger. Good for welcoming / home / hospitality dailies.
- **derp**, **Jeff**, **Fabricio** — named village folk; welcoming, light tone.
- **Leck** (reserved), **Brook** (ceremonious, macabre humour), **Naif** (naïve, trusting),
  **Ame** (cheerful, chatty), **Silvia** (serene, martial) — keep them wholesome; use lightly as
  talk/deliver targets or for colour.

## HARD: do not reveal the hidden plot
There is a hidden antagonist and a deeper story (a Witch, a creeping Blight/Corruption, sealed realms, a
Great Ancestor). Dailies MAY use the **Blight/Corruption** as ambient dread and reference the heat realm,
but must **never name or explain** the villain, the seals, or any later-chapter secret. Foreshadow and
deflect; keep the mystery. Only Flugel, Verity and Satella carry the faint "something is coming" unease —
Kirigaya and the other villagers do not.
