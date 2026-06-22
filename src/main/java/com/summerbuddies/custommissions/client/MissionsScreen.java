package com.summerbuddies.custommissions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.summerbuddies.custommissions.net.MissionSyncS2C;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The {@code M} adventure-log screen, styled after Breath of the Wild's quest menu: category tabs
 * (Active / Available / Completed), a left list of missions, and a right panel with the selected mission's
 * title, giver, description, lore, objective checklist (with live progress) and rewards — plus Accept /
 * Abandon actions. All custom-drawn ({@link GuiGraphics} fills + font), navy panels + gold accents, so it
 * reads cleanly under any shader pack. Data comes from {@link ClientMissions#snapshot()}.
 */
public final class MissionsScreen extends Screen {

    private enum Tab {
        ACTIVE("Active"), AVAILABLE("Available"), COMPLETED("Completed");
        final String label;
        Tab(String l) { this.label = l; }
    }

    // palette
    private static final int PANEL_BG = 0xC8101826;
    private static final int PANEL_LINE = 0x66E8C170;
    private static final int SEL_BG = 0x40E8C170;
    private static final int HOVER_BG = 0x20FFFFFF;
    private static final int GOLD = 0xFFE8C170;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFF8A8A99;
    private static final int DONE = 0xFF8FD18A;
    private static final int AQUA = 0xFF8FC7E8;
    private static final int GREEN = 0xFF9FD98A;

    private static final int ROW_H = 16;

    private static Tab tab = Tab.ACTIVE;
    private static boolean tabChosen;

    private int selected;
    private int scroll;

    // geometry (set in init)
    private int listX, listW, detailX, detailW, contentTop, contentBottom, visibleRows;
    // hit rects set during render, read in mouseClicked
    private final int[] tabStart = new int[3];
    private final int[] tabEnd = new int[3];
    private int btnX, btnY, btnW, btnH;
    private boolean btnActive;
    private int trkX, trkY, trkW, trkH;
    private boolean trkActive;

    public MissionsScreen() {
        super(Component.literal("Adventure Log"));
    }

    @Override
    protected void init() {
        int margin = 16;
        contentTop = 52;
        contentBottom = height - 16;
        listX = margin;
        listW = Math.max(120, (int) (width * 0.32f));
        detailX = listX + listW + 12;
        detailW = width - margin - detailX;
        visibleRows = Math.max(1, (contentBottom - contentTop) / ROW_H);

        if (!tabChosen) {
            tab = firstNonEmptyTab();
            tabChosen = true;
        }
        clampSelection();
    }

    private Tab firstNonEmptyTab() {
        if (!snap().active().isEmpty()) return Tab.ACTIVE;
        if (!snap().available().isEmpty()) return Tab.AVAILABLE;
        if (!snap().completed().isEmpty()) return Tab.COMPLETED;
        return Tab.ACTIVE;
    }

    private MissionSyncS2C snap() {
        return ClientMissions.snapshot();
    }

    private List<MissionSyncS2C.Entry> current() {
        return switch (tab) {
            case ACTIVE -> snap().active();
            case AVAILABLE -> snap().available();
            case COMPLETED -> snap().completed();
        };
    }

    private void clampSelection() {
        int n = current().size();
        if (selected >= n) selected = Math.max(0, n - 1);
        int maxScroll = Math.max(0, n - visibleRows);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
    }

    // ---- render ----------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        Font font = this.font;

        // title
        g.drawString(font, "ADVENTURE LOG", listX, 18, GOLD, false);
        g.drawString(font, "Missions", listX, 30, DIM, false);

        // tabs (top-right of the list area)
        renderTabs(g, font, mouseX, mouseY);

        // panels
        panel(g, listX, contentTop, listW, contentBottom - contentTop);
        panel(g, detailX, contentTop, detailW, contentBottom - contentTop);

        renderList(g, font, mouseX, mouseY);
        renderDetail(g, font, mouseX, mouseY);

        // footer hint
        g.drawString(font, "M / Esc to close", listX, height - 12, DIM, false);
    }

    private void renderTabs(GuiGraphics g, Font font, int mouseX, int mouseY) {
        int x = listX;
        int y = 40;
        Tab[] tabs = Tab.values();
        int[] counts = {snap().active().size(), snap().available().size(), snap().completed().size()};
        for (int i = 0; i < tabs.length; i++) {
            String label = tabs[i].label + " " + counts[i];
            int w = font.width(label);
            tabStart[i] = x;
            tabEnd[i] = x + w;
            boolean sel = tab == tabs[i];
            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 9;
            g.drawString(font, label, x, y, sel ? GOLD : (hover ? TEXT : DIM), false);
            if (sel) {
                g.fill(x, y + 10, x + w, y + 11, GOLD);
            }
            x += w + 14;
        }
    }

    private void renderList(GuiGraphics g, Font font, int mouseX, int mouseY) {
        List<MissionSyncS2C.Entry> list = current();
        int y = contentTop + 4;
        int rowX = listX + 6;
        int rowW = listW - 12;
        for (int i = scroll; i < list.size() && i < scroll + visibleRows; i++) {
            MissionSyncS2C.Entry e = list.get(i);
            int rowY = y + (i - scroll) * ROW_H;
            boolean hover = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= rowY - 2 && mouseY < rowY + ROW_H - 2;
            if (i == selected) {
                g.fill(listX + 2, rowY - 3, listX + listW - 2, rowY + ROW_H - 5, SEL_BG);
                g.fill(listX + 2, rowY - 3, listX + 4, rowY + ROW_H - 5, GOLD); // selection bar
            } else if (hover) {
                g.fill(listX + 2, rowY - 3, listX + listW - 2, rowY + ROW_H - 5, HOVER_BG);
            }
            String glyph = "> ";
            int color = tab == Tab.COMPLETED ? DIM : categoryColor(e.category());
            String title = trunc(font, glyph + e.title(), rowW);
            g.drawString(font, title, rowX, rowY, i == selected ? GOLD : color, false);
            // progress fraction for active
            if (tab == Tab.ACTIVE) {
                String frac = doneCount(e) + "/" + e.objectives().size();
                int fw = font.width(frac);
                g.drawString(font, frac, listX + listW - 8 - fw, rowY, DIM, false);
            }
        }
        // scrollbar
        if (list.size() > visibleRows) {
            int trackH = contentBottom - contentTop - 8;
            int barH = Math.max(12, trackH * visibleRows / list.size());
            int maxScroll = list.size() - visibleRows;
            int barY = contentTop + 4 + (maxScroll == 0 ? 0 : (trackH - barH) * scroll / maxScroll);
            g.fill(listX + listW - 4, contentTop + 4, listX + listW - 2, contentBottom - 4, 0x40FFFFFF);
            g.fill(listX + listW - 4, barY, listX + listW - 2, barY + barH, GOLD);
        }
    }

    private void renderDetail(GuiGraphics g, Font font, int mouseX, int mouseY) {
        btnActive = false;
        List<MissionSyncS2C.Entry> list = current();
        int x = detailX + 10;
        int w = detailW - 20;
        int y = contentTop + 10;
        if (list.isEmpty() || selected >= list.size()) {
            g.drawString(font, "No missions here yet.", x, y, DIM, false);
            return;
        }
        MissionSyncS2C.Entry e = list.get(selected);

        // title (scaled up)
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(1.5f, 1.5f, 1f);
        g.drawString(font, trunc(font, e.title(), (int) (w / 1.5f)), 0, 0, GOLD, false);
        pose.popPose();
        y += 18;

        // category + giver
        String sub = categoryLabel(e.category());
        if (!e.giver().isEmpty()) sub += "  -  from " + e.giver();
        g.drawString(font, sub, x, y, categoryColor(e.category()), false);
        y += 12;
        g.fill(x, y, x + w, y + 1, PANEL_LINE);
        y += 7;

        // description
        if (!e.description().isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.literal(e.description()), w)) {
                g.drawString(font, line, x, y, TEXT, false);
                y += 11;
            }
            y += 3;
        }
        // lore (italic-ish, dim)
        if (!e.lore().isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.literal("\"" + e.lore() + "\""), w)) {
                g.drawString(font, line, x, y, DIM, false);
                y += 11;
            }
            y += 3;
        }

        // objectives
        g.drawString(font, "OBJECTIVES", x, y, GOLD, false);
        y += 12;
        for (MissionSyncS2C.Obj o : e.objectives()) {
            boolean done = o.have() >= o.required();
            String box = done ? "[x] " : "[ ] ";
            String suffix = o.required() > 1 ? "  " + o.have() + "/" + o.required() : "";
            String line = trunc(font, box + o.desc() + suffix, w);
            g.drawString(font, line, x, y, done ? DONE : TEXT, false);
            y += 11;
        }
        y += 4;

        // rewards
        if (!e.rewards().isEmpty()) {
            g.drawString(font, "REWARDS", x, y, GOLD, false);
            y += 12;
            for (String r : e.rewards()) {
                g.drawString(font, trunc(font, "- " + r, w), x, y, TEXT, false);
                y += 11;
            }
        }

        // action buttons (bottom of detail panel)
        btnActive = false;
        trkActive = false;
        int bw = 78;
        int bh = 18;
        int by = contentBottom - 10 - bh;
        if (tab == Tab.AVAILABLE) {
            btnX = detailX + detailW - 10 - bw;
            btnY = by;
            btnW = bw;
            btnH = bh;
            btnActive = true;
            drawBtn(g, font, btnX, btnY, btnW, btnH, "Accept", GOLD, mouseX, mouseY);
        } else if (tab == Tab.ACTIVE) {
            btnX = detailX + detailW - 10 - bw;
            btnY = by;
            btnW = bw;
            btnH = bh;
            btnActive = true;
            drawBtn(g, font, btnX, btnY, btnW, btnH, "Abandon", 0xFFD18A8A, mouseX, mouseY);

            boolean isTracked = ClientMissions.isTracked(e.id());
            trkX = btnX - 8 - bw;
            trkY = by;
            trkW = bw;
            trkH = bh;
            trkActive = true;
            drawBtn(g, font, trkX, trkY, trkW, trkH, isTracked ? "Tracked" : "Track",
                    isTracked ? DONE : GOLD, mouseX, mouseY);
        }
    }

    private void drawBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String label, int border,
                         int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g.fill(x, y, x + w, y + h, hover ? 0x50E8C170 : 0x30FFFFFF);
        outline(g, x, y, w, h, border);
        int lw = font.width(label);
        g.drawString(font, label, x + (w - lw) / 2, y + (h - 8) / 2, hover ? GOLD : TEXT, false);
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // tabs
            for (int i = 0; i < 3; i++) {
                if (mx >= tabStart[i] && mx <= tabEnd[i] && my >= 40 && my <= 50) {
                    tab = Tab.values()[i];
                    selected = 0;
                    scroll = 0;
                    clampSelection();
                    return true;
                }
            }
            // list rows
            List<MissionSyncS2C.Entry> list = current();
            if (mx >= listX && mx <= listX + listW && my >= contentTop && my <= contentBottom) {
                int row = (int) ((my - (contentTop + 4) + 3) / ROW_H) + scroll;
                if (row >= 0 && row < list.size()) {
                    selected = row;
                    return true;
                }
            }
            // track button (active tab) — toggles HUD visibility, client-side, no server round-trip
            if (trkActive && mx >= trkX && mx <= trkX + trkW && my >= trkY && my <= trkY + trkH
                    && selected < list.size()) {
                ClientMissions.toggleTracked(list.get(selected).id());
                return true;
            }
            // accept / abandon
            if (btnActive && mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH
                    && selected < list.size()) {
                String id = list.get(selected).id();
                runCommand((tab == Tab.AVAILABLE ? "mission accept " : "mission abandon ") + id);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= listX && mx <= listX + listW) {
            int n = current().size();
            int maxScroll = Math.max(0, n - visibleRows);
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void runCommand(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Re-lay out from the latest {@link ClientMissions#snapshot()} (called when a fresh snapshot arrives). */
    public void refresh() {
        rebuildWidgets();
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        outline(g, x, y, w, h, PANEL_LINE);
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private String trunc(Font font, String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, w - font.width("...")) + "...";
    }

    private int doneCount(MissionSyncS2C.Entry e) {
        int d = 0;
        for (MissionSyncS2C.Obj o : e.objectives()) {
            if (o.have() >= o.required()) d++;
        }
        return d;
    }

    private static int categoryColor(int cat) {
        return switch (cat) {
            case 1 -> GOLD;   // primary / main
            case 0 -> GREEN;  // daily
            default -> AQUA;  // secondary / side
        };
    }

    private static String categoryLabel(int cat) {
        return switch (cat) {
            case 1 -> "Main Quest";
            case 0 -> "Daily";
            default -> "Side Quest";
        };
    }
}
