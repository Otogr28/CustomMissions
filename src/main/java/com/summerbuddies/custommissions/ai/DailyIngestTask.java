package com.summerbuddies.custommissions.ai;

import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.state.MissionTracker;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import com.summerbuddies.custommissions.state.PlayerMissions;
import net.minecraft.server.level.ServerPlayer;

/**
 * Auto-assigns the day's AI-authored daily missions to a player. {@link MissionManager} loads the dailies
 * under {@code daily/<today>/<uuid>/} (keyed to the owner uuid); this task takes any that the player has
 * not already accepted, completed, or let expire. Idempotent — safe to call on login and periodically. The
 * per-player completed/active/expired sets are the dedup guard, so no separate processed-marker is needed.
 */
public final class DailyIngestTask {

    private DailyIngestTask() {}

    public static int ingest(ServerPlayer player) {
        PlayerMissions pm = PlayerMissionStore.get(player);
        int assigned = 0;
        for (Mission m : MissionManager.dailiesFor(player.getUUID())) {
            if (pm.isActive(m.id()) || pm.isCompleted(m.id()) || pm.isExpired(m.id())) {
                continue;
            }
            if (MissionTracker.assign(player, m)) {
                assigned++;
            }
        }
        if (assigned > 0) {
            Constants.LOG.info("[custommissions] assigned {} daily mission(s) to {}", assigned,
                    player.getGameProfile().getName());
        }
        return assigned;
    }
}
