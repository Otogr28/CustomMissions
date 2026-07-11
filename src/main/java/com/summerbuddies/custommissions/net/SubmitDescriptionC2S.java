package com.summerbuddies.custommissions.net;

import com.summerbuddies.custommissions.ai.MissionContextExporter;
import com.summerbuddies.custommissions.intro.IntroCutscene;
import com.summerbuddies.custommissions.state.PlayerMissionStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the player pressed "Send" on the self-description screen. Stored persistently as their
 * final description, clears any draft, and re-exports the player's AI context so the next daily generation
 * reflects it.
 */
public record SubmitDescriptionC2S(String text) {

    /** Hard server-side cap, regardless of what the client sends. */
    public static final int MAX_LEN = 2000;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(text);
    }

    public static SubmitDescriptionC2S decode(FriendlyByteBuf buf) {
        return new SubmitDescriptionC2S(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer sender = c.getSender();
            if (sender == null) {
                return;
            }
            String clean = clamp(text);
            PlayerMissionStore.update(sender, pm -> {
                pm.setDescription(clean);
                pm.setDraftDescription("");
            });
            MissionContextExporter.export(sender);
            sender.sendSystemMessage(Component.literal("Your story was saved — your missions will reflect it.")
                    .withStyle(ChatFormatting.GOLD));
            // First close of the describe screen for a brand-new player → play the intro cutscene (once).
            IntroCutscene.onDescribeClosed(sender);
        });
        c.setPacketHandled(true);
    }

    static String clamp(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() > MAX_LEN ? t.substring(0, MAX_LEN) : t;
    }
}
