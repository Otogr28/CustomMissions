package com.summerbuddies.custommissions.mission;

import com.summerbuddies.custommissions.objective.Objective;
import com.summerbuddies.custommissions.reward.Reward;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable, fully-built mission: its identity and presentation, the gating {@link Prerequisites}, the
 * ordered {@link Objective}s, the {@link Reward}s granted on complete, and the on-accept hooks. Built from
 * a {@code json.MissionDto} by {@code MissionManager}. Daily missions additionally carry an {@code owner}
 * (the player whose {@code daily/<date>/<uuid>/} dir they were dropped in) and an {@code expirySeconds}.
 * When {@code autoAccept} is set, the mission is forced onto a player the moment its prerequisites are met
 * (no manual accept) — used for important/story missions that must not be missed.
 */
public record Mission(String id, MissionCategory category, String title, String description, String lore,
                      Giver giver, Prerequisites prerequisites, List<Objective> objectives,
                      List<Reward> rewards, List<Reward> onAccept, List<Reward> onComplete,
                      @Nullable MissionLocation location, long expirySeconds,
                      @Nullable UUID owner, Set<String> assignTo, boolean autoAccept) {

    public boolean isDaily() {
        return category == MissionCategory.DAILY;
    }

    /** The waypoint to show given current per-objective counters: the first incomplete reach objective. */
    @Nullable
    public MissionLocation activeWaypoint(int[] counters) {
        for (int i = 0; i < objectives.size(); i++) {
            Objective o = objectives.get(i);
            MissionLocation wp = o.waypoint();
            if (wp != null) {
                boolean done = i < counters.length && counters[i] >= o.required();
                if (!done) {
                    return wp;
                }
            }
        }
        return location;
    }

    /** True once every (non-optional) objective's counter has reached its required count. */
    public boolean isComplete(int[] counters) {
        for (int i = 0; i < objectives.size(); i++) {
            Objective o = objectives.get(i);
            if (o.optional()) {
                continue;
            }
            if (i >= counters.length || counters[i] < o.required()) {
                return false;
            }
        }
        return true;
    }
}
