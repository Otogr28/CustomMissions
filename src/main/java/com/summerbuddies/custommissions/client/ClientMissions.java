package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.net.MarkerS2C;
import com.summerbuddies.custommissions.net.MissionSyncS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only holder of the data the server pushes: the active quest markers ({@link MarkerOverlay} reads
 * them) and the missions snapshot ({@link MissionsScreen} reads it). Reached only on the physical client
 * (via {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)} in the packet handlers).
 */
public final class ClientMissions {

    public record Marker(String missionId, ResourceLocation dimension, BlockPos pos, String name, int rgb) {}

    private static final Map<String, Marker> MARKERS = new ConcurrentHashMap<>();
    private static final MissionSyncS2C EMPTY = new MissionSyncS2C(List.of(), List.of(), List.of());

    private static volatile MissionSyncS2C snapshot = EMPTY;
    private static volatile String trackedId;
    private static boolean openRequested;

    private ClientMissions() {}

    /** The mission highlighted/expanded in the left HUD (chosen via the screen's Track button). */
    public static String trackedId() {
        return trackedId;
    }

    public static void setTracked(String id) {
        trackedId = id;
    }

    // ---- markers ---------------------------------------------------------------------------------

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
        snapshot = EMPTY;
        trackedId = null;
    }

    // ---- missions snapshot -----------------------------------------------------------------------

    public static MissionSyncS2C snapshot() {
        return snapshot;
    }

    /** Mark that the next snapshot should pop the screen open (set when the keybind is pressed). */
    public static void requestOpen() {
        openRequested = true;
    }

    /** Called from the packet handler on the client thread when a fresh snapshot arrives. */
    public static void applySnapshot(MissionSyncS2C s) {
        snapshot = s;
        Minecraft mc = Minecraft.getInstance();
        if (openRequested) {
            openRequested = false;
            mc.setScreen(new MissionsScreen());
        } else if (mc.screen instanceof MissionsScreen screen) {
            screen.refresh();
        }
    }
}
