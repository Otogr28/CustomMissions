package com.summerbuddies.custommissions.mission;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The NPC that offers a mission (typically an EasyNPC like the Traveler). Either field may be null; the
 * UUID is authoritative when present, the name is a human-readable fallback used for display and for
 * matching {@code talk_to_npc} objectives by name.
 */
public record Giver(@Nullable UUID npcUuid, @Nullable String npcName) {

    public static final Giver NONE = new Giver(null, null);

    public boolean isPresent() {
        return npcUuid != null || (npcName != null && !npcName.isBlank());
    }

    public String describe() {
        if (npcName != null && !npcName.isBlank()) {
            return npcName;
        }
        return npcUuid != null ? npcUuid.toString() : "unknown";
    }
}
