package com.summerbuddies.custommissions.bridge;

import com.summerbuddies.custommissions.reward.RewardContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Fires a StoryKit cutscene as a reward ({@code /story play <script> <player>}). No-op if StoryKit absent. */
public final class StoryKitBridge {

    private StoryKitBridge() {}

    public static boolean loaded() {
        return ModList.get().isLoaded("storykit");
    }

    public static void playCutscene(RewardContext ctx, ServerPlayer player, String script) {
        if (!loaded() || script == null || script.isBlank()) {
            return;
        }
        ctx.runServer("story play " + script + " " + player.getGameProfile().getName());
    }
}
