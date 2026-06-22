package com.summerbuddies.custommissions.mission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.mission.json.MissionDto;
import com.summerbuddies.custommissions.mission.json.ObjectiveDto;
import com.summerbuddies.custommissions.mission.json.RewardDto;
import com.summerbuddies.custommissions.objective.Objective;
import com.summerbuddies.custommissions.objective.ObjectiveFactory;
import com.summerbuddies.custommissions.reward.Reward;
import com.summerbuddies.custommissions.reward.RewardFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Owns the active set of {@link Mission}s. Reads authored chains from {@code config/custommissions/missions/}
 * (recursively) plus AI-authored dailies from {@code config/custommissions/daily/<today>/<uuid>/}, builds
 * the typed missions, and swaps them in atomically. Loaded on server start and re-run by
 * {@code /mission reload} — no restart needed to add or edit missions (or to ingest a fresh day's dailies).
 */
@Mod.EventBusSubscriber(modid = Constants.MODID)
public final class MissionManager {

    private static final Gson GSON = new GsonBuilder().create();
    public static final String DIR_NAME = "custommissions";

    private static volatile Map<String, Mission> active = Map.of();

    private MissionManager() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LoadResult r = reload();
        Constants.LOG.info("[custommissions] {}", r.summary());
    }

    // ---- accessors -------------------------------------------------------------------------------

    @Nullable
    public static Mission get(String id) {
        return active.get(id);
    }

    public static Set<String> ids() {
        return active.keySet();
    }

    public static java.util.Collection<Mission> all() {
        return active.values();
    }

    /** Today's daily missions owned by the given player uuid. */
    public static List<Mission> dailiesFor(UUID owner) {
        List<Mission> out = new ArrayList<>();
        for (Mission m : active.values()) {
            if (m.isDaily() && owner.equals(m.owner())) {
                out.add(m);
            }
        }
        return out;
    }

    // ---- paths -----------------------------------------------------------------------------------

    public static Path baseDir() {
        return FMLPaths.CONFIGDIR.get().resolve(DIR_NAME);
    }

    private static Path missionsDir() {
        return baseDir().resolve("missions");
    }

    private static Path dailyDir() {
        return baseDir().resolve("daily");
    }

    public static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    // ---- load ------------------------------------------------------------------------------------

    /** Re-read from disk and swap the active map. Bad files/entries are skipped with warnings. */
    public static LoadResult reload() {
        LoadResult report = new LoadResult();
        try {
            ensureBootstrap(report);
        } catch (IOException e) {
            report.warn("could not create config dir " + baseDir() + ": " + e.getMessage());
        }

        Map<String, Mission> built = new TreeMap<>();
        loadAuthored(missionsDir(), built, report);
        loadDailies(dailyDir().resolve(today()), built, report);

        active = Collections.unmodifiableMap(built);
        for (Mission m : built.values()) {
            report.objectives += m.objectives().size();
            if (m.isDaily()) report.dailies++;
        }
        report.missions = built.size();
        return report;
    }

    private static void loadAuthored(Path dir, Map<String, Mission> into, LoadResult report) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".json")).sorted().forEach(file -> {
                Mission m = readMission(file, null, report);
                addMission(m, file, into, report);
            });
        } catch (IOException e) {
            report.warn("listing missions/: " + e.getMessage());
        }
    }

    private static void loadDailies(Path todayDir, Map<String, Mission> into, LoadResult report) {
        if (!Files.isDirectory(todayDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(todayDir)) {
            walk.filter(p -> p.toString().endsWith(".json")).sorted().forEach(file -> {
                UUID owner = ownerFromPath(file);
                if (owner == null) {
                    report.warn("daily/" + todayDir.getFileName() + ": cannot read owner uuid from "
                            + file.getParent().getFileName() + " — skipped " + file.getFileName());
                    return;
                }
                Mission m = readMission(file, owner, report);
                addMission(m, file, into, report);
            });
        } catch (IOException e) {
            report.warn("listing daily/" + todayDir.getFileName() + ": " + e.getMessage());
        }
    }

    @Nullable
    private static UUID ownerFromPath(Path file) {
        Path parent = file.getParent();
        if (parent == null) return null;
        try {
            return UUID.fromString(parent.getFileName().toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void addMission(@Nullable Mission m, Path file, Map<String, Mission> into, LoadResult report) {
        if (m == null) {
            return;
        }
        if (into.containsKey(m.id())) {
            report.warn("duplicate mission id '" + m.id() + "' (in " + file.getFileName() + ") — overwriting");
        }
        into.put(m.id(), m);
    }

    @Nullable
    private static Mission readMission(Path file, @Nullable UUID owner, LoadResult report) {
        MissionDto dto;
        try (Reader r = Files.newBufferedReader(file)) {
            dto = GSON.fromJson(r, MissionDto.class);
        } catch (JsonParseException | IOException e) {
            report.warn(file.getFileName() + ": " + e.getMessage());
            return null;
        }
        if (dto == null) {
            report.warn(file.getFileName() + ": empty file, skipped");
            return null;
        }
        return build(dto, stripExt(file), owner, report);
    }

    @Nullable
    private static Mission build(MissionDto dto, String idFallback, @Nullable UUID owner, LoadResult report) {
        String id = dto.id != null && !dto.id.isBlank() ? dto.id.trim() : idFallback;
        MissionCategory category = MissionCategory.parse(dto.category);

        Giver giver = Giver.NONE;
        if (dto.giver != null) {
            giver = new Giver(ObjectiveFactory.uuid(dto.giver.npcUuid), dto.giver.npcName);
        }

        Prerequisites prereq = Prerequisites.NONE;
        if (dto.prerequisites != null) {
            int loreStage = dto.prerequisites.loreStage != null ? dto.prerequisites.loreStage : 0;
            Set<String> flags = dto.prerequisites.flags != null ? new LinkedHashSet<>(dto.prerequisites.flags) : Set.of();
            Set<String> prior = dto.prerequisites.priorMissions != null
                    ? new LinkedHashSet<>(dto.prerequisites.priorMissions) : Set.of();
            ResourceLocation dim = dto.prerequisites.dimension != null
                    ? ResourceLocation.tryParse(dto.prerequisites.dimension) : null;
            prereq = new Prerequisites(loreStage, flags, prior, dim);
        }

        List<Objective> objectives = new ArrayList<>();
        if (dto.objectives != null) {
            for (int i = 0; i < dto.objectives.size(); i++) {
                ObjectiveDto od = dto.objectives.get(i);
                if (od == null) continue;
                Objective o = ObjectiveFactory.build(od, id, i, report);
                if (o != null) objectives.add(o);
            }
        }
        if (objectives.isEmpty()) {
            report.warn(id + ": no valid objectives — skipped");
            return null;
        }

        List<Reward> rewards = rewards(dto.rewards, id, "reward", report);
        List<Reward> onAccept = rewards(dto.onAccept, id, "onAccept", report);
        List<Reward> onComplete = rewards(dto.onComplete, id, "onComplete", report);

        MissionLocation location = null;
        if (dto.location != null && dto.location.dimension != null
                && dto.location.x != null && dto.location.y != null && dto.location.z != null) {
            ResourceLocation dim = ResourceLocation.tryParse(dto.location.dimension);
            if (dim != null) {
                int radius = dto.location.radius != null ? Math.max(1, dto.location.radius) : MissionLocation.DEFAULT_RADIUS;
                String name = dto.location.waypoint != null ? dto.location.waypoint : title(dto);
                int rgb = ObjectiveFactory.parseRgb(dto.location.waypointColor, MissionLocation.DEFAULT_RGB);
                location = new MissionLocation(dim, dto.location.x, dto.location.y, dto.location.z, radius, name, rgb);
            }
        }

        long expirySeconds = 0;
        if (category == MissionCategory.DAILY) {
            int hours = dto.expiryHours != null ? Math.max(1, dto.expiryHours) : 24;
            expirySeconds = hours * 3600L;
        }
        Set<String> assignTo = dto.assignTo != null ? new LinkedHashSet<>(dto.assignTo) : Set.of();

        return new Mission(id, category, title(dto), nz(dto.description), nz(dto.lore), giver, prereq,
                objectives, rewards, onAccept, onComplete, location, expirySeconds, owner, assignTo);
    }

    private static List<Reward> rewards(@Nullable List<RewardDto> list, String id, String section, LoadResult report) {
        List<Reward> out = new ArrayList<>();
        if (list == null) return out;
        for (int i = 0; i < list.size(); i++) {
            RewardDto rd = list.get(i);
            if (rd == null) continue;
            Reward r = RewardFactory.build(rd, id, section, i, report);
            if (r != null) out.add(r);
        }
        return out;
    }

    private static String title(MissionDto dto) {
        return dto.title != null && !dto.title.isBlank() ? dto.title : (dto.id != null ? dto.id : "Mission");
    }

    private static String nz(@Nullable String s) {
        return s == null ? "" : s;
    }

    private static String stripExt(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ---- bootstrap -------------------------------------------------------------------------------

    private static void ensureBootstrap(LoadResult report) throws IOException {
        Files.createDirectories(missionsDir());
        Files.createDirectories(dailyDir());
        Files.createDirectories(baseDir().resolve("state").resolve("context"));
        Path example = missionsDir().resolve("example_primary.json");
        if (!Files.exists(example) && isEmptyDir(missionsDir())) {
            Files.writeString(example, EXAMPLE_PRIMARY);
            report.warn("wrote example mission " + example.getFileName()
                    + " — edit it and /mission reload, then /mission assign <you> example_first_steps");
        }
        Path readme = baseDir().resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, README);
        }
    }

    private static boolean isEmptyDir(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static final String EXAMPLE_PRIMARY = """
            {
              "id": "example_first_steps",
              "category": "primary",
              "title": "First Steps",
              "description": "Speak with the Traveler, then gather a little wood.",
              "lore": "Every road begins at someone's door.",
              "giver": { "npcName": "the-traveler" },
              "prerequisites": { "loreStage": 0 },
              "objectives": [
                { "type": "talk_to_npc", "npcUuid": "d48a3f45-0efd-46c6-9803-5e1256d95d33", "npcName": "the-traveler", "description": "Find the Traveler" },
                { "type": "collect_item", "item": "minecraft:oak_log", "count": 4, "description": "Gather 4 oak logs" }
              ],
              "rewards": [
                { "type": "item", "item": "minecraft:bread", "count": 8 },
                { "type": "xp", "amount": 30 }
              ]
            }
            """;

    private static final String README = """
            CustomMissions config
            =====================
            missions/         authored chains (primary/secondary). Edit/add *.json, then /mission reload.
            daily/<date>/<player-uuid>/NN.json   AI-authored daily missions (the brain writes these).
            state/context/<uuid>.json            per-player context the mod EXPORTS for the AI brain.
            state/world_state.json               server lore state + roster the mod EXPORTS for the AI.

            A mission file is one JSON object. See DSL.md in the repo for the full mission language.
            Hot-reload with /mission reload (no restart). Assign with /mission assign <player> <id>,
            or let players /mission accept <id> when prerequisites are met.

            Objective types: kill_entity, collect_item, reach_location, talk_to_npc, enter_dimension,
              advancement, use_block, place_block, deliver_item_to_npc, custom_signal.
            Reward types: item, xp, command, cutscene, unlock, companion, lore_stage_advance.

            Custom signals: emit from KubeJS/EasyNPC/other mods with
              /mission signal <name> [count] [player]
            to advance any custom_signal objective.
            """;
}
