package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.ai.TravelerChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the player picked a gossip target and wrote their tip (≤200 chars). The server hands it
 * to the gossip brain ({@link TravelerChat#gossip}), which asks Sonnet to judge how credible it sounds and
 * nudges the target's (and, lightly, the gossiper's) reputation bias accordingly.
 */
public record GossipSubmitC2S(String targetUuid, String targetName, String text) {

    public static final int MAX_LEN = 200;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(targetUuid);
        buf.writeUtf(targetName);
        buf.writeUtf(text);
    }

    public static GossipSubmitC2S decode(FriendlyByteBuf buf) {
        return new GossipSubmitC2S(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null) {
                TravelerChat.gossip(sender, targetUuid, targetName, text);
            }
        });
        c.setPacketHandled(true);
    }
}
