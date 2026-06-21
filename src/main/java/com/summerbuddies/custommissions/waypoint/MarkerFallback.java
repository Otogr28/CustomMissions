package com.summerbuddies.custommissions.waypoint;

import com.summerbuddies.custommissions.mission.MissionLocation;
import com.summerbuddies.custommissions.net.MarkerS2C;
import com.summerbuddies.custommissions.net.MissionNet;
import net.minecraft.server.level.ServerPlayer;

/**
 * The guaranteed waypoint backend: pushes an in-house {@link MarkerS2C} the client renders as a compass +
 * distance overlay. Always available (both-side mod), independent of JourneyMap. A JourneyMap upgrade
 * (a real map waypoint) is layered on the client in a later milestone without changing this server path.
 */
public final class MarkerFallback {

    private MarkerFallback() {}

    public static void show(ServerPlayer player, String missionId, MissionLocation loc) {
        MissionNet.toPlayer(player, new MarkerS2C(true, missionId, loc.dimension().toString(),
                loc.x(), loc.y(), loc.z(), loc.waypointName(), loc.waypointRgb()));
    }

    public static void hide(ServerPlayer player, String missionId) {
        MissionNet.toPlayer(player, MarkerS2C.remove(missionId));
    }
}
