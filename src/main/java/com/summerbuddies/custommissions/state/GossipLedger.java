package com.summerbuddies.custommissions.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide reputation ledger driven by gossip: per-player {@code bias} (-100 wary .. +100 trusted) plus a
 * few recent credible {@code rumors}. Keyed by UUID so it works for OFFLINE targets too — you can gossip
 * about anyone who has played. Read by the AI exporter + Flugel chat to colour how Flugel treats a player and
 * what personal missions they get. Lives on the overworld data storage, mirroring {@link LoreState}.
 */
public class GossipLedger extends SavedData {

    private static final String NAME = "custommissions_gossip";
    private static final int MAX_RUMORS = 5;

    private final Map<UUID, Integer> bias = new HashMap<>();
    private final Map<UUID, List<String>> rumors = new HashMap<>();

    public static GossipLedger get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().getLevel(Level.OVERWORLD);
        ServerLevel target = overworld != null ? overworld : anyLevel;
        return target.getDataStorage().computeIfAbsent(GossipLedger::load, GossipLedger::new, NAME);
    }

    public int bias(UUID id) {
        return bias.getOrDefault(id, 0);
    }

    public List<String> rumors(UUID id) {
        return new ArrayList<>(rumors.getOrDefault(id, List.of()));
    }

    /** Nudge a player's reputation, clamped to [-100, 100]. */
    public void addBias(UUID id, int delta) {
        if (id == null || delta == 0) {
            return;
        }
        bias.put(id, Math.max(-100, Math.min(100, bias(id) + delta)));
        setDirty();
    }

    /** Record a short credible rumor about a player (ring buffer of the last {@value #MAX_RUMORS}). */
    public void addRumor(UUID id, String note) {
        if (id == null || note == null || note.isBlank()) {
            return;
        }
        List<String> list = rumors.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(note.strip());
        while (list.size() > MAX_RUMORS) {
            list.remove(0);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(bias.keySet());
        ids.addAll(rumors.keySet());
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", id);
            c.putInt("Bias", bias.getOrDefault(id, 0));
            List<String> rs = rumors.get(id);
            if (rs != null && !rs.isEmpty()) {
                ListTag rl = new ListTag();
                for (String r : rs) rl.add(StringTag.valueOf(r));
                c.put("Rumors", rl);
            }
            list.add(c);
        }
        tag.put("Players", list);
        return tag;
    }

    public static GossipLedger load(CompoundTag tag) {
        GossipLedger s = new GossipLedger();
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("Id")) {
                continue;
            }
            UUID id = c.getUUID("Id");
            s.bias.put(id, c.getInt("Bias"));
            ListTag rl = c.getList("Rumors", Tag.TAG_STRING);
            if (!rl.isEmpty()) {
                List<String> rs = new ArrayList<>();
                for (int j = 0; j < rl.size(); j++) {
                    rs.add(rl.getString(j));
                }
                s.rumors.put(id, rs);
            }
        }
        return s;
    }
}
