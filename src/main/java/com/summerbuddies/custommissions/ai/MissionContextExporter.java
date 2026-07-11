package com.summerbuddies.custommissions.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.state.GossipLedger;
import com.summerbuddies.custommissions.state.LoreState;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import com.summerbuddies.custommissions.state.PlayerMissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Writes the per-player CONTEXT file the AI brain reads to author tailored dailies:
 * {@code config/custommissions/state/context/<uuid>.json}. Captures identity, the server lore stage, the
 * player's dimension/position, their active and completed missions, and a snapshot of key vanilla stats.
 * Refreshed on logout and periodically while online (see {@code event.ForgeEventHooks}).
 */
public final class MissionContextExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MissionContextExporter() {}

    public static void export(ServerPlayer player) {
        try {
            Path dir = MissionManager.baseDir().resolve("state").resolve("context");
            Files.createDirectories(dir);
            Path file = dir.resolve(player.getUUID() + ".json");
            Files.writeString(file, GSON.toJson(build(player)));
        } catch (IOException e) {
            Constants.LOG.warn("[custommissions] could not export context for {}: {}",
                    player.getGameProfile().getName(), e.toString());
        }
    }

    private static JsonObject build(ServerPlayer player) {
        LoreState ls = LoreState.get(player.serverLevel());
        PlayerMissions pm = PlayerMissionStore.get(player);

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        JsonObject p = new JsonObject();
        p.addProperty("uuid", player.getUUID().toString());
        p.addProperty("name", player.getGameProfile().getName());
        if (pm.hasDescription()) {
            p.addProperty("description", pm.description());
        }
        GossipLedger gl = GossipLedger.get(player.serverLevel());
        p.addProperty("gossipBias", gl.bias(player.getUUID()));
        java.util.List<String> rumors = gl.rumors(player.getUUID());
        if (!rumors.isEmpty()) {
            JsonArray ra = new JsonArray();
            for (String s : rumors) ra.add(s);
            p.add("rumors", ra);
        }
        root.add("player", p);

        root.addProperty("generatedAt", Instant.now().toString());
        root.addProperty("loreStage", ls.stage());
        root.addProperty("loreChapter", ls.chapter());
        root.addProperty("currentDimension", player.serverLevel().dimension().location().toString());

        JsonArray pos = new JsonArray();
        pos.add(player.blockPosition().getX());
        pos.add(player.blockPosition().getY());
        pos.add(player.blockPosition().getZ());
        root.add("lastSeenPos", pos);

        root.add("activeMissions", toArray(pm.activeIds()));
        root.add("completedMissions", toArray(pm.completedIds()));

        JsonObject stats = new JsonObject();
        stats.addProperty("play_time", stat(player, Stats.PLAY_TIME));
        stats.addProperty("mob_kills", stat(player, Stats.MOB_KILLS));
        stats.addProperty("player_kills", stat(player, Stats.PLAYER_KILLS));
        stats.addProperty("deaths", stat(player, Stats.DEATHS));
        stats.addProperty("walk_one_cm", stat(player, Stats.WALK_ONE_CM));
        stats.addProperty("damage_dealt", stat(player, Stats.DAMAGE_DEALT));
        stats.addProperty("damage_taken", stat(player, Stats.DAMAGE_TAKEN));
        root.add("stats", stats);

        return root;
    }

    private static int stat(ServerPlayer player, net.minecraft.resources.ResourceLocation custom) {
        return player.getStats().getValue(Stats.CUSTOM.get(custom));
    }

    private static JsonArray toArray(Iterable<String> ids) {
        JsonArray a = new JsonArray();
        for (String s : ids) a.add(s);
        return a;
    }
}
