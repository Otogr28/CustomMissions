package com.summerbuddies.custommissions.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One player's mission ledger: the active missions (each with its accept time and per-objective progress
 * counters), plus the sets of completed, claimed (rewards granted — an idempotency guard), and expired
 * mission ids. Backed by the player's persistent NBT via {@link PlayerMissionStore}, so it survives death,
 * respawn, logout, and restart with no capability boilerplate.
 */
public final class PlayerMissions {

    private final Map<String, int[]> counters = new LinkedHashMap<>();
    private final Map<String, Long> acceptedAt = new LinkedHashMap<>();
    private final Set<String> completed = new LinkedHashSet<>();
    private final Set<String> claimed = new LinkedHashSet<>();
    private final Set<String> expired = new LinkedHashSet<>();

    // ---- queries ---------------------------------------------------------------------------------

    public boolean isActive(String id) { return counters.containsKey(id); }
    public boolean isCompleted(String id) { return completed.contains(id); }
    public boolean isClaimed(String id) { return claimed.contains(id); }
    public boolean isExpired(String id) { return expired.contains(id); }

    public Set<String> activeIds() { return new LinkedHashSet<>(counters.keySet()); }
    public Set<String> completedIds() { return completed; }
    public Set<String> expiredIds() { return expired; }

    @Nullable
    public int[] counters(String id) {
        int[] c = counters.get(id);
        return c == null ? null : c.clone();
    }

    public long acceptedAt(String id) {
        return acceptedAt.getOrDefault(id, 0L);
    }

    // ---- mutations -------------------------------------------------------------------------------

    public void accept(String id, int objectiveCount, long nowEpochSeconds) {
        counters.put(id, new int[Math.max(0, objectiveCount)]);
        acceptedAt.put(id, nowEpochSeconds);
    }

    public void setCounters(String id, int[] values) {
        if (counters.containsKey(id)) {
            counters.put(id, values.clone());
        }
    }

    public void complete(String id) {
        counters.remove(id);
        acceptedAt.remove(id);
        expired.remove(id);
        completed.add(id);
    }

    public void markClaimed(String id) {
        claimed.add(id);
    }

    public void expire(String id) {
        counters.remove(id);
        acceptedAt.remove(id);
        expired.add(id);
    }

    public void abandon(String id) {
        counters.remove(id);
        acceptedAt.remove(id);
    }

    // ---- persistence -----------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag activeList = new ListTag();
        for (Map.Entry<String, int[]> e : counters.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", e.getKey());
            entry.putLong("At", acceptedAt.getOrDefault(e.getKey(), 0L));
            entry.putIntArray("C", e.getValue());
            activeList.add(entry);
        }
        tag.put("Active", activeList);
        tag.put("Completed", strings(completed));
        tag.put("Claimed", strings(claimed));
        tag.put("Expired", strings(expired));
        return tag;
    }

    public static PlayerMissions load(CompoundTag tag) {
        PlayerMissions p = new PlayerMissions();
        ListTag activeList = tag.getList("Active", Tag.TAG_COMPOUND);
        for (int i = 0; i < activeList.size(); i++) {
            CompoundTag e = activeList.getCompound(i);
            String id = e.getString("Id");
            if (id.isEmpty()) continue;
            p.counters.put(id, e.getIntArray("C"));
            p.acceptedAt.put(id, e.getLong("At"));
        }
        readStrings(tag, "Completed", p.completed);
        readStrings(tag, "Claimed", p.claimed);
        readStrings(tag, "Expired", p.expired);
        return p;
    }

    private static ListTag strings(Set<String> set) {
        ListTag list = new ListTag();
        for (String s : set) list.add(StringTag.valueOf(s));
        return list;
    }

    private static void readStrings(CompoundTag tag, String key, Set<String> into) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) into.add(list.getString(i));
    }
}
