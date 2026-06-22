package com.summerbuddies.custommissions.client;

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
 * Always-on quest tracker on the LEFT of the screen (BotW-style). Lists the active missions; the
 * highlighted/tracked one (set via the {@code M} screen's Track button, default = first active) is expanded
 * with its objective checklist + live progress, the rest are collapsed to a title + fraction. Pure font
 * draws over a translucent backing with a gold accent bar. Hidden when the GUI is hidden or no active
 * missions exist (and automatically while a full screen is open).
 */
@Mod.EventBusSubscriber(modid = Constants.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MissionHudOverlay {

    private static final int GOLD = 0xFFE8C170;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFFB8B8C2;
    private static final int DONE = 0xFF8FD18A;
    private static final int AQUA = 0xFF8FC7E8;
    private static final int GREEN = 0xFF9FD98A;

    private static final int MAX_MISSIONS = 4;
    private static final int MAX_OBJ_LINES = 6;

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
        List<MissionSyncS2C.Entry> active = ClientMissions.snapshot().active();
        if (active.isEmpty()) {
            return;
        }
        Font font = mc.font;

        String tracked = effectiveTracked(active);
        List<Line> lines = new ArrayList<>();
        int count = Math.min(active.size(), MAX_MISSIONS);
        for (int i = 0; i < count; i++) {
            MissionSyncS2C.Entry e = active.get(i);
            boolean isTracked = e.id().equals(tracked);
            if (isTracked) {
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
            } else {
                lines.add(new Line("> " + e.title() + "  (" + doneCount(e) + "/" + e.objectives().size() + ")",
                        categoryColor(e.category())));
            }
        }

        int maxW = 0;
        for (Line l : lines) {
            maxW = Math.max(maxW, font.width(l.text));
        }
        int lineH = font.lineHeight + 1;
        int totalH = lines.size() * lineH + 6;
        int x = 6;
        int y = Math.max(30, height / 2 - totalH / 2);

        g.fill(x - 4, y - 4, x + maxW + 4, y + totalH - 4, 0x73000000);
        g.fill(x - 4, y - 4, x - 2, y + totalH - 4, 0xC0E8C170); // gold accent bar

        int cy = y;
        for (Line l : lines) {
            g.drawString(font, l.text, x, cy, l.color, true);
            cy += lineH;
        }
    }

    private static String effectiveTracked(List<MissionSyncS2C.Entry> active) {
        String tracked = ClientMissions.trackedId();
        if (tracked != null) {
            for (MissionSyncS2C.Entry e : active) {
                if (e.id().equals(tracked)) {
                    return tracked;
                }
            }
        }
        return active.get(0).id();
    }

    private static int doneCount(MissionSyncS2C.Entry e) {
        int d = 0;
        for (MissionSyncS2C.Obj o : e.objectives()) {
            if (o.have() >= o.required()) {
                d++;
            }
        }
        return d;
    }

    private static int categoryColor(int cat) {
        return switch (cat) {
            case 1 -> GOLD;
            case 0 -> GREEN;
            default -> AQUA;
        };
    }

    private record Line(String text, int color) {}
}
