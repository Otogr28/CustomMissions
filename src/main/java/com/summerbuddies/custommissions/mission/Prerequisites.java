package com.summerbuddies.custommissions.mission;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Gating for a mission to become available: a minimum server-wide lore stage, required lore flags,
 * prior missions the player must have completed, and an optional dimension the player must currently be
 * in. All conditions are AND-ed; an empty {@link Prerequisites} is always met.
 */
public record Prerequisites(int loreStage, Set<String> flags, Set<String> priorMissions,
                            @Nullable ResourceLocation dimension) {

    public static final Prerequisites NONE = new Prerequisites(0, Set.of(), Set.of(), null);

    /**
     * @param currentLoreStage   the server-wide lore stage
     * @param loreFlags          the server-wide lore flags
     * @param completedMissions  this player's completed mission ids
     * @param currentDimension   the dimension this player is in (may be null when checked offline)
     */
    public boolean met(int currentLoreStage, Set<String> loreFlags, Set<String> completedMissions,
                       @Nullable ResourceLocation currentDimension) {
        if (currentLoreStage < loreStage) {
            return false;
        }
        if (!loreFlags.containsAll(flags)) {
            return false;
        }
        if (!completedMissions.containsAll(priorMissions)) {
            return false;
        }
        if (dimension != null && !dimension.equals(currentDimension)) {
            return false;
        }
        return true;
    }
}
