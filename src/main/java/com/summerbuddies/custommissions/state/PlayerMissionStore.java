package com.summerbuddies.custommissions.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

/**
 * Reads/writes {@link PlayerMissions} to the player's persistent NBT, stored under
 * {@code ForgeData -> PlayerPersisted -> custommissions}. The {@code PlayerPersisted} sub-tag
 * ({@link Player#PERSISTED_NBT_TAG}) is preserved by Forge across death/respawn, and the whole ForgeData
 * compound is saved to the player {@code .dat} — so progress survives everything without a capability.
 */
public final class PlayerMissionStore {

    private static final String KEY = "custommissions";

    private PlayerMissionStore() {}

    public static PlayerMissions get(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return PlayerMissions.load(persisted.getCompound(KEY));
    }

    public static void put(ServerPlayer player, PlayerMissions missions) {
        CompoundTag forgeData = player.getPersistentData();
        CompoundTag persisted = forgeData.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(KEY, missions.save());
        forgeData.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Read-modify-write convenience: load, mutate, save. */
    public static void update(ServerPlayer player, Consumer<PlayerMissions> mutator) {
        PlayerMissions m = get(player);
        mutator.accept(m);
        put(player, m);
    }
}
