package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.client.GossipClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: open the gossip target picker, carrying the roster of players you can gossip about (the
 * requester is sent first so the GUI can mark it "you"). Triggered by the gossip NPC's dialogue button.
 */
public record OpenGossipS2C(List<Target> targets) {

    public record Target(String uuid, String name) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targets.size());
        for (Target t : targets) {
            buf.writeUtf(t.uuid());
            buf.writeUtf(t.name());
        }
    }

    public static OpenGossipS2C decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Target> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Target(buf.readUtf(), buf.readUtf()));
        }
        return new OpenGossipS2C(list);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GossipClient.open(targets));
        ctx.get().setPacketHandled(true);
    }
}
