package com.summerbuddies.custommissions.state;

import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.net.MissionSync;
import com.summerbuddies.custommissions.reward.Reward;
import com.summerbuddies.custommissions.reward.RewardContext;
import com.summerbuddies.custommissions.waypoint.WaypointService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The server-side mission logic: assigning, completing, abandoning, and expiring missions, granting their
 * rewards, and computing what a player can currently accept. Per-event progress lives in
 * {@code event.MissionEventBus}; this class owns the lifecycle transitions and side effects (rewards,
 * waypoints, chat). All methods run on the server thread.
 */
public final class MissionTracker {

    private MissionTracker() {}

    /** Assign a mission to a player (fires onAccept hooks, pushes a waypoint). False if already taken. */
    public static boolean assign(ServerPlayer player, Mission m) {
        PlayerMissions pm = PlayerMissionStore.get(player);
        if (pm.isActive(m.id()) || pm.isCompleted(m.id()) || pm.isExpired(m.id())) {
            return false;
        }
        pm.accept(m.id(), m.objectives().size(), nowEpoch());
        PlayerMissionStore.put(player, pm);

        RewardContext ctx = new RewardContext(server(player));
        grant(player, ctx, m.onAccept());
        WaypointService.refresh(player, m, new int[m.objectives().size()]);

        player.sendSystemMessage(Component.literal("New mission: " + m.title())
                .withStyle(ChatFormatting.GOLD));
        if (!m.description().isEmpty()) {
            player.sendSystemMessage(Component.literal("  " + m.description()).withStyle(ChatFormatting.GRAY));
        }
        for (var o : m.objectives()) {
            player.sendSystemMessage(Component.literal("  • " + o.describe()).withStyle(ChatFormatting.YELLOW));
        }
        MissionSync.send(player);
        return true;
    }

    /** Mark a mission complete, grant its rewards once (idempotent), and clear its waypoint. */
    public static void complete(ServerPlayer player, Mission m) {
        PlayerMissions pm = PlayerMissionStore.get(player);
        boolean firstTime = !pm.isClaimed(m.id());
        pm.complete(m.id());
        pm.markClaimed(m.id());
        PlayerMissionStore.put(player, pm);
        WaypointService.remove(player, m.id());

        if (firstTime) {
            RewardContext ctx = new RewardContext(server(player));
            grant(player, ctx, m.rewards());
            grant(player, ctx, m.onComplete());
            player.sendSystemMessage(Component.literal("Mission complete: " + m.title())
                    .withStyle(ChatFormatting.GREEN));
        }
        MissionSync.send(player);
    }

    /** Drop an active mission and remove its waypoint. */
    public static boolean abandon(ServerPlayer player, String id) {
        PlayerMissions pm = PlayerMissionStore.get(player);
        if (!pm.isActive(id)) {
            return false;
        }
        pm.abandon(id);
        PlayerMissionStore.put(player, pm);
        WaypointService.remove(player, id);
        MissionSync.send(player);
        return true;
    }

    /** Expire active daily missions that have timed out (or whose day's catalog is gone). */
    public static int expireDailies(ServerPlayer player) {
        long now = nowEpoch();
        PlayerMissions pm = PlayerMissionStore.get(player);
        int expired = 0;
        for (String id : pm.activeIds()) {
            Mission m = MissionManager.get(id);
            boolean staleDaily = m == null && id.startsWith("daily_");
            boolean timedOut = m != null && m.isDaily() && m.expirySeconds() > 0
                    && pm.acceptedAt(id) > 0 && (now - pm.acceptedAt(id)) > m.expirySeconds();
            if (staleDaily || timedOut) {
                pm.expire(id);
                WaypointService.remove(player, id);
                expired++;
                String label = m != null ? m.title() : id;
                player.sendSystemMessage(Component.literal("Daily mission expired: " + label)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (expired > 0) {
            PlayerMissionStore.put(player, pm);
            MissionSync.send(player);
        }
        return expired;
    }

    /** Missions this player may currently accept: authored, prerequisites met, not active/completed. */
    public static List<Mission> available(ServerPlayer player) {
        LoreState ls = LoreState.get(player.serverLevel());
        PlayerMissions pm = PlayerMissionStore.get(player);
        ResourceLocation dim = player.serverLevel().dimension().location();
        List<Mission> out = new ArrayList<>();
        for (Mission m : MissionManager.all()) {
            if (m.isDaily()) {
                continue; // dailies are auto-assigned by the ingest task, not accepted from a pool
            }
            if (pm.isActive(m.id()) || pm.isCompleted(m.id())) {
                continue;
            }
            if (m.prerequisites().met(ls.stage(), ls.flags(), pm.completedIds(), dim)) {
                out.add(m);
            }
        }
        return out;
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static void grant(ServerPlayer player, RewardContext ctx, List<Reward> rewards) {
        for (Reward r : rewards) {
            try {
                r.grant(player, ctx);
            } catch (Exception e) {
                Constants.LOG.warn("[custommissions] reward failed ({}): {}", r.describe(), e.toString());
            }
        }
    }

    private static MinecraftServer server(ServerPlayer player) {
        return player.serverLevel().getServer();
    }

    public static long nowEpoch() {
        return System.currentTimeMillis() / 1000L;
    }
}
