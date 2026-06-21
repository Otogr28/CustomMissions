package com.summerbuddies.custommissions.reward;

import com.summerbuddies.custommissions.mission.LoadResult;
import com.summerbuddies.custommissions.mission.json.RewardDto;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Builds a typed {@link Reward} from a {@link RewardDto}, applying defaults and warning on errors. */
public final class RewardFactory {

    private RewardFactory() {}

    @Nullable
    public static Reward build(RewardDto d, String missionId, String section, int index, LoadResult report) {
        String type = d.type == null ? "" : d.type.trim().toLowerCase();
        switch (type) {
            case "item" -> {
                if (d.item == null) return missing(report, missionId, section, index, type, "item");
                ResourceLocation id = rl(d.item);
                if (id == null) return bad(report, missionId, section, index, type, "item", d.item);
                int count = d.count != null ? Math.max(1, d.count) : 1;
                return new Rewards.Item(id, count);
            }
            case "xp" -> {
                if (d.amount == null) return missing(report, missionId, section, index, type, "amount");
                return new Rewards.Xp(Math.max(0, d.amount));
            }
            case "command" -> {
                if (d.command == null || d.command.isBlank()) {
                    return missing(report, missionId, section, index, type, "command");
                }
                boolean asPlayer = d.asPlayer != null && d.asPlayer;
                return new Rewards.Command(d.command, asPlayer);
            }
            case "cutscene" -> {
                if (d.script == null || d.script.isBlank()) {
                    return missing(report, missionId, section, index, type, "script");
                }
                return new Rewards.Cutscene(d.script);
            }
            case "unlock", "unlock_gate" -> {
                if (d.gate == null) return missing(report, missionId, section, index, type, "gate");
                ResourceLocation id = rl(d.gate);
                if (id == null) return bad(report, missionId, section, index, type, "gate", d.gate);
                return new Rewards.UnlockGate(id);
            }
            case "companion" -> {
                if (d.companion == null || d.companion.isBlank()) {
                    return missing(report, missionId, section, index, type, "companion");
                }
                return new Rewards.Companion(d.companion);
            }
            case "lore_stage_advance" -> {
                return new Rewards.LoreStageAdvance(d.to); // null => +1
            }
            default -> {
                report.warn(missionId + " " + section + " #" + index + ": unknown reward type '" + d.type + "' — skipped");
                return null;
            }
        }
    }

    @Nullable
    private static Reward missing(LoadResult r, String m, String s, int i, String type, String field) {
        r.warn(m + " " + s + " #" + i + " (" + type + "): missing '" + field + "' — skipped");
        return null;
    }

    @Nullable
    private static Reward bad(LoadResult r, String m, String s, int i, String type, String field, String value) {
        r.warn(m + " " + s + " #" + i + " (" + type + "): bad " + field + " '" + value + "' — skipped");
        return null;
    }

    @Nullable
    private static ResourceLocation rl(String raw) {
        return raw == null ? null : ResourceLocation.tryParse(raw.trim());
    }
}
