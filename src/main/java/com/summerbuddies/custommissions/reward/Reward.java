package com.summerbuddies.custommissions.reward;

import net.minecraft.server.level.ServerPlayer;

/**
 * Something granted to a player — on mission accept, or on complete. Implementations are immutable
 * records in {@link Rewards}; cross-mod rewards run through {@code bridge.*} (guarded by ModList) so the
 * mod degrades gracefully when an integration mod is absent.
 */
public interface Reward {

    RewardType type();

    void grant(ServerPlayer player, RewardContext ctx);

    /** Short human-readable line for mission listings. */
    String describe();
}
