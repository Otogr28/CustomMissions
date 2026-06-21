package com.summerbuddies.custommissions.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server-wide story progression: the current {@code chapter} and {@code stage}, narrative {@code flags},
 * and the set of gates unlocked by the story. This is the single source of truth the AI mission brain
 * reads (exported to {@code world_state.json}) and the {@code lore_stage_advance} reward writes. Lives on
 * the overworld's data storage so it is world-saved and global, mirroring Realm Gates' {@code FogBarriers}.
 */
public class LoreState extends SavedData {

    private static final String NAME = "custommissions_lore_state";

    private int chapter;
    private int stage;
    private final Set<String> flags = new LinkedHashSet<>();
    private final Set<ResourceLocation> unlockedGates = new LinkedHashSet<>();

    public static LoreState get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().getLevel(Level.OVERWORLD);
        ServerLevel target = overworld != null ? overworld : anyLevel;
        return target.getDataStorage().computeIfAbsent(LoreState::load, LoreState::new, NAME);
    }

    public int chapter() { return chapter; }
    public int stage() { return stage; }
    public Set<String> flags() { return flags; }
    public Set<ResourceLocation> unlockedGates() { return unlockedGates; }

    public void setChapter(int value) {
        chapter = Math.max(0, value);
        setDirty();
    }

    public void setStage(int value) {
        stage = Math.max(0, value);
        setDirty();
    }

    /** Advance the stage by one and return the new value. */
    public int advanceStage() {
        stage++;
        setDirty();
        return stage;
    }

    public boolean addFlag(String flag) {
        boolean added = flags.add(flag);
        if (added) setDirty();
        return added;
    }

    public boolean removeFlag(String flag) {
        boolean removed = flags.remove(flag);
        if (removed) setDirty();
        return removed;
    }

    public void unlockGate(ResourceLocation gate) {
        if (unlockedGates.add(gate)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Chapter", chapter);
        tag.putInt("Stage", stage);
        ListTag flagList = new ListTag();
        for (String f : flags) flagList.add(StringTag.valueOf(f));
        tag.put("Flags", flagList);
        ListTag gateList = new ListTag();
        for (ResourceLocation g : unlockedGates) gateList.add(StringTag.valueOf(g.toString()));
        tag.put("UnlockedGates", gateList);
        return tag;
    }

    public static LoreState load(CompoundTag tag) {
        LoreState s = new LoreState();
        s.chapter = tag.getInt("Chapter");
        s.stage = tag.getInt("Stage");
        ListTag flagList = tag.getList("Flags", Tag.TAG_STRING);
        for (int i = 0; i < flagList.size(); i++) s.flags.add(flagList.getString(i));
        ListTag gateList = tag.getList("UnlockedGates", Tag.TAG_STRING);
        for (int i = 0; i < gateList.size(); i++) {
            ResourceLocation rl = ResourceLocation.tryParse(gateList.getString(i));
            if (rl != null) s.unlockedGates.add(rl);
        }
        return s;
    }
}
