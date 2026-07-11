package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.client.TravelerClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the Traveler's answer to the player's last message (or an {@code error} fallback line
 * when the brain timed out / failed). Appended to the chat transcript by {@link TravelerClient}. */
public record TravelerReplyS2C(String reply, boolean error) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(reply);
        buf.writeBoolean(error);
    }

    public static TravelerReplyS2C decode(FriendlyByteBuf buf) {
        return new TravelerReplyS2C(buf.readUtf(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TravelerClient.onReply(reply, error));
        ctx.get().setPacketHandled(true);
    }
}
