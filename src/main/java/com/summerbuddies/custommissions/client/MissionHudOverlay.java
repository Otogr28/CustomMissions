package com.summerbuddies.custommissions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.summerbuddies.custommissions.Constants;
import com.summerbuddies.custommissions.net.MissionSyncS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Always-on quest tracker on the LEFT of the screen (BotW-style). Shows the missions the player is
 * <b>tracking</b> (see {@link ClientMissions}); newly-accepted missions are auto-tracked, and the screen's
 * Track button hides a mission from here. Each tracked mission is shown with its objective checklist + live
 * progress. Pure font draws over a translucent backing with a gold accent bar. Hidden when the GUI is hidden
 * or nothing is tracked.
 */
@Mod.EventBusSubscriber(modid = Constants.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MissionHudOverlay {

    private static final int GOLD = 0xFFE8C170;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DONE = 0xFF8FD18A;

    private static final int MAX_MISSIONS = 4;
    private static final int MAX_OBJ_LINES = 5;

    private MissionHudOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("custommissions_hud", (gui, g, partial, w, h) -> render(g, w, h));
    }

    private static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        List<Line> lines = new ArrayList<>();
        int missions = 0;
        for (MissionSyncS2C.Entry e : ClientMissions.snapshot().active()) {
            if (!ClientMissions.isTracked(e.id())) {
                continue;
            }
            if (missions++ >= MAX_MISSIONS) {
                break;
            }
            lines.add(new Line("> " + e.title(), GOLD));
            int shown = 0;
            for (MissionSyncS2C.Obj o : e.objectives()) {
                if (shown++ >= MAX_OBJ_LINES) {
                    break;
                }
                boolean done = o.have() >= o.required();
                String frac = o.required() > 1 ? "  " + o.have() + "/" + o.required() : "";
                lines.add(new Line("   " + (done ? "[x] " : "[ ] ") + o.desc() + frac, done ? DONE : TEXT));
            }
        }
        if (lines.isEmpty()) {
            return;
        }

        Font font = mc.font;
        int maxW = 0;
        for (Line l : lines) {
            maxW = Math.max(maxW, font.width(l.text));
        }
        int lineH = font.lineHeight + 1;
        int totalH = lines.size() * lineH + 6;
        int x = 6;
        int y = Math.max(30, height / 2 - totalH / 2);

        // Player-set HUD scale, pivoted on the left edge at mid-height so it stays left-anchored + centered.
        float s = MissionClientConfig.hudScale();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, height / 2.0, 0);
        pose.scale(s, s, 1f);
        pose.translate(-x, -(height / 2.0), 0);

        g.fill(x - 4, y - 4, x + maxW + 4, y + totalH - 4, 0x73000000);
        g.fill(x - 4, y - 4, x - 2, y + totalH - 4, 0xC0E8C170); // gold accent bar

        int cy = y;
        for (Line l : lines) {
            g.drawString(font, l.text, x, cy, l.color, true);
            cy += lineH;
        }
        pose.popPose();
    }

    private record Line(String text, int color) {}
}
