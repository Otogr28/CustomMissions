package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.client.ClientMissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client snapshot of a player's missions, for the {@code M} adventure-log screen (and a future
 * HUD). Sent on login, on every progress/lifecycle change, and in response to {@link RequestMissionsC2S}.
 * Three groups mirror the screen's tabs: active (with live progress), available to accept, and completed.
 */
public record MissionSyncS2C(List<Entry> active, List<Entry> available, List<Entry> completed) {

    /** One mission as shown in the UI. {@code category}: 0 daily, 1 primary, 2 secondary. */
    public record Entry(String id, String title, String description, String lore, String giver, int category,
                        List<Obj> objectives, List<String> rewards) {}

    /** One objective line: its text and current/needed counts. */
    public record Obj(String desc, int have, int required) {}

    public void encode(FriendlyByteBuf buf) {
        writeEntries(buf, active);
        writeEntries(buf, available);
        writeEntries(buf, completed);
    }

    public static MissionSyncS2C decode(FriendlyByteBuf buf) {
        return new MissionSyncS2C(readEntries(buf), readEntries(buf), readEntries(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMissions.applySnapshot(this));
        ctx.get().setPacketHandled(true);
    }

    private static void writeEntries(FriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUtf(e.id());
            buf.writeUtf(e.title());
            buf.writeUtf(e.description());
            buf.writeUtf(e.lore());
            buf.writeUtf(e.giver());
            buf.writeVarInt(e.category());
            buf.writeVarInt(e.objectives().size());
            for (Obj o : e.objectives()) {
                buf.writeUtf(o.desc());
                buf.writeVarInt(o.have());
                buf.writeVarInt(o.required());
            }
            buf.writeVarInt(e.rewards().size());
            for (String r : e.rewards()) {
                buf.writeUtf(r);
            }
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf();
            String title = buf.readUtf();
            String desc = buf.readUtf();
            String lore = buf.readUtf();
            String giver = buf.readUtf();
            int cat = buf.readVarInt();
            int m = buf.readVarInt();
            List<Obj> objs = new ArrayList<>(m);
            for (int j = 0; j < m; j++) {
                objs.add(new Obj(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
            }
            int rn = buf.readVarInt();
            List<String> rewards = new ArrayList<>(rn);
            for (int j = 0; j < rn; j++) {
                rewards.add(buf.readUtf());
            }
            out.add(new Entry(id, title, desc, lore, giver, cat, objs, rewards));
        }
        return out;
    }
}
