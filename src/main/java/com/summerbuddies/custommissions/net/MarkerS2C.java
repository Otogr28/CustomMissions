package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.client.ClientMissions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: add or remove a quest marker keyed by {@code missionId}. When {@code show} is false the
 * client drops the marker; the other fields are then ignored. The client renders an on-screen compass arrow
 * + distance and (if JourneyMap is present) a real map waypoint.
 */
public record MarkerS2C(boolean show, String missionId, String dimension, int x, int y, int z,
                        String name, int rgb) {

    /** Convenience for a removal packet. */
    public static MarkerS2C remove(String missionId) {
        return new MarkerS2C(false, missionId, "", 0, 0, 0, "", 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(show);
        buf.writeUtf(missionId);
        buf.writeUtf(dimension);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        buf.writeUtf(name);
        buf.writeInt(rgb);
    }

    public static MarkerS2C decode(FriendlyByteBuf buf) {
        return new MarkerS2C(buf.readBoolean(), buf.readUtf(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientMissions.applyMarker(this));
        ctx.get().setPacketHandled(true);
    }
}
