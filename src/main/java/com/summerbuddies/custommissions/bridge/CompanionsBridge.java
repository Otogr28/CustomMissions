package com.summerbuddies.custommissions.bridge;

import com.summerbuddies.custommissions.reward.RewardContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Grants a Custom Companions pictograph (companion) as a reward ({@code /companion picto grant <id>}, run
 * as the player). No-op if Custom Companions is absent. The exact grant verb may need adjusting if the
 * companion mod's command surface changes.
 */
public final class CompanionsBridge {

    private CompanionsBridge() {}

    public static boolean loaded() {
        return ModList.get().isLoaded("customcompanions");
    }

    public static void grantCompanion(RewardContext ctx, ServerPlayer player, String id) {
        if (!loaded() || id == null || id.isBlank()) {
            return;
        }
        ctx.runAsPlayer(player, "companion picto grant " + id);
    }
}
