package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only overlay that draws a compact quest-marker guide near the top of the screen: one line per
 * active {@code reach_location} objective in the current dimension, showing the label (in its color), the
 * distance, and a compass bearing toward it. Pure {@link GuiGraphics} font draws — no textures, so it works
 * under any shader pack. This is the always-available fallback for JourneyMap waypoints.
 */
@Mod.EventBusSubscriber(modid = Constants.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MarkerOverlay {

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private MarkerOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("custommissions_markers", (gui, graphics, partialTick, width, height) ->
                render(graphics, width, height));
    }

    private static void render(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        if (ClientMissions.markers().isEmpty()) {
            return;
        }
        var currentDim = mc.level.dimension().location();
        Font font = mc.font;
        Vec3 pos = player.position();

        int y = (int) (height * 0.06f);
        for (ClientMissions.Marker m : ClientMissions.markers()) {
            if (!m.dimension().equals(currentDim)) {
                continue;
            }
            double dx = (m.pos().getX() + 0.5) - pos.x;
            double dy = (m.pos().getY() + 0.5) - pos.y;
            double dz = (m.pos().getZ() + 0.5) - pos.z;
            int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            String line = "» " + m.name() + "  " + dist + "m  " + bearing(dx, dz);
            int color = 0xFF000000 | (m.rgb() & 0xFFFFFF);
            // soft backing for readability over bright scenes
            int w = font.width(line);
            graphics.fill(width / 2 - w / 2 - 3, y - 2, width / 2 + w / 2 + 3, y + font.lineHeight, 0x80000000);
            graphics.drawCenteredString(font, line, width / 2, y, color);
            y += font.lineHeight + 3;
        }
    }

    /** 8-point world compass bearing from a delta (north = -Z, east = +X). */
    private static String bearing(double dx, double dz) {
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) deg += 360;
        int idx = (int) Math.round(deg / 45.0) % 8;
        return COMPASS[idx];
    }
}
