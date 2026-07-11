package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.ai.TravelerChat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the player said something to the Traveler. The server records it and hands it to the
 * off-process brain ({@link TravelerChat}); the answer comes back later as a {@link TravelerReplyS2C}. */
public record TravelerChatC2S(String message) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(message);
    }

    public static TravelerChatC2S decode(FriendlyByteBuf buf) {
        return new TravelerChatC2S(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null) {
                TravelerChat.ask(sender, message);
            }
        });
        c.setPacketHandled(true);
    }
}
