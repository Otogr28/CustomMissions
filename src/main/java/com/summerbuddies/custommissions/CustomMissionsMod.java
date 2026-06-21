package com.summerbuddies.custommissions;

import com.summerbuddies.custommissions.net.MissionNet;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Entry point of CustomMissions — an AI-driven mission system.
 *
 * <p>The mod owns five decoupled subsystems, all keyed off plain JSON in {@code config/custommissions/}:
 * (A) a hot-reloadable <b>mission catalog</b> ({@code mission.MissionManager}: authored chains in
 * {@code missions/} plus AI-authored dailies in {@code daily/}); (B) an <b>objective engine</b>
 * ({@code event.*}) that funnels Forge events and custom {@code /mission signal}s into per-player
 * progress; (C) <b>state</b> ({@code state.*}: per-player progress in persistent NBT, a server-wide
 * lore stage as SavedData); (D) <b>rewards + bridges</b> ({@code reward.*}, {@code bridge.*}) including
 * cross-mod hooks (StoryKit cutscenes, Realm Gates unlocks, Companions) guarded by {@code ModList};
 * and (E) <b>waypoints</b> ({@code waypoint.*}) drawing an on-screen quest marker (and an optional
 * JourneyMap waypoint) for {@code reach_location} objectives.
 *
 * <p>Forge-bus handlers register themselves via {@code @Mod.EventBusSubscriber} in their own classes.
 * The only thing wired here is the network channel, which must exist on both sides.
 */
@Mod(Constants.MODID)
public class CustomMissionsMod {

    public CustomMissionsMod(FMLJavaModLoadingContext context) {
        MissionNet.register();
        Constants.LOG.info("[custommissions] constructed");
    }
}
