package com.summerbuddies.custommissions.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.net.MissionNet;
import com.summerbuddies.custommissions.net.TravelerReplyS2C;
import com.summerbuddies.custommissions.state.GossipLedger;
import com.summerbuddies.custommissions.state.LoreState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-game brain bridge for <b>Flugel the Traveler</b> — chat + the market gossip system — driven by an
 * off-process Claude (Sonnet) through a FILE CONTRACT (the Minecraft server runs in Docker without the
 * {@code claude} binary). The mod drops a request JSON at {@code config/custommissions/chat/requests/};
 * the host sidecar ({@code agent/traveler-chat.sh}) answers into {@code chat/responses/}; {@link #poll}
 * picks the answer up. Three request modes: {@code direct} (1:1 chat, limited 2/10min), {@code ambient}
 * (a public-chat mention, broadcast), {@code gossip} (judge a tip's credibility → reputation bias).
 *
 * <p>Everything runs on the server thread (C2S handlers + server tick), so the plain maps need no sync.
 */
public final class TravelerChat {

    /** One exchange line. {@code role} is {@code "user"} or {@code "traveler"}. */
    public record Turn(String role, String text) {}

    private enum Kind { DIRECT, AMBIENT, GOSSIP }

    private static final int MAX_TURNS = 16;             // direct-chat history kept per player
    private static final int RECENT_MAX = 8;             // rolling public-chat buffer
    private static final int AMBIENT_CONTEXT = 5;        // chat lines handed to the brain on a mention
    private static final long TIMEOUT_MS = 90_000;       // give up on a request after this long
    private static final long MENTION_COOLDOWN_MS = 8_000;  // min gap between chat-mention replies
    private static final String[] KEYWORDS = {"flugel", "traveler"}; // public-chat words that wake Flugel
    private static final int MAX_PERSONAL = 2;            // messages per window in the 1:1 Flugel chat
    private static final long PERSONAL_WINDOW_MS = 600_000; // 10 minutes
    private static final int GOSSIP_MAX_STEP = 20;       // |bias delta| cap from a single tip

    private static final Gson GSON = new GsonBuilder().create();
    private static final AtomicLong SEQ = new AtomicLong();

    private static final Map<UUID, Deque<Turn>> HISTORY = new HashMap<>();
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    private static final Deque<String> RECENT_CHAT = new ArrayDeque<>();
    private static final Map<UUID, Deque<Long>> PERSONAL_TIMES = new HashMap<>();
    private static long lastMentionMs;

    /** A request waiting on the brain. For GOSSIP, {@code targetUuid} is who the rumor is about. */
    private record Pending(UUID player, Kind kind, String targetUuid, long submittedMs) {}

    private TravelerChat() {}

    // ---- direct chat (Flugel "talk more personally", limited 2 / 10 min) -------------------------

    public static void ask(ServerPlayer player, String message) {
        String msg = message == null ? "" : message.strip();
        if (msg.isEmpty()) {
            return;
        }
        if (!consumePersonalQuota(player.getUUID())) {
            reply(player, "Flugel has said all they will for now. Return in a little while.", true);
            return;
        }
        Deque<Turn> hist = HISTORY.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        try {
            Path dir = chatDir().resolve("requests");
            Files.createDirectories(dir);
            String reqId = player.getUUID() + "-" + SEQ.incrementAndGet();
            Files.writeString(dir.resolve(reqId + ".json"), GSON.toJson(buildRequest(player, msg, hist)));
            push(hist, new Turn("user", msg));
            PENDING.put(reqId, new Pending(player.getUUID(), Kind.DIRECT, null, System.currentTimeMillis()));
        } catch (IOException e) {
            Constants.LOG.warn("[custommissions] flugel chat request failed: {}", e.toString());
            reply(player, "Flugel's voice fades — the link is down. Try again later.", true);
        }
    }

    private static boolean consumePersonalQuota(UUID player) {
        Deque<Long> times = PERSONAL_TIMES.computeIfAbsent(player, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        while (!times.isEmpty() && now - times.peekFirst() > PERSONAL_WINDOW_MS) {
            times.removeFirst();
        }
        if (times.size() >= MAX_PERSONAL) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    // ---- public-chat listener (broadcast) --------------------------------------------------------

    public static void onPublicChat(ServerPlayer player, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        RECENT_CHAT.addLast(player.getGameProfile().getName() + ": " + text);
        while (RECENT_CHAT.size() > RECENT_MAX) {
            RECENT_CHAT.removeFirst();
        }
        if (!mentionsFlugel(text)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastMentionMs < MENTION_COOLDOWN_MS) {
            return; // someone just woke him; don't spam
        }
        lastMentionMs = now;
        try {
            Path dir = chatDir().resolve("requests");
            Files.createDirectories(dir);
            String reqId = "chat-" + SEQ.incrementAndGet();
            Files.writeString(dir.resolve(reqId + ".json"), GSON.toJson(buildAmbientRequest(player, text)));
            PENDING.put(reqId, new Pending(player.getUUID(), Kind.AMBIENT, null, now));
        } catch (IOException e) {
            Constants.LOG.warn("[custommissions] flugel chat-listener request failed: {}", e.toString());
        }
    }

    private static boolean mentionsFlugel(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String k : KEYWORDS) {
            if (low.contains(k)) {
                return true;
            }
        }
        return false;
    }

    // ---- gossip ----------------------------------------------------------------------------------

    /** A player told the market gossip something about {@code target}; ask Sonnet to judge its credibility. */
    public static void gossip(ServerPlayer gossiper, String targetUuid, String targetName, String text) {
        String msg = text == null ? "" : text.strip();
        if (msg.isEmpty() || targetUuid == null || targetUuid.isBlank()) {
            return;
        }
        if (msg.length() > 200) {
            msg = msg.substring(0, 200);
        }
        try {
            Path dir = chatDir().resolve("requests");
            Files.createDirectories(dir);
            String reqId = "gossip-" + SEQ.incrementAndGet();
            Files.writeString(dir.resolve(reqId + ".json"),
                    GSON.toJson(buildGossipRequest(gossiper, targetUuid, targetName, msg)));
            PENDING.put(reqId, new Pending(gossiper.getUUID(), Kind.GOSSIP, targetUuid, System.currentTimeMillis()));
        } catch (IOException e) {
            Constants.LOG.warn("[custommissions] gossip request failed: {}", e.toString());
            gossiper.sendSystemMessage(Component.literal("The market is too noisy right now. Try again later.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- poll ------------------------------------------------------------------------------------

    public static void poll(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Pending> entry = it.next();
            String reqId = entry.getKey();
            Pending p = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(p.player());
            Path respFile = chatDir().resolve("responses").resolve(reqId + ".json");
            try {
                if (Files.exists(respFile)) {
                    String raw = Files.readString(respFile);
                    cleanup(reqId);
                    it.remove();
                    switch (p.kind()) {
                        case GOSSIP -> applyGossip(server, p, player, raw);
                        case AMBIENT -> server.getPlayerList()
                                .broadcastSystemMessage(travelerLine(replyText(raw)), false);
                        case DIRECT -> {
                            if (player != null) {
                                push(HISTORY.computeIfAbsent(p.player(), k -> new ArrayDeque<>()),
                                        new Turn("traveler", replyText(raw)));
                                reply(player, replyText(raw), false);
                            }
                        }
                    }
                } else if (now - p.submittedMs() > TIMEOUT_MS) {
                    cleanup(reqId);
                    it.remove();
                    if (p.kind() == Kind.DIRECT && player != null) {
                        reply(player, "Flugel is lost in thought, and gives no answer. (timeout)", true);
                    } else if (p.kind() == Kind.GOSSIP && player != null) {
                        player.sendSystemMessage(Component.literal("Your tip goes unheard for now…")
                                .withStyle(ChatFormatting.GRAY));
                    }
                    // AMBIENT timeouts stay silent — never spam public chat with errors.
                }
            } catch (IOException ex) {
                Constants.LOG.warn("[custommissions] chat poll error: {}", ex.toString());
                it.remove();
            }
        }
    }

    /** Parse the gossip verdict {@code {credibility,sentiment,delta,note}} and nudge reputation. */
    private static void applyGossip(MinecraftServer server, Pending p, ServerPlayer gossiper, String raw) {
        int delta = 0;
        String note = "";
        try {
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            boolean error = o.has("error") && o.get("error").getAsBoolean();
            double credibility = o.has("credibility") ? clamp(o.get("credibility").getAsDouble(), 0, 1) : 0;
            double sentiment = o.has("sentiment") ? clamp(o.get("sentiment").getAsDouble(), -1, 1) : 0;
            delta = o.has("delta") ? o.get("delta").getAsInt()
                    : (int) Math.round(credibility * sentiment * GOSSIP_MAX_STEP);
            delta = (int) clamp(delta, -GOSSIP_MAX_STEP, GOSSIP_MAX_STEP);
            note = o.has("note") ? o.get("note").getAsString().strip() : "";
            if (error) {
                delta = 0;
            }
        } catch (RuntimeException ex) {
            Constants.LOG.warn("[custommissions] gossip parse failed: {}", ex.toString());
        }

        GossipLedger ledger = GossipLedger.get(server.overworld());
        UUID targetId = parseUuid(p.targetUuid());
        if (targetId != null && delta != 0) {
            ledger.addBias(targetId, delta);
            if (Math.abs(delta) >= 4 && !note.isBlank()) {
                ledger.addRumor(targetId, note);
            }
        }
        // The rumor-monger's own reputation drifts a little in the same direction.
        int gDelta = (int) Math.round(delta / 4.0);
        if (gDelta != 0) {
            ledger.addBias(p.player(), gDelta);
        }
        if (gossiper != null) {
            String msg = Math.abs(delta) >= 4
                    ? "Hmm… that has the ring of truth. Word will spread."
                    : "The market hears you, but talk that cheap goes nowhere.";
            gossiper.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD));
        }
    }

    public static void forget(UUID player) {
        HISTORY.remove(player);
    }

    // ---- request builders ------------------------------------------------------------------------

    private static JsonObject buildRequest(ServerPlayer player, String msg, Deque<Turn> hist) {
        JsonObject root = baseRequest("direct", player);
        root.addProperty("message", msg);
        addReputation(root, player);
        JsonArray h = new JsonArray();
        for (Turn t : hist) {
            JsonObject o = new JsonObject();
            o.addProperty("role", t.role());
            o.addProperty("text", t.text());
            h.add(o);
        }
        root.add("history", h);
        return root;
    }

    private static JsonObject buildAmbientRequest(ServerPlayer player, String trigger) {
        JsonObject root = baseRequest("ambient", player);
        root.addProperty("message", trigger);
        addReputation(root, player);
        JsonArray chat = new JsonArray();
        int skip = Math.max(0, RECENT_CHAT.size() - AMBIENT_CONTEXT);
        int i = 0;
        for (String line : RECENT_CHAT) {
            if (i++ >= skip) {
                chat.add(line);
            }
        }
        root.add("recentChat", chat);
        return root;
    }

    private static JsonObject buildGossipRequest(ServerPlayer gossiper, String targetUuid, String targetName,
                                                 String text) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("mode", "gossip");
        JsonObject g = new JsonObject();
        g.addProperty("uuid", gossiper.getUUID().toString());
        g.addProperty("name", gossiper.getGameProfile().getName());
        root.add("gossiper", g);
        JsonObject t = new JsonObject();
        t.addProperty("uuid", targetUuid);
        t.addProperty("name", targetName);
        t.addProperty("self", gossiper.getUUID().toString().equals(targetUuid));
        root.add("target", t);
        root.addProperty("text", text);
        LoreState ls = LoreState.get(gossiper.serverLevel());
        root.addProperty("loreStage", ls.stage());
        root.addProperty("loreChapter", ls.chapter());
        return root;
    }

    private static JsonObject baseRequest(String mode, ServerPlayer player) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("mode", mode);
        JsonObject p = new JsonObject();
        p.addProperty("uuid", player.getUUID().toString());
        p.addProperty("name", player.getGameProfile().getName());
        root.add("player", p);
        LoreState ls = LoreState.get(player.serverLevel());
        root.addProperty("loreStage", ls.stage());
        root.addProperty("loreChapter", ls.chapter());
        root.addProperty("dimension", player.serverLevel().dimension().location().toString());
        return root;
    }

    /** Fold the player's gossip reputation into a request so Flugel's tone reflects what he's heard. */
    private static void addReputation(JsonObject root, ServerPlayer player) {
        GossipLedger ledger = GossipLedger.get(player.serverLevel());
        root.addProperty("gossipBias", ledger.bias(player.getUUID()));
        JsonArray r = new JsonArray();
        for (String s : ledger.rumors(player.getUUID())) {
            r.add(s);
        }
        root.add("rumors", r);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private static Component travelerLine(String text) {
        return Component.literal("✦ Flugel the Traveler ✦ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
    }

    private static String replyText(String raw) {
        try {
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            boolean error = o.has("error") && o.get("error").getAsBoolean();
            String text = o.has("reply") ? o.get("reply").getAsString() : "";
            if (error || text.isBlank()) {
                return "Flugel frowns, unable to find the words right now.";
            }
            return text.strip();
        } catch (RuntimeException ex) {
            String t = raw.strip();
            return t.isEmpty() ? "Flugel says nothing." : t;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void cleanup(String reqId) {
        try {
            Files.deleteIfExists(chatDir().resolve("requests").resolve(reqId + ".json"));
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(chatDir().resolve("responses").resolve(reqId + ".json"));
        } catch (IOException ignored) {
        }
    }

    private static void reply(ServerPlayer player, String text, boolean error) {
        MissionNet.toPlayer(player, new TravelerReplyS2C(text, error));
    }

    private static void push(Deque<Turn> hist, Turn t) {
        hist.addLast(t);
        while (hist.size() > MAX_TURNS) {
            hist.removeFirst();
        }
    }

    private static Path chatDir() {
        return MissionManager.baseDir().resolve("chat");
    }
}
