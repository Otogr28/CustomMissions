package com.summerbuddies.custommissions.intro;

import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.bridge.StoryKitBridge;
import com.summerbuddies.custommissions.reward.RewardContext;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import com.summerbuddies.custommissions.state.PlayerMissions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fires the brand-new-player intro cutscene the first time a player closes the "describe your character"
 * screen, exactly once. The cutscene itself lives in StoryKit (the {@code epilogue} sequence); this just
 * runs {@code /story play epilogue <player>} as the server. The one-shot is the {@code introCinematicPlayed}
 * flag on {@link PlayerMissions} (per-player NBT, survives logout/restart).
 *
 * <p>Why here, on describe-close, and not on a login timer: the describe screen only opens for a player with
 * no description yet (a new account), and only once they close it has the world finished loading and the
 * player engaged. So it is both the right moment and a natural "new player" gate, with no guessing at load
 * time. Established players are pre-marked on login ({@link #markSeenIfEstablished}) so re-editing their
 * description never replays it.
 */
public final class IntroCutscene {

    /** StoryKit sequence id played as the intro. */
    public static final String SEQUENCE = "epilogue";

    private IntroCutscene() {}

    /**
     * Call when the describe screen closes (submit OR Esc/Later). Plays the intro once, for an online,
     * not-yet-shown player; a player who disconnects mid-close is left unmarked and retries next time.
     */
    public static void onDescribeClosed(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null || server.getPlayerList().getPlayer(player.getUUID()) == null) {
            return; // disconnecting / offline — don't consume the one-shot
        }
        if (PlayerMissionStore.get(player).introCinematicPlayed()) {
            return;
        }
        PlayerMissionStore.update(player, p -> p.setIntroCinematicPlayed(true));
        StoryKitBridge.playCutscene(new RewardContext(server), player, SEQUENCE);
        Constants.LOG.info("[custommissions] intro cutscene '{}' fired for {}", SEQUENCE,
                player.getGameProfile().getName());
    }

    /**
     * Mark a player who already has a description as having seen the intro, so editing it later never
     * re-triggers the cutscene. Idempotent; called on login for established players.
     */
    public static void markSeenIfEstablished(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerMissions pm = PlayerMissionStore.get(player);
        if (pm.hasDescription() && !pm.introCinematicPlayed()) {
            PlayerMissionStore.update(player, p -> p.setIntroCinematicPlayed(true));
        }
    }
}
