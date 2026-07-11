package com.summerbuddies.custommissions.state;

import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionCategory;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.net.MissionSync;
import com.summerbuddies.custommissions.reward.Reward;
import com.summerbuddies.custommissions.reward.RewardContext;
import com.summerbuddies.custommissions.waypoint.WaypointService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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

        announceNewMission(player, m);
        MissionSync.send(player);
        return true;
    }

    /**
     * Route a "new mission" cue by category. Only PRIMARY (story) missions get the big, hard-to-miss
     * on-screen title banner + fanfare; daily and secondary missions stay quiet and announce in chat only,
     * so the auto-assigned dailies don't spam the screen banner over and over.
     */
    private static void announceNewMission(ServerPlayer player, Mission m) {
        if (m.category() == MissionCategory.PRIMARY) {
            announcePrimaryMission(player, m);
        } else {
            announceMinorMission(player, m);
        }
    }

    /**
     * Loud, hard-to-miss cue for PRIMARY story missions: a big on-screen title, a fanfare, and a
     * highlighted chat block. The plain single chat line was easy to lose in a busy chat, so we layer
     * three channels — this is the "letrero grande" reserved for the main quest.
     */
    private static void announcePrimaryMission(ServerPlayer player, Mission m) {
        // On-screen title overlay (fade in 10t, hold 70t ≈ 3.5s, fade out 20t) — the part nobody misses.
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("New Mission").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(m.title()).withStyle(ChatFormatting.YELLOW)));

        // Audible cue, only to this player.
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);

        // Highlighted chat block with a header so it stands out from regular chat.
        player.sendSystemMessage(Component.literal("=== NEW MISSION ===")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("» " + m.title())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (!m.description().isEmpty()) {
            player.sendSystemMessage(Component.literal("  " + m.description()).withStyle(ChatFormatting.GRAY));
        }
        for (var o : m.objectives()) {
            player.sendSystemMessage(Component.literal("  • " + o.describe()).withStyle(ChatFormatting.YELLOW));
        }
    }

    /**
     * Quiet, chat-only cue for daily and secondary missions: no screen banner, just a tagged chat block
     * and a soft ping so the player still notices. Color/tag follow the in-UI scheme (daily = green,
     * side quest = aqua).
     */
    private static void announceMinorMission(ServerPlayer player, Mission m) {
        ChatFormatting color = m.isDaily() ? ChatFormatting.GREEN : ChatFormatting.AQUA;
        String tag = m.isDaily() ? "Daily" : "Side Quest";

        // Soft, short ping — noticeable but clearly not the primary-mission fanfare.
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.5f);

        player.sendSystemMessage(Component.literal("[" + tag + "] New mission: ")
                .withStyle(color, ChatFormatting.BOLD)
                .append(Component.literal(m.title()).withStyle(color)));
        if (!m.description().isEmpty()) {
            player.sendSystemMessage(Component.literal("  " + m.description()).withStyle(ChatFormatting.GRAY));
        }
        for (var o : m.objectives()) {
            player.sendSystemMessage(Component.literal("  • " + o.describe()).withStyle(color));
        }
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
        autoAssignAvailable(player); // a finished mission may unlock the next auto-accept link in a chain
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

    /**
     * Force-assign every {@code autoAccept} mission whose prerequisites are now met (story/important missions
     * the player must not miss). Called on login, on the periodic tick, on dimension change, and right after a
     * completion (so a chain's next link pops automatically). Returns how many were assigned this pass.
     */
    public static int autoAssignAvailable(ServerPlayer player) {
        int assigned = 0;
        for (Mission m : available(player)) {
            if (m.autoAccept() && assign(player, m)) {
                assigned++;
            }
        }
        return assigned;
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
