package com.summerbuddies.custommissions.objective;

import com.summerbuddies.custommissions.event.MissionEvent;
import com.summerbuddies.custommissions.mission.MissionLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Immutable record implementations of every {@link Objective} type. Built by {@link ObjectiveFactory}. */
public final class Objectives {

    private Objectives() {}

    /** Kill {@code count} entities of an id, or any entity in a {@code #tag} when {@code isTag}. */
    public record KillEntity(ResourceLocation entity, boolean isTag, int count, String desc, boolean optional)
            implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.KILL_ENTITY; }
        @Override public int required() { return Math.max(1, count); }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.Kill k) {
                boolean hit = isTag ? k.tags().contains(entity) : k.entity().equals(entity);
                if (hit) return 1;
            }
            return 0;
        }
    }

    /** Pick up {@code count} of an item (counts cumulative pickups). */
    public record CollectItem(ResourceLocation item, int count, String desc, boolean optional)
            implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.COLLECT_ITEM; }
        @Override public int required() { return Math.max(1, count); }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.Pickup pk && pk.item().equals(item)) {
                return Math.max(1, pk.count());
            }
            return 0;
        }
    }

    /** Reach a point in a dimension; drives both completion and the on-screen waypoint. */
    public record ReachLocation(MissionLocation loc, String desc, boolean optional) implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.REACH_LOCATION; }
        @Override public int required() { return 1; }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override @Nullable public MissionLocation waypoint() { return loc; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.Move mv
                    && loc.within(mv.dimension(), mv.pos().getX() + 0.5, mv.pos().getY() + 0.5, mv.pos().getZ() + 0.5)) {
                return 1;
            }
            return 0;
        }
    }

    /** Interact with a specific NPC (by uuid and/or name). */
    public record TalkToNpc(@Nullable UUID uuid, @Nullable String name, String desc, boolean optional)
            implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.TALK_TO_NPC; }
        @Override public int required() { return 1; }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.NpcTalk t && matchesNpc(uuid, name, t)) return 1;
            return 0;
        }
    }

    /** Be in / enter a dimension (a position sample in the dimension also satisfies it). */
    public record EnterDimension(ResourceLocation dim, String desc, boolean optional) implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.ENTER_DIMENSION; }
        @Override public int required() { return 1; }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.EnterDim ed && ed.dimension().equals(dim)) return 1;
            if (e instanceof MissionEvent.Move mv && mv.dimension().equals(dim)) return 1;
            return 0;
        }
    }

    /** Earn an advancement. */
    public record AdvancementObj(ResourceLocation id, String desc, boolean optional) implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.ADVANCEMENT; }
        @Override public int required() { return 1; }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.Advancement a && a.id().equals(id)) return 1;
            return 0;
        }
    }

    /** Right-click a block {@code count} times. */
    public record UseBlockObj(ResourceLocation block, int count, String desc, boolean optional)
            implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.USE_BLOCK; }
        @Override public int required() { return Math.max(1, count); }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.UseBlock ub && ub.block().equals(block)) return 1;
            return 0;
        }
    }

    /** Place a block {@code count} times. */
    public record PlaceBlockObj(ResourceLocation block, int count, String desc, boolean optional)
            implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.PLACE_BLOCK; }
        @Override public int required() { return Math.max(1, count); }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.PlaceBlock pb && pb.block().equals(block)) return 1;
            return 0;
        }
    }

    /** Bring {@code count} of an item to an NPC; consumes the items on a matching interaction. */
    public record DeliverItem(@Nullable UUID uuid, @Nullable String name, ResourceLocation item, int count,
                              String desc, boolean optional) implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.DELIVER_ITEM_TO_NPC; }
        @Override public int required() { return 1; }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (!(e instanceof MissionEvent.NpcTalk t) || !matchesNpc(uuid, name, t)) {
                return 0;
            }
            int need = Math.max(1, count);
            if (countItem(p, item) < need) {
                p.displayClientMessage(Component.literal("You need " + need + "x " + item.getPath() + "."), true);
                return 0;
            }
            removeItem(p, item, need);
            return 1;
        }
    }

    /** Advance on a named custom signal (the universal escape hatch for KubeJS/EasyNPC/other mods). */
    public record CustomSignal(String signal, int count, String desc, boolean optional) implements Objective {
        @Override public ObjectiveType type() { return ObjectiveType.CUSTOM_SIGNAL; }
        @Override public int required() { return Math.max(1, count); }
        @Override public String describe() { return desc; }
        @Override public boolean optional() { return optional; }
        @Override public int progress(MissionEvent e, ServerPlayer p) {
            if (e instanceof MissionEvent.Signal s && s.name().equalsIgnoreCase(signal)) {
                return Math.max(1, s.count());
            }
            return 0;
        }
    }

    // ---- shared helpers --------------------------------------------------------------------------

    private static boolean matchesNpc(@Nullable UUID uuid, @Nullable String name, MissionEvent.NpcTalk t) {
        if (uuid != null && uuid.equals(t.uuid())) {
            return true;
        }
        return name != null && !name.isBlank() && t.name() != null && name.equalsIgnoreCase(t.name());
    }

    private static int countItem(ServerPlayer p, ResourceLocation item) {
        int total = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && item.equals(ForgeRegistries.ITEMS.getKey(st.getItem()))) {
                total += st.getCount();
            }
        }
        return total;
    }

    private static void removeItem(ServerPlayer p, ResourceLocation item, int amount) {
        int left = amount;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && item.equals(ForgeRegistries.ITEMS.getKey(st.getItem()))) {
                int take = Math.min(left, st.getCount());
                st.shrink(take);
                left -= take;
            }
        }
    }
}
