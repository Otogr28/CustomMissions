package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.objective.Objective;
import com.summerbuddies.custommissions.reward.Reward;
import com.summerbuddies.custommissions.state.MissionTracker;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import com.summerbuddies.custommissions.state.PlayerMissions;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and pushes a {@link MissionSyncS2C} snapshot to a player. Call after any change to their
 * missions (assign/progress/complete/abandon/expire), on login, and on a client request. This is the only
 * thing the adventure-log screen consumes.
 */
public final class MissionSync {

    private static final int MAX_COMPLETED = 100;

    private MissionSync() {}

    public static void send(ServerPlayer player) {
        PlayerMissions pm = PlayerMissionStore.get(player);

        List<MissionSyncS2C.Entry> active = new ArrayList<>();
        for (String id : pm.activeIds()) {
            Mission m = MissionManager.get(id);
            int[] c = pm.counters(id);
            if (m != null && c != null) {
                active.add(entry(m, c, false));
            }
        }

        List<MissionSyncS2C.Entry> available = new ArrayList<>();
        for (Mission m : MissionTracker.available(player)) {
            available.add(entry(m, new int[m.objectives().size()], false));
        }

        List<MissionSyncS2C.Entry> completed = new ArrayList<>();
        for (String id : pm.completedIds()) {
            Mission m = MissionManager.get(id);
            if (m != null) {
                completed.add(entry(m, new int[m.objectives().size()], true));
                if (completed.size() >= MAX_COMPLETED) {
                    break;
                }
            }
        }

        MissionNet.toPlayer(player, new MissionSyncS2C(active, available, completed));
    }

    private static MissionSyncS2C.Entry entry(Mission m, int[] counters, boolean allDone) {
        List<MissionSyncS2C.Obj> objs = new ArrayList<>();
        for (int i = 0; i < m.objectives().size(); i++) {
            Objective o = m.objectives().get(i);
            int have = allDone ? o.required() : (i < counters.length ? Math.min(counters[i], o.required()) : 0);
            objs.add(new MissionSyncS2C.Obj(o.describe(), have, o.required()));
        }
        List<String> rewards = new ArrayList<>();
        for (Reward r : m.rewards()) {
            rewards.add(r.describe());
        }
        String giver = m.giver().isPresent() ? m.giver().describe() : "";
        return new MissionSyncS2C.Entry(m.id(), m.title(), m.description(), m.lore(), giver,
                m.category().ordinal(), objs, rewards);
    }
}
