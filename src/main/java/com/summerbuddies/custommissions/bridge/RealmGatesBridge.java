package com.summerbuddies.custommissions.bridge;

import com.summerbuddies.custommissions.reward.RewardContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Opens a Realm Gates dimension for the player ({@code /realmgates unlock <player> <dim>}). No-op if absent. */
public final class RealmGatesBridge {

    private RealmGatesBridge() {}

    public static boolean loaded() {
        return ModList.get().isLoaded("realmgates");
    }

    public static void unlockGate(RewardContext ctx, ServerPlayer player, ResourceLocation gate) {
        if (!loaded() || gate == null) {
            return;
        }
        ctx.runServer("realmgates unlock " + player.getGameProfile().getName() + " " + gate);
    }
}
