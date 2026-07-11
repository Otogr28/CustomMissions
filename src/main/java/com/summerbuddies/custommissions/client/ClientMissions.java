package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.net.MarkerS2C;
import com.summerbuddies.custommissions.net.MissionSyncS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only holder of the data the server pushes: quest markers ({@link MarkerOverlay} + JourneyMap read
 * them) and the missions snapshot ({@link MissionsScreen} + {@link MissionHudOverlay} read it). Also owns
 * the client-side <b>tracked set</b> — which missions appear in the left HUD. Newly-accepted missions are
 * auto-tracked; the screen's Track button toggles membership so a mission can be hidden from the HUD.
 * Reached only on the physical client (via {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)}).
 */
public final class ClientMissions {

    public record Marker(String missionId, ResourceLocation dimension, BlockPos pos, String name, int rgb) {}

    private static final Map<String, Marker> MARKERS = new ConcurrentHashMap<>();
    private static final MissionSyncS2C EMPTY = new MissionSyncS2C(List.of(), List.of(), List.of());

    /** Missions shown in the left HUD. */
    private static final Set<String> TRACKED = ConcurrentHashMap.newKeySet();
    /** Active mission ids we've already seen, so a mission is auto-tracked exactly once (on first accept). */
    private static final Set<String> KNOWN_ACTIVE = ConcurrentHashMap.newKeySet();

    private static volatile MissionSyncS2C snapshot = EMPTY;
    private static boolean openRequested;

    // Pending self-description prompt: held until the player is in-world and free, so it never fights the
    // loading screen. Opened by pollDescribe() from the client tick.
    private static volatile boolean describePending;
    private static volatile String describeText = "";
    private static volatile boolean describeFirstTime;

    private ClientMissions() {}

    // ---- self-description prompt ------------------------------------------------------------------

    /** Queue the self-description screen to open once the client is ready (called from the packet handler). */
    public static void requestDescribe(String text, boolean firstTime) {
        describeText = text == null ? "" : text;
        describeFirstTime = firstTime;
        describePending = true;
    }

    /** Open the queued self-description screen when the player is in-world with no other screen open. */
    public static void pollDescribe(Minecraft mc) {
        if (!describePending || mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        describePending = false;
        mc.setScreen(new DescribeScreen(describeText, describeFirstTime));
    }

    // ---- HUD tracking ----------------------------------------------------------------------------

    public static boolean isTracked(String missionId) {
        return TRACKED.contains(missionId);
    }

    /** Toggle whether a mission shows in the left HUD, the compass marker, AND the JourneyMap waypoint. */
    public static void toggleTracked(String missionId) {
        if (!TRACKED.remove(missionId)) {
            TRACKED.add(missionId);
        }
        syncMarkerJm(missionId);
    }

    /** True only for markers of tracked missions (compass overlay filters on this). */
    public static boolean markerVisible(String missionId) {
        return TRACKED.contains(missionId);
    }

    // ---- markers ---------------------------------------------------------------------------------

    public static void applyMarker(MarkerS2C p) {
        boolean jm = ModList.get().isLoaded("journeymap");
        if (!p.show()) {
            MARKERS.remove(p.missionId());
            if (jm) {
                JourneyMapBridge.remove(p.missionId());
            }
            return;
        }
        ResourceLocation dim = ResourceLocation.tryParse(p.dimension());
        if (dim == null) {
            return;
        }
        BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
        MARKERS.put(p.missionId(), new Marker(p.missionId(), dim, pos, p.name(), p.rgb()));
        if (jm) {
            if (isTracked(p.missionId())) {
                JourneyMapBridge.sync(p.missionId(), p.name(), dim, pos, p.rgb());
            } else {
                JourneyMapBridge.remove(p.missionId());
            }
        }
    }

    /** Show/hide a mission's JourneyMap waypoint to match its tracked state (called on toggle). */
    private static void syncMarkerJm(String missionId) {
        if (!ModList.get().isLoaded("journeymap")) {
            return;
        }
        Marker m = MARKERS.get(missionId);
        if (m != null && isTracked(missionId)) {
            JourneyMapBridge.sync(missionId, m.name(), m.dimension(), m.pos(), m.rgb());
        } else {
            JourneyMapBridge.remove(missionId);
        }
    }

    /** Reconcile every marker's JourneyMap waypoint with its tracked state (after a snapshot/auto-track). */
    private static void syncAllMarkersJm() {
        if (!ModList.get().isLoaded("journeymap")) {
            return;
        }
        for (Marker m : MARKERS.values()) {
            if (isTracked(m.missionId())) {
                JourneyMapBridge.sync(m.missionId(), m.name(), m.dimension(), m.pos(), m.rgb());
            } else {
                JourneyMapBridge.remove(m.missionId());
            }
        }
    }

    public static Collection<Marker> markers() {
        return MARKERS.values();
    }

    public static void clear() {
        MARKERS.clear();
        snapshot = EMPTY;
        TRACKED.clear();
        KNOWN_ACTIVE.clear();
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
        autoTrack(s);
        syncAllMarkersJm();
        Minecraft mc = Minecraft.getInstance();
        if (openRequested) {
            openRequested = false;
            mc.setScreen(new MissionsScreen());
        } else if (mc.screen instanceof MissionsScreen screen) {
            screen.refresh();
        }
    }

    /** Auto-track each mission the first time it becomes active; forget tracking once it's no longer active. */
    private static void autoTrack(MissionSyncS2C s) {
        Set<String> active = new HashSet<>();
        for (MissionSyncS2C.Entry e : s.active()) {
            active.add(e.id());
        }
        for (String id : active) {
            if (KNOWN_ACTIVE.add(id)) {
                TRACKED.add(id);
            }
        }
        KNOWN_ACTIVE.retainAll(active);
        TRACKED.retainAll(active);
    }
}
