package com.summerbuddies.custommissions.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.mission.Mission;
import com.summerbuddies.custommissions.mission.MissionManager;
import com.summerbuddies.custommissions.state.LoreState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Writes the server WORLD_STATE file the AI brain reads: {@code config/custommissions/state/world_state.json}.
 * Captures the lore chapter/stage, narrative flags, unlocked gates, the dimensions that exist, the known
 * mission givers (so the AI can attribute dailies to an NPC), and the current online roster.
 */
public final class WorldStateExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WorldStateExporter() {}

    public static void export(MinecraftServer server) {
        try {
            Path dir = MissionManager.baseDir().resolve("state");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("world_state.json"), GSON.toJson(build(server)));
        } catch (IOException e) {
            Constants.LOG.warn("[custommissions] could not export world_state: {}", e.toString());
        }
    }

    private static JsonObject build(MinecraftServer server) {
        LoreState ls = LoreState.get(server.overworld());

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("generatedAt", Instant.now().toString());
        root.addProperty("loreChapter", ls.chapter());
        root.addProperty("loreStage", ls.stage());

        JsonArray flags = new JsonArray();
        ls.flags().forEach(flags::add);
        root.add("flags", flags);

        JsonArray gates = new JsonArray();
        ls.unlockedGates().forEach(g -> gates.add(g.toString()));
        root.add("unlockedGates", gates);

        JsonArray dims = new JsonArray();
        for (ResourceKey<Level> key : server.levelKeys()) {
            dims.add(key.location().toString());
        }
        root.add("knownDimensions", dims);

        JsonArray givers = new JsonArray();
        Set<String> seen = new LinkedHashSet<>();
        for (Mission m : MissionManager.all()) {
            if (!m.giver().isPresent()) {
                continue;
            }
            String key = m.giver().describe();
            if (seen.add(key)) {
                JsonObject g = new JsonObject();
                if (m.giver().npcName() != null) g.addProperty("name", m.giver().npcName());
                if (m.giver().npcUuid() != null) g.addProperty("uuid", m.giver().npcUuid().toString());
                givers.add(g);
            }
        }
        root.add("givers", givers);

        JsonArray roster = new JsonArray();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            roster.add(p.getGameProfile().getName());
        }
        root.add("onlineRoster", roster);

        return root;
    }
}
