package com.summerbuddies.custommissions.event;

import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.ai.DailyIngestTask;
import com.summerbuddies.custommissions.ai.MissionContextExporter;
import com.summerbuddies.custommissions.ai.WorldStateExporter;
import com.summerbuddies.custommissions.bridge.EasyNpcBridge;
import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.state.MissionTracker;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import com.summerbuddies.custommissions.state.PlayerMissions;
import com.summerbuddies.custommissions.waypoint.WaypointService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Translates raw Forge events into normalized {@link MissionEvent}s and feeds them to
 * {@link MissionEventBus}. Also wires player lifecycle: on login it expires stale dailies, ingests the
 * day's AI-authored dailies, and re-pushes waypoints; on logout it exports the player's AI context. A
 * periodic server tick runs the same daily housekeeping for already-online players.
 */
@Mod.EventBusSubscriber(modid = Constants.MODID)
public final class ForgeEventHooks {

    /** ~ every 30s: light daily housekeeping (expiry + ingest) for online players. */
    private static final int HOUSEKEEP_INTERVAL = 600;
    private static int tickCounter;

    private ForgeEventHooks() {}

    // ---- objective sources -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        ServerPlayer player = killer(event);
        if (player == null) {
            return;
        }
        LivingEntity dead = event.getEntity();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(dead.getType());
        if (id == null) {
            return;
        }
        Set<ResourceLocation> tags = dead.getType().builtInRegistryHolder().tags()
                .map(TagKey::location).collect(Collectors.toSet());
        MissionEventBus.dispatch(player, new MissionEvent.Kill(id, tags));
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem().getItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            MissionEventBus.dispatch(player, new MissionEvent.Pickup(id, stack.getCount()));
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Entity target = event.getTarget();
        if (EasyNpcBridge.isNpc(target)) {
            MissionEventBus.dispatch(player,
                    new MissionEvent.NpcTalk(EasyNpcBridge.npcUuid(target), EasyNpcBridge.npcName(target)));
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MissionEventBus.dispatch(player, new MissionEvent.EnterDim(event.getTo().location()));
        }
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MissionEventBus.dispatch(player, new MissionEvent.Advancement(event.getAdvancement().getId()));
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock());
        if (id != null) {
            MissionEventBus.dispatch(player, new MissionEvent.PlaceBlock(id));
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null) {
            MissionEventBus.dispatch(player, new MissionEvent.UseBlock(id));
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MissionTracker.expireDailies(player);
            DailyIngestTask.ingest(player);
            refreshWaypoints(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MissionContextExporter.export(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < HOUSEKEEP_INTERVAL) {
            return;
        }
        tickCounter = 0;
        WorldStateExporter.export(event.getServer());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            MissionTracker.expireDailies(player);
            DailyIngestTask.ingest(player);
            MissionContextExporter.export(player);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static ServerPlayer killer(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer sp) {
            return sp;
        }
        if (attacker instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    private static void refreshWaypoints(ServerPlayer player) {
        PlayerMissions pm = PlayerMissionStore.get(player);
        for (String id : pm.activeIds()) {
            Mission m = MissionManager.get(id);
            int[] counters = pm.counters(id);
            if (m != null && counters != null) {
                WaypointService.refresh(player, m, counters);
            }
        }
    }
}
