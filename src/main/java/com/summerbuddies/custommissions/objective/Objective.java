package com.summerbuddies.custommissions.objective;

import com.summerbuddies.custommissions.event.MissionEvent;
import com.summerbuddies.custommissions.mission.MissionLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * One step of a mission. An objective is satisfied once its progress counter reaches {@link #required()}.
 * The engine calls {@link #progress} for every active mission objective on every {@link MissionEvent};
 * the objective returns how much to add to its counter (0 when the event is irrelevant). Implementations
 * are immutable records in {@link Objectives}; any per-player state lives in the player's progress NBT.
 */
public interface Objective {

    ObjectiveType type();

    /** Total count needed to complete (>= 1). */
    int required();

    /** Short human-readable line for the quest tracker. */
    String describe();

    /** Optional objectives never block mission completion (reserved for future use; default required). */
    default boolean optional() {
        return false;
    }

    /**
     * How much this objective advances for the given event, considering the acting player. May have side
     * effects for objectives that consume resources (e.g. deliver removes items). Returns 0 if no match.
     */
    int progress(MissionEvent event, ServerPlayer player);

    /** The waypoint to mark while this objective is incomplete, or null if it has no location. */
    @Nullable
    default MissionLocation waypoint() {
        return null;
    }
}
