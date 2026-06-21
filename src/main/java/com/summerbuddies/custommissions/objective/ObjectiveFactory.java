package com.summerbuddies.custommissions.objective;

import com.summerbuddies.custommissions.mission.LoadResult;
import com.summerbuddies.custommissions.mission.MissionLocation;
import com.summerbuddies.custommissions.mission.json.ObjectiveDto;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Builds a typed {@link Objective} from an {@link ObjectiveDto}, applying defaults and warning on errors. */
public final class ObjectiveFactory {

    private ObjectiveFactory() {}

    @Nullable
    public static Objective build(ObjectiveDto d, String missionId, int index, LoadResult report) {
        String type = d.type == null ? "" : d.type.trim().toLowerCase();
        boolean optional = d.optional != null && d.optional;
        int count = d.count != null ? Math.max(1, d.count) : 1;

        switch (type) {
            case "kill_entity" -> {
                if (d.entity == null) return missing(report, missionId, index, type, "entity");
                boolean isTag = d.entity.startsWith("#");
                ResourceLocation id = rl(isTag ? d.entity.substring(1) : d.entity);
                if (id == null) return bad(report, missionId, index, type, "entity", d.entity);
                return new Objectives.KillEntity(id, isTag, count, desc(d, "Defeat " + d.entity), optional);
            }
            case "collect_item" -> {
                if (d.item == null) return missing(report, missionId, index, type, "item");
                ResourceLocation id = rl(d.item);
                if (id == null) return bad(report, missionId, index, type, "item", d.item);
                return new Objectives.CollectItem(id, count, desc(d, "Collect " + count + "x " + d.item), optional);
            }
            case "reach_location" -> {
                MissionLocation loc = location(d, report, missionId, index);
                if (loc == null) return null;
                return new Objectives.ReachLocation(loc, desc(d, "Reach " + loc.waypointName()), optional);
            }
            case "talk_to_npc" -> {
                UUID uuid = uuid(d.npcUuid);
                if (uuid == null && (d.npcName == null || d.npcName.isBlank())) {
                    return missing(report, missionId, index, type, "npcUuid/npcName");
                }
                return new Objectives.TalkToNpc(uuid, d.npcName, desc(d, "Talk to " + npcLabel(d)), optional);
            }
            case "enter_dimension" -> {
                if (d.dimension == null) return missing(report, missionId, index, type, "dimension");
                ResourceLocation id = rl(d.dimension);
                if (id == null) return bad(report, missionId, index, type, "dimension", d.dimension);
                return new Objectives.EnterDimension(id, desc(d, "Travel to " + d.dimension), optional);
            }
            case "advancement" -> {
                if (d.advancement == null) return missing(report, missionId, index, type, "advancement");
                ResourceLocation id = rl(d.advancement);
                if (id == null) return bad(report, missionId, index, type, "advancement", d.advancement);
                return new Objectives.AdvancementObj(id, desc(d, "Earn " + d.advancement), optional);
            }
            case "use_block" -> {
                if (d.block == null) return missing(report, missionId, index, type, "block");
                ResourceLocation id = rl(d.block);
                if (id == null) return bad(report, missionId, index, type, "block", d.block);
                return new Objectives.UseBlockObj(id, count, desc(d, "Use " + d.block), optional);
            }
            case "place_block" -> {
                if (d.block == null) return missing(report, missionId, index, type, "block");
                ResourceLocation id = rl(d.block);
                if (id == null) return bad(report, missionId, index, type, "block", d.block);
                return new Objectives.PlaceBlockObj(id, count, desc(d, "Place " + d.block), optional);
            }
            case "deliver_item_to_npc" -> {
                if (d.item == null) return missing(report, missionId, index, type, "item");
                ResourceLocation id = rl(d.item);
                if (id == null) return bad(report, missionId, index, type, "item", d.item);
                UUID uuid = uuid(d.npcUuid);
                if (uuid == null && (d.npcName == null || d.npcName.isBlank())) {
                    return missing(report, missionId, index, type, "npcUuid/npcName");
                }
                int amount = d.count != null ? Math.max(1, d.count) : 1;
                return new Objectives.DeliverItem(uuid, d.npcName, id, amount,
                        desc(d, "Deliver " + amount + "x " + d.item + " to " + npcLabel(d)), optional);
            }
            case "custom_signal" -> {
                if (d.signal == null || d.signal.isBlank()) return missing(report, missionId, index, type, "signal");
                return new Objectives.CustomSignal(d.signal, count, desc(d, d.signal), optional);
            }
            default -> {
                report.warn(missionId + " objective #" + index + ": unknown type '" + d.type + "' — skipped");
                return null;
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    @Nullable
    private static MissionLocation location(ObjectiveDto d, LoadResult report, String missionId, int index) {
        if (d.dimension == null || d.x == null || d.y == null || d.z == null) {
            report.warn(missionId + " objective #" + index + " (reach_location): needs dimension + x,y,z — skipped");
            return null;
        }
        ResourceLocation dim = rl(d.dimension);
        if (dim == null) {
            report.warn(missionId + " objective #" + index + " (reach_location): bad dimension '" + d.dimension + "'");
            return null;
        }
        int radius = d.radius != null ? Math.max(1, d.radius) : MissionLocation.DEFAULT_RADIUS;
        String name = d.waypoint != null && !d.waypoint.isBlank() ? d.waypoint : "Objective";
        int rgb = parseRgb(d.waypointColor, MissionLocation.DEFAULT_RGB);
        return new MissionLocation(dim, d.x, d.y, d.z, radius, name, rgb);
    }

    private static String npcLabel(ObjectiveDto d) {
        if (d.npcName != null && !d.npcName.isBlank()) return d.npcName;
        return d.npcUuid != null ? d.npcUuid : "the NPC";
    }

    private static String desc(ObjectiveDto d, String fallback) {
        return d.description != null && !d.description.isBlank() ? d.description : fallback;
    }

    @Nullable
    private static Objective missing(LoadResult r, String mission, int index, String type, String field) {
        r.warn(mission + " objective #" + index + " (" + type + "): missing '" + field + "' — skipped");
        return null;
    }

    @Nullable
    private static Objective bad(LoadResult r, String mission, int index, String type, String field, String value) {
        r.warn(mission + " objective #" + index + " (" + type + "): bad " + field + " '" + value + "' — skipped");
        return null;
    }

    @Nullable
    static ResourceLocation rl(String raw) {
        return raw == null ? null : ResourceLocation.tryParse(raw.trim());
    }

    @Nullable
    public static UUID uuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static int parseRgb(@Nullable String raw, int fallback) {
        if (raw == null) return fallback;
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6) return fallback;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
