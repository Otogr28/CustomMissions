package com.summerbuddies.custommissions.client;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only state for the Traveler chat: the session transcript, whether we're waiting on the brain, and a
 * deferred open request (so an {@code OpenTravelerChatS2C} that arrives during the loading screen still pops
 * the screen once the player is in-world). Reached only on the physical client via {@code DistExecutor}.
 */
public final class TravelerClient {

    /** One rendered line. {@code fromPlayer} false = the Traveler (or a system/error line). */
    public record Line(boolean fromPlayer, String text) {}

    private static final List<Line> TRANSCRIPT = new ArrayList<>();
    private static volatile boolean awaiting;
    private static volatile boolean openPending;
    private static volatile String pendingGreeting = "";

    private TravelerClient() {}

    public static void requestOpen(String greeting) {
        pendingGreeting = greeting == null ? "" : greeting;
        openPending = true;
    }

    /** Open the queued chat screen once the player is in-world with no other screen (called each client tick). */
    public static void pollOpen(Minecraft mc) {
        if (!openPending || mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        openPending = false;
        if (TRANSCRIPT.isEmpty() && !pendingGreeting.isBlank()) {
            TRANSCRIPT.add(new Line(false, pendingGreeting));
        }
        mc.setScreen(new TravelerChatScreen());
    }

    public static List<Line> transcript() {
        return TRANSCRIPT;
    }

    public static boolean awaiting() {
        return awaiting;
    }

    /** Record the player's outgoing message and mark that we're waiting for the Traveler. */
    public static void addPlayerMessage(String text) {
        TRANSCRIPT.add(new Line(true, text));
        awaiting = true;
    }

    /** The Traveler's answer arrived (called from the packet handler). */
    public static void onReply(String reply, boolean error) {
        TRANSCRIPT.add(new Line(false, reply));
        awaiting = false;
    }

    public static void clear() {
        TRANSCRIPT.clear();
        awaiting = false;
    }
}
