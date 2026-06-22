package com.summerbuddies.custommissions.event;

import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.net.MissionSync;
import com.summerbuddies.custommissions.objective.Objective;
import com.summerbuddies.custommissions.state.MissionTracker;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import com.summerbuddies.custommissions.state.PlayerMissions;
import com.summerbuddies.custommissions.waypoint.WaypointService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The single funnel for objective progress. {@code ForgeEventHooks}, {@code LocationScanner}, and
 * {@code /mission signal} all normalize their input into a {@link MissionEvent} and call
 * {@link #dispatch}. It advances every matching objective on the player's active missions, refreshes
 * waypoints, and hands completed missions to {@link MissionTracker}. Server thread only.
 */
public final class MissionEventBus {

    private MissionEventBus() {}

    public static void dispatch(ServerPlayer player, MissionEvent event) {
        PlayerMissions pm = PlayerMissionStore.get(player);
        Set<String> activeIds = pm.activeIds();
        if (activeIds.isEmpty()) {
            return;
        }
        boolean changed = false;
        List<Mission> completedNow = new ArrayList<>();

        for (String id : activeIds) {
            Mission m = MissionManager.get(id);
            if (m == null) {
                continue;
            }
            int[] counters = pm.counters(id);
            if (counters == null) {
                continue;
            }
            if (counters.length != m.objectives().size()) {
                counters = resize(counters, m.objectives().size());
            }

            boolean missionChanged = false;
            for (int i = 0; i < m.objectives().size(); i++) {
                Objective o = m.objectives().get(i);
                if (counters[i] >= o.required()) {
                    continue;
                }
                int delta = o.progress(event, player);
                if (delta > 0) {
                    counters[i] = Math.min(o.required(), counters[i] + delta);
                    missionChanged = true;
                }
            }

            if (missionChanged) {
                pm.setCounters(id, counters);
                changed = true;
                if (m.isComplete(counters)) {
                    completedNow.add(m);
                } else {
                    WaypointService.refresh(player, m, counters);
                    sendProgress(player, m, counters);
                }
            }
        }

        if (changed) {
            PlayerMissionStore.put(player, pm);
        }
        for (Mission m : completedNow) {
            MissionTracker.complete(player, m); // pushes its own snapshot
        }
        if (changed && completedNow.isEmpty()) {
            MissionSync.send(player); // progress without completion still refreshes the screen
        }
    }

    private static void sendProgress(ServerPlayer player, Mission m, int[] counters) {
        int done = 0;
        for (int i = 0; i < m.objectives().size(); i++) {
            if (counters[i] >= m.objectives().get(i).required()) {
                done++;
            }
        }
        player.displayClientMessage(Component.literal(m.title() + "  " + done + "/" + m.objectives().size())
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static int[] resize(int[] src, int len) {
        int[] out = new int[len];
        System.arraycopy(src, 0, out, 0, Math.min(src.length, len));
        return out;
    }
}
