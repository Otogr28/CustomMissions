package com.summerbuddies.custommissions.event;

import com.summerbuddies.custommissions.Constants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Samples each player's position a few times a second and emits a {@link MissionEvent.Move}, which is how
 * {@code reach_location} objectives complete. Throttled per player ({@link #SCAN_INTERVAL} ticks); the
 * dispatch early-returns for players with no active missions, so the cost is negligible.
 */
@Mod.EventBusSubscriber(modid = Constants.MODID)
public final class LocationScanner {

    private static final int SCAN_INTERVAL = 5;

    private LocationScanner() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }
        MissionEventBus.dispatch(player,
                new MissionEvent.Move(player.serverLevel().dimension().location(), player.blockPosition()));
    }
}
