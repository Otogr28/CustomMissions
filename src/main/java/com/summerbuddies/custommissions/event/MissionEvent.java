package com.summerbuddies.custommissions.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;

/**
 * A normalized game event that mission objectives can match against. {@code event.ForgeEventHooks} and
 * {@code event.LocationScanner} translate raw Forge events into these; {@code command.MissionCommand}
 * produces {@link Signal} from {@code /mission signal}. Each objective decides, in its {@code progress},
 * whether a given event advances it.
 */
public sealed interface MissionEvent {

    /** A living entity died to the player (directly, or via a pet/projectile they own). */
    record Kill(ResourceLocation entity, Set<ResourceLocation> tags) implements MissionEvent {}

    /** The player picked up an item stack. */
    record Pickup(ResourceLocation item, int count) implements MissionEvent {}

    /** Throttled position sample for the player (drives reach_location). */
    record Move(ResourceLocation dimension, BlockPos pos) implements MissionEvent {}

    /** The player right-click-interacted with an NPC entity (used by talk_to_npc and deliver). */
    record NpcTalk(UUID uuid, String name) implements MissionEvent {}

    /** The player changed dimension. */
    record EnterDim(ResourceLocation dimension) implements MissionEvent {}

    /** The player earned an advancement. */
    record Advancement(ResourceLocation id) implements MissionEvent {}

    /** The player right-clicked a block. */
    record UseBlock(ResourceLocation block) implements MissionEvent {}

    /** The player placed a block. */
    record PlaceBlock(ResourceLocation block) implements MissionEvent {}

    /** A named custom signal emitted via {@code /mission signal} (KubeJS, EasyNPC, other mods). */
    record Signal(String name, int count) implements MissionEvent {}
}
