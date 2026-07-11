package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.intro.IntroCutscene;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: persist the in-progress self-description as a DRAFT (sent when the screen is closed
 * without submitting). This is the anti-frustration guarantee — an accidental Esc never loses what the
 * player typed; the draft is restored next time the screen opens, until they finally press "Send".
 */
public record SaveDescriptionDraftC2S(String text) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(text);
    }

    public static SaveDescriptionDraftC2S decode(FriendlyByteBuf buf) {
        return new SaveDescriptionDraftC2S(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender != null) {
                String clean = SubmitDescriptionC2S.clamp(text);
                PlayerMissionStore.update(sender, pm -> pm.setDraftDescription(clean));
                // Closing the screen (Esc / "Later") counts as the first close → play the intro once.
                IntroCutscene.onDescribeClosed(sender);
            }
        });
        c.setPacketHandled(true);
    }
}
