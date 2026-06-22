package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.Constants;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Creates/removes a real JourneyMap waypoint (map marker + JourneyMap's own in-world beam + edge indicator)
 * for a reach_location objective. Only called when JourneyMap is loaded (guarded at the call site in
 * {@link ClientMissions}); the waypoint id is the mission id so it can be removed on completion. References
 * the journeymap.* API directly — never loaded on a server without JourneyMap.
 */
public final class JourneyMapBridge {

    private JourneyMapBridge() {}

    public static void sync(String missionId, String name, ResourceLocation dim, BlockPos pos, int rgb) {
        IClientAPI api = JourneyMapPlugin.api();
        if (api == null) {
            return;
        }
        try {
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dim);
            Waypoint existing = api.getWaypoint(Constants.MODID, missionId);
            if (existing != null) {
                api.remove(existing);
            }
            Waypoint wp = new Waypoint(Constants.MODID, missionId, name, dimKey, pos)
                    .setColor(rgb & 0xFFFFFF)
                    .setPersistent(false)
                    .setEditable(false);
            api.show(wp);
        } catch (Exception e) {
            Constants.LOG.warn("[custommissions] JourneyMap waypoint sync failed for {}: {}", missionId, e.toString());
        }
    }

    public static void remove(String missionId) {
        IClientAPI api = JourneyMapPlugin.api();
        if (api == null) {
            return;
        }
        try {
            Waypoint existing = api.getWaypoint(Constants.MODID, missionId);
            if (existing != null) {
                api.remove(existing);
            }
        } catch (Exception e) {
            Constants.LOG.warn("[custommissions] JourneyMap waypoint remove failed for {}: {}", missionId, e.toString());
        }
    }
}
