package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.net.MarkerS2C;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only holder of the active quest markers pushed by the server (one per mission with a live
 * reach_location objective). {@link MarkerOverlay} reads these every frame to draw the on-screen guidance.
 * Reached only on the physical client (via {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)}).
 */
public final class ClientMissions {

    public record Marker(String missionId, ResourceLocation dimension, BlockPos pos, String name, int rgb) {}

    private static final Map<String, Marker> MARKERS = new ConcurrentHashMap<>();

    private ClientMissions() {}

    public static void applyMarker(MarkerS2C p) {
        if (!p.show()) {
            MARKERS.remove(p.missionId());
            return;
        }
        ResourceLocation dim = ResourceLocation.tryParse(p.dimension());
        if (dim == null) {
            return;
        }
        MARKERS.put(p.missionId(), new Marker(p.missionId(), dim, new BlockPos(p.x(), p.y(), p.z()), p.name(), p.rgb()));
    }

    public static Collection<Marker> markers() {
        return MARKERS.values();
    }

    public static void clear() {
        MARKERS.clear();
    }
}
