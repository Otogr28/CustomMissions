package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.client.TravelerClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: open the Traveler chat screen. {@code greeting} (optional) is shown as the Traveler's
 * opening line when the transcript is empty. The client defers opening until the player is in-world. */
public record OpenTravelerChatS2C(String greeting) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(greeting);
    }

    public static OpenTravelerChatS2C decode(FriendlyByteBuf buf) {
        return new OpenTravelerChatS2C(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TravelerClient.requestOpen(greeting));
        ctx.get().setPacketHandled(true);
    }
}
