package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.Constants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's network channel. Registered from the mod constructor. All packets are server → client. */
public final class MissionNet {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private MissionNet() {}

    public static void register() {
        CHANNEL.messageBuilder(MarkerS2C.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MarkerS2C::encode).decoder(MarkerS2C::decode)
                .consumerMainThread(MarkerS2C::handle).add();
    }

    /** Send a packet to a single player. */
    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
