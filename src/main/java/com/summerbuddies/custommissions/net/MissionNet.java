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

        CHANNEL.messageBuilder(MissionSyncS2C.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MissionSyncS2C::encode).decoder(MissionSyncS2C::decode)
                .consumerMainThread(MissionSyncS2C::handle).add();

        CHANNEL.messageBuilder(RequestMissionsC2S.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestMissionsC2S::encode).decoder(RequestMissionsC2S::decode)
                .consumerMainThread(RequestMissionsC2S::handle).add();

        CHANNEL.messageBuilder(OpenDescribeS2C.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDescribeS2C::encode).decoder(OpenDescribeS2C::decode)
                .consumerMainThread(OpenDescribeS2C::handle).add();

        CHANNEL.messageBuilder(SubmitDescriptionC2S.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SubmitDescriptionC2S::encode).decoder(SubmitDescriptionC2S::decode)
                .consumerMainThread(SubmitDescriptionC2S::handle).add();

        CHANNEL.messageBuilder(SaveDescriptionDraftC2S.class, 5, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveDescriptionDraftC2S::encode).decoder(SaveDescriptionDraftC2S::decode)
                .consumerMainThread(SaveDescriptionDraftC2S::handle).add();

        CHANNEL.messageBuilder(OpenTravelerChatS2C.class, 6, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenTravelerChatS2C::encode).decoder(OpenTravelerChatS2C::decode)
                .consumerMainThread(OpenTravelerChatS2C::handle).add();

        CHANNEL.messageBuilder(TravelerChatC2S.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TravelerChatC2S::encode).decoder(TravelerChatC2S::decode)
                .consumerMainThread(TravelerChatC2S::handle).add();

        CHANNEL.messageBuilder(TravelerReplyS2C.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TravelerReplyS2C::encode).decoder(TravelerReplyS2C::decode)
                .consumerMainThread(TravelerReplyS2C::handle).add();

        CHANNEL.messageBuilder(OpenGossipS2C.class, 9, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenGossipS2C::encode).decoder(OpenGossipS2C::decode)
                .consumerMainThread(OpenGossipS2C::handle).add();

        CHANNEL.messageBuilder(GossipSubmitC2S.class, 10, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GossipSubmitC2S::encode).decoder(GossipSubmitC2S::decode)
                .consumerMainThread(GossipSubmitC2S::handle).add();
    }

    /** Send a packet to a single player. */
    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    /** Send a packet to the server (client → server). */
    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
