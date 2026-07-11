package com.summerbuddies.custommissions.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.summerbuddies.custommissions.Constants;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-only display preferences for CustomMissions, persisted to {@code config/custommissions-client.json}.
 * Currently the on-screen mission HUD (left tracker) scale, adjustable from the M screen's drag bar. Loaded
 * lazily on first read; saved when the player lets go of the slider.
 */
public final class MissionClientConfig {

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.0f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static float hudScale = 1.0f;
    private static boolean loaded;

    private MissionClientConfig() {}

    public static float hudScale() {
        ensureLoaded();
        return hudScale;
    }

    public static void setHudScale(float scale) {
        ensureLoaded();
        hudScale = clamp(scale);
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("hudScale", hudScale);
            Files.writeString(file(), GSON.toJson(o));
        } catch (Exception e) {
            Constants.LOG.warn("[custommissions] could not save client config: {}", e.toString());
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path f = file();
            if (Files.exists(f)) {
                JsonObject o = GSON.fromJson(Files.readString(f), JsonObject.class);
                if (o != null && o.has("hudScale")) {
                    hudScale = clamp(o.get("hudScale").getAsFloat());
                }
            }
        } catch (Exception e) {
            Constants.LOG.warn("[custommissions] could not load client config: {}", e.toString());
        }
    }

    private static float clamp(float s) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("custommissions-client.json");
    }
}
