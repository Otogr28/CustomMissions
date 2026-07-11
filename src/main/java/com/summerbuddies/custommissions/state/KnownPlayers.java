package com.summerbuddies.custommissions.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-wide roster of every player who has logged in (uuid → latest name). Populates the gossip target
 * list so a player can gossip about anyone who has played, not just whoever is online. Lives on the overworld
 * data storage, mirroring {@link LoreState}.
 */
public class KnownPlayers extends SavedData {

    private static final String NAME = "custommissions_known_players";

    public record Entry(UUID uuid, String name) {}

    private final Map<UUID, String> names = new LinkedHashMap<>();

    public static KnownPlayers get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().getLevel(Level.OVERWORLD);
        ServerLevel target = overworld != null ? overworld : anyLevel;
        return target.getDataStorage().computeIfAbsent(KnownPlayers::load, KnownPlayers::new, NAME);
    }

    public void record(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        String prev = names.put(uuid, name);
        if (!name.equals(prev)) {
            setDirty();
        }
    }

    /** All known players, most-recently-recorded last. */
    public List<Entry> all() {
        List<Entry> out = new ArrayList<>(names.size());
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue()));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", e.getKey());
            c.putString("Name", e.getValue());
            list.add(c);
        }
        tag.put("Players", list);
        return tag;
    }

    public static KnownPlayers load(CompoundTag tag) {
        KnownPlayers s = new KnownPlayers();
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (c.hasUUID("Id")) {
                s.names.put(c.getUUID("Id"), c.getString("Name"));
            }
        }
        return s;
    }
}
