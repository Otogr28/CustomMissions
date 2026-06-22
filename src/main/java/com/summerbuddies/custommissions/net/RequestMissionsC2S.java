package com.summerbuddies.custommissions.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: "send me my current missions" (fired when the player opens the adventure-log screen). */
public record RequestMissionsC2S() {

    public void encode(FriendlyByteBuf buf) {
        // no payload
    }

    public static RequestMissionsC2S decode(FriendlyByteBuf buf) {
        return new RequestMissionsC2S();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                MissionSync.send(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
