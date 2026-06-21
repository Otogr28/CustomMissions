package com.summerbuddies.custommissions.bridge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * Recognizes EasyNPC entities for {@code talk_to_npc} / {@code deliver_item_to_npc} objectives, without a
 * compile-time dependency: any entity whose registered type is in the {@code easy_npc} namespace counts as
 * an NPC. The auto-detected interaction is the convenient path; the robust alternative is the NPC's own
 * {@code RUN_COMMAND} action emitting {@code /mission signal talk_<name>} against a {@code custom_signal}
 * objective.
 */
public final class EasyNpcBridge {

    private EasyNpcBridge() {}

    public static boolean isNpc(Entity entity) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null && key.getNamespace().equals("easy_npc");
    }

    public static UUID npcUuid(Entity entity) {
        return entity.getUUID();
    }

    public static String npcName(Entity entity) {
        Component custom = entity.getCustomName();
        if (custom != null) {
            return custom.getString();
        }
        return entity.getName().getString();
    }
}
