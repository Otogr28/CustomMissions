package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.net.GossipSubmitC2S;
import com.summerbuddies.custommissions.net.MissionNet;
import com.summerbuddies.custommissions.net.OpenGossipS2C;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * The market gossip's "I've got gossip" window. Two phases: (1) pick a target from the roster — each row
 * shows the player's face (their CustomSkinLoader/Mojang skin, fetched by {@link GuiSkinCache}) and name,
 * self first; (2) write up to 200 chars and Send. The tip goes to the server, where Sonnet judges how
 * credible it sounds and nudges reputation. Navy + gold to match the rest of CustomMissions.
 */
public final class GossipScreen extends Screen {

    private enum Phase { SELECT, WRITE }

    private static final int PANEL_BG = 0xC8101826;
    private static final int PANEL_LINE = 0x66E8C170;
    private static final int GOLD = 0xFFE8C170;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFF8A8A99;
    private static final int HOVER_BG = 0x20FFFFFF;
    private static final int ROW_H = 16;

    private final List<OpenGossipS2C.Target> targets;
    private Phase phase = Phase.SELECT;
    private OpenGossipS2C.Target selected;
    private String draft = "";
    private MultiLineEditBox editBox;

    private int panelX, panelY, panelW, panelH;
    private int listTop, listBottom, visibleRows, scroll, hintY;

    public GossipScreen(List<OpenGossipS2C.Target> targets) {
        super(Component.literal("Gossip"));
        this.targets = targets;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, width - 40);
        panelH = Math.min(240, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        if (phase == Phase.WRITE && selected != null) {
            int pad = 12;
            int bh = 20;
            int by = panelY + panelH - bh - 8;
            hintY = by - 12;
            int boxTop = panelY + 46;
            int boxBottom = hintY - 4;
            editBox = new MultiLineEditBox(font, panelX + pad, boxTop, panelW - pad * 2,
                    Math.max(40, boxBottom - boxTop),
                    Component.literal("What's the word on " + selected.name() + "? Be specific to be believed."),
                    Component.literal("Gossip"));
            editBox.setCharacterLimit(GossipSubmitC2S.MAX_LEN);
            editBox.setValueListener(s -> draft = s);
            editBox.setValue(draft);
            addRenderableWidget(editBox);
            int bw = 90;
            addRenderableWidget(Button.builder(Component.literal("Send"), b -> send())
                    .bounds(panelX + panelW - pad - bw, by, bw, bh).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
                phase = Phase.SELECT;
                rebuildWidgets();
            }).bounds(panelX + pad, by, bw, bh).build());
            setInitialFocus(editBox);
        } else {
            listTop = panelY + 40;
            listBottom = panelY + panelH - 16;
            visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);
        }
    }

    private void send() {
        String text = draft.strip();
        if (selected == null || text.isEmpty()) {
            return;
        }
        MissionNet.toServer(new GossipSubmitC2S(selected.uuid(), selected.name(), text));
        onClose();
    }

    // ---- render ----------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(g, panelX, panelY, panelW, panelH, PANEL_LINE);
        if (phase == Phase.WRITE && selected != null) {
            renderWrite(g, mouseX, mouseY, partial);
        } else {
            renderSelect(g, mouseX, mouseY);
        }
    }

    private void renderSelect(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "I'VE GOT GOSSIP", panelX + 12, panelY + 12, GOLD, false);
        g.drawString(font, "About whom? Pick someone:", panelX + 12, panelY + 24, DIM, false);
        g.fill(panelX + 12, panelY + 36, panelX + panelW - 12, panelY + 37, PANEL_LINE);

        int y = listTop + 2;
        for (int i = scroll; i < targets.size() && i < scroll + visibleRows; i++) {
            OpenGossipS2C.Target t = targets.get(i);
            int rowY = y + (i - scroll) * ROW_H;
            boolean hover = mouseX >= panelX + 8 && mouseX <= panelX + panelW - 8
                    && mouseY >= rowY - 2 && mouseY < rowY + ROW_H - 4;
            if (hover) {
                g.fill(panelX + 8, rowY - 2, panelX + panelW - 8, rowY + ROW_H - 4, HOVER_BG);
            }
            PlayerFaceRenderer.draw(g, skinFor(t), panelX + 12, rowY - 1, 10);
            String label = t.name() + (isSelf(t) ? "  (you)" : "");
            g.drawString(font, label, panelX + 28, rowY + 1, isSelf(t) ? GOLD : TEXT, false);
        }
        if (targets.size() > visibleRows) {
            String more = "scroll for more (" + targets.size() + ")";
            g.drawString(font, more, panelX + panelW - 12 - font.width(more), panelY + panelH - 11, DIM, false);
        }
        g.drawString(font, "Esc to cancel", panelX + 12, panelY + panelH - 11, DIM, false);
    }

    private void renderWrite(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.drawString(font, "TELLING ON", panelX + 12, panelY + 12, GOLD, false);
        PlayerFaceRenderer.draw(g, skinFor(selected), panelX + 12, panelY + 24, 16);
        g.drawString(font, selected.name(), panelX + 34, panelY + 28, TEXT, false);
        g.fill(panelX + 12, panelY + 44, panelX + panelW - 12, panelY + 45, PANEL_LINE);

        super.render(g, mouseX, mouseY, partial); // edit box + buttons

        String counter = draft.length() + " / " + GossipSubmitC2S.MAX_LEN;
        g.drawString(font, counter, panelX + panelW - 12 - font.width(counter), hintY, DIM, false);
        g.drawString(font, "Hard gossip lands; cheap insults don't.", panelX + 12, hintY, DIM, false);
    }

    // ---- input -----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (phase == Phase.SELECT && button == 0
                && mx >= panelX + 8 && mx <= panelX + panelW - 8 && my >= listTop && my <= listBottom) {
            int row = (int) ((my - (listTop + 2) + 2) / ROW_H) + scroll;
            if (row >= 0 && row < targets.size()) {
                selected = targets.get(row);
                draft = "";
                phase = Phase.WRITE;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (phase == Phase.SELECT) {
            int maxScroll = Math.max(0, targets.size() - visibleRows);
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private ResourceLocation skinFor(OpenGossipS2C.Target t) {
        ResourceLocation skin = GuiSkinCache.byUsername(t.name());
        if (skin != null) {
            return skin;
        }
        UUID u;
        try {
            u = UUID.fromString(t.uuid());
        } catch (IllegalArgumentException e) {
            u = Util.NIL_UUID;
        }
        return DefaultPlayerSkin.getDefaultSkin(u);
    }

    private boolean isSelf(OpenGossipS2C.Target t) {
        return minecraft != null && minecraft.player != null
                && minecraft.player.getUUID().toString().equals(t.uuid());
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
