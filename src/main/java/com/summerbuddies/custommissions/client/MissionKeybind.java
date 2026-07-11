package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.net.MissionNet;
import com.summerbuddies.custommissions.net.RequestMissionsC2S;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The {@code M} keybinding that opens the adventure-log screen. On press (when no other screen is open) it
 * requests a fresh snapshot from the server; the screen pops open when that snapshot arrives
 * ({@link ClientMissions#applySnapshot}), so it always shows current data. Registered by
 * {@link MissionClientSetup}.
 */
@Mod.EventBusSubscriber(modid = Constants.MODID, value = Dist.CLIENT)
public final class MissionKeybind {

    public static final KeyMapping OPEN = new KeyMapping(
            "key.custommissions.open_missions", GLFW.GLFW_KEY_M, "key.categories.custommissions");

    private MissionKeybind() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                ClientMissions.requestOpen();
                MissionNet.toServer(new RequestMissionsC2S());
            }
        }
        ClientMissions.pollDescribe(mc);
        TravelerClient.pollOpen(mc);
    }
}
