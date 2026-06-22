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
 * One player's mission ledger. Progress is kept SEPARATELY from the active set: the {@code progress} map
 * holds each mission's per-objective counters and survives an {@code abandon}, so re-accepting a mission
 * resumes where the player left off. {@code active} is just which missions are currently accepted. Plus the
 * sets of completed, claimed (rewards granted — idempotency guard), and expired ids. Backed by the player's
 * persistent NBT via {@link PlayerMissionStore}, so everything survives death, respawn, logout, and restart.
 */
public final class PlayerMissions {

    private final Map<String, int[]> progress = new LinkedHashMap<>();
    private final Map<String, Long> acceptedAt = new LinkedHashMap<>();
    private final Set<String> active = new LinkedHashSet<>();
    private final Set<String> completed = new LinkedHashSet<>();
    private final Set<String> claimed = new LinkedHashSet<>();
    private final Set<String> expired = new LinkedHashSet<>();

    // ---- queries ---------------------------------------------------------------------------------

    public boolean isActive(String id) { return active.contains(id); }
    public boolean isCompleted(String id) { return completed.contains(id); }
    public boolean isClaimed(String id) { return claimed.contains(id); }
    public boolean isExpired(String id) { return expired.contains(id); }

    public Set<String> activeIds() { return new LinkedHashSet<>(active); }
    public Set<String> completedIds() { return completed; }
    public Set<String> expiredIds() { return expired; }

    /** Saved counters for a mission (kept across abandon). Null if the player never accepted it. */
    @Nullable
    public int[] counters(String id) {
        int[] c = progress.get(id);
        return c == null ? null : c.clone();
    }

    public long acceptedAt(String id) {
        return acceptedAt.getOrDefault(id, 0L);
    }

    // ---- mutations -------------------------------------------------------------------------------

    /** Accept a mission, PRESERVING any prior progress (resized if the objective count changed). */
    public void accept(String id, int objectiveCount, long nowEpochSeconds) {
        int[] cur = progress.get(id);
        if (cur == null || cur.length != objectiveCount) {
            int[] resized = new int[Math.max(0, objectiveCount)];
            if (cur != null) {
                System.arraycopy(cur, 0, resized, 0, Math.min(cur.length, resized.length));
            }
            progress.put(id, resized);
        }
        acceptedAt.put(id, nowEpochSeconds);
        active.add(id);
        expired.remove(id);
    }

    public void setCounters(String id, int[] values) {
        progress.put(id, values.clone());
    }

    public void complete(String id) {
        active.remove(id);
        progress.remove(id);
        acceptedAt.remove(id);
        expired.remove(id);
        completed.add(id);
    }

    public void markClaimed(String id) {
        claimed.add(id);
    }

    public void expire(String id) {
        active.remove(id);
        progress.remove(id);
        acceptedAt.remove(id);
        expired.add(id);
    }

    /** Drop from the active set but KEEP progress, so re-accepting resumes. */
    public void abandon(String id) {
        active.remove(id);
    }

    // ---- persistence -----------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag prog = new ListTag();
        for (Map.Entry<String, int[]> e : progress.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", e.getKey());
            entry.putLong("At", acceptedAt.getOrDefault(e.getKey(), 0L));
            entry.putIntArray("C", e.getValue());
            prog.add(entry);
        }
        tag.put("Progress", prog);
        tag.put("Active", strings(active));
        tag.put("Completed", strings(completed));
        tag.put("Claimed", strings(claimed));
        tag.put("Expired", strings(expired));
        return tag;
    }

    public static PlayerMissions load(CompoundTag tag) {
        PlayerMissions p = new PlayerMissions();
        ListTag prog = tag.getList("Progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < prog.size(); i++) {
            CompoundTag e = prog.getCompound(i);
            String id = e.getString("Id");
            if (id.isEmpty()) continue;
            p.progress.put(id, e.getIntArray("C"));
            p.acceptedAt.put(id, e.getLong("At"));
        }
        readStrings(tag, "Active", p.active);
        readStrings(tag, "Completed", p.completed);
        readStrings(tag, "Claimed", p.claimed);
        readStrings(tag, "Expired", p.expired);
        // Backward-compat: older saves stored counters under "Active" as compounds; if Active came back
        // empty but Progress has entries, treat all progressed missions as active (best-effort migration).
        if (p.active.isEmpty() && !p.progress.isEmpty()) {
            ListTag legacy = tag.getList("Active", Tag.TAG_COMPOUND);
            for (int i = 0; i < legacy.size(); i++) {
                String id = legacy.getCompound(i).getString("Id");
                if (!id.isEmpty()) {
                    p.active.add(id);
                    p.progress.putIfAbsent(id, legacy.getCompound(i).getIntArray("C"));
                    p.acceptedAt.putIfAbsent(id, legacy.getCompound(i).getLong("At"));
                }
            }
        }
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
