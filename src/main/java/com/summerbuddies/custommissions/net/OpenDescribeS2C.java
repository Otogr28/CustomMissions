package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.client.ClientMissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: open the self-description screen, prefilled with {@code text} (the saved draft, or the
 * current description when editing). {@code firstTime} switches the intro copy between the login prompt and a
 * later edit. The client defers actually opening the screen until the player is in-world and free, so it
 * never fights the loading screen (see {@link ClientMissions#requestDescribe}).
 */
public record OpenDescribeS2C(String text, boolean firstTime) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(text);
        buf.writeBoolean(firstTime);
    }

    public static OpenDescribeS2C decode(FriendlyByteBuf buf) {
        return new OpenDescribeS2C(buf.readUtf(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMissions.requestDescribe(text, firstTime));
        ctx.get().setPacketHandled(true);
    }
}
