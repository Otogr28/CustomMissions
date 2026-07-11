package com.summerbuddies.custommissions.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.summerbuddies.custommissions.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-side, runtime fetcher for a player's skin <b>by Minecraft account name</b> via Mojang's public APIs
 * — the same source CustomSkinLoader uses for premium nicks, so the gossip picker shows the same face. The
 * PNG is registered as a {@link DynamicTexture} and cached; only names are ever passed around, never images.
 * Returns {@code null} until the async fetch lands (callers fall back to the default skin). Adapted from
 * RealmNPC's {@code SkinFetcher} / CustomCompanions' {@code MojangSkinManager}.
 */
public final class GuiSkinCache {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String NAME_TO_UUID = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String UUID_TO_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final ConcurrentMap<String, ResourceLocation> resolved = new ConcurrentHashMap<>();
    private static final Set<String> failed = ConcurrentHashMap.newKeySet();
    private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    private GuiSkinCache() {}

    /** @return the live skin of a Minecraft account, or {@code null} until the async fetch lands. */
    @Nullable
    public static ResourceLocation byUsername(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        ResourceLocation rl = resolved.get(key);
        if (rl != null) {
            return rl;
        }
        if (failed.contains(key)) {
            return null;
        }
        if (inFlight.add(key)) {
            CompletableFuture.runAsync(() -> fetch(username, key));
        }
        return null;
    }

    private static void fetch(String username, String key) {
        try {
            String uuid = nameToUuid(username);
            if (uuid == null) {
                throw new IllegalStateException("no Mojang account named '" + username + "'");
            }
            String skinUrl = profileSkinUrl(uuid);
            if (skinUrl == null) {
                throw new IllegalStateException("account '" + username + "' has no skin");
            }
            byte[] png = get(skinUrl.replace("http://", "https://")).body();
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Minecraft.getInstance().execute(() -> {
                try {
                    ResourceLocation rl = new ResourceLocation(Constants.MODID,
                            "gossip_skin/" + key.replaceAll("[^a-z0-9_./-]", "_"));
                    Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(image));
                    resolved.put(key, rl);
                } catch (Exception e) {
                    image.close();
                    failed.add(key);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (Exception e) {
            failed.add(key);
            inFlight.remove(key);
            Constants.LOG.warn("[custommissions] could not fetch skin for '{}': {}", username, e.getMessage());
        }
    }

    @Nullable
    private static String nameToUuid(String username) throws Exception {
        HttpResponse<byte[]> res = get(NAME_TO_UUID + username);
        if (res.statusCode() != 200) {
            return null;
        }
        JsonObject o = JsonParser.parseString(new String(res.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        return o.has("id") ? o.get("id").getAsString() : null;
    }

    @Nullable
    private static String profileSkinUrl(String uuid) throws Exception {
        HttpResponse<byte[]> res = get(UUID_TO_PROFILE + uuid);
        if (res.statusCode() != 200) {
            return null;
        }
        JsonObject profile = JsonParser.parseString(new String(res.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        for (var prop : profile.getAsJsonArray("properties")) {
            JsonObject p = prop.getAsJsonObject();
            if ("textures".equals(p.get("name").getAsString())) {
                String json = new String(Base64.getDecoder().decode(p.get("value").getAsString()),
                        StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                if (textures.has("SKIN")) {
                    return textures.getAsJsonObject("SKIN").get("url").getAsString();
                }
            }
        }
        return null;
    }

    private static HttpResponse<byte[]> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
}
