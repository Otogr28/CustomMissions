package com.summerbuddies.custommissions.waypoint;

import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Facade for quest markers. Given a mission and the player's current per-objective counters, it shows a
 * marker at the first incomplete {@code reach_location} objective (or the mission's headline location), and
 * clears it when there's nothing left to point at. For MVP it drives {@link MarkerFallback}; a JourneyMap
 * backend can be selected here later without touching callers.
 */
public final class WaypointService {

    private WaypointService() {}

    /** Recompute and push the marker for this mission, or hide it if no location remains. */
    public static void refresh(ServerPlayer player, Mission mission, int[] counters) {
        MissionLocation wp = mission.activeWaypoint(counters);
        if (wp != null) {
            MarkerFallback.show(player, mission.id(), wp);
        } else {
            MarkerFallback.hide(player, mission.id());
        }
    }

    /** Remove this mission's marker entirely (complete / abandon / expire). */
    public static void remove(ServerPlayer player, String missionId) {
        MarkerFallback.hide(player, missionId);
    }
}
