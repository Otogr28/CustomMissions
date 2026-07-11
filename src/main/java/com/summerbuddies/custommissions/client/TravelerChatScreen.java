package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.net.MissionNet;
import com.summerbuddies.custommissions.net.TravelerChatC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Talk-to-the-Traveler chat window: a scrolling transcript on top, a single-line input + Send below, and a
 * "thinking" state while the off-process brain ({@code ai.TravelerChat} → Sonnet sidecar) composes a reply.
 * Navy panel + gold accents to match the rest of CustomMissions. The transcript lives in {@link TravelerClient}
 * so it survives closing and reopening within a session.
 */
public final class TravelerChatScreen extends Screen {

    private static final int PANEL_BG = 0xD8101826;
    private static final int PANEL_LINE = 0x66E8C170;
    private static final int GOLD = 0xFFE8C170;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFF8A8A99;
    private static final int AQUA = 0xFF8FC7E8;
    private static final int LINE_H = 10;
    private static final int INPUT_MAX = 256;

    private EditBox input;
    private int panelX, panelY, panelW, panelH;
    private int trTop, trBottom, trX, trW;
    private int scrollFromBottom;
    private int totalLines, visibleRows;

    public TravelerChatScreen() {
        super(Component.literal("Flugel the Traveler"));
    }

    @Override
    protected void init() {
        panelW = Math.min(420, width - 40);
        panelH = Math.min(260, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        trX = panelX + 12;
        trW = panelW - 24;
        trTop = panelY + 34;
        int inputY = panelY + panelH - 26;
        trBottom = inputY - 6;
        visibleRows = Math.max(1, (trBottom - trTop) / LINE_H);

        int sendW = 60;
        int inputW = panelW - 24 - sendW - 6;
        input = new EditBox(font, trX, inputY, inputW, 18, Component.literal("Message"));
        input.setMaxLength(INPUT_MAX);
        input.setHint(Component.literal("Speak to Flugel…"));
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> send())
                .bounds(trX + inputW + 6, inputY - 1, sendW, 20).build());
        setInitialFocus(input);
    }

    private void send() {
        if (TravelerClient.awaiting()) {
            return; // one question at a time
        }
        String msg = input.getValue().strip();
        if (msg.isEmpty()) {
            return;
        }
        TravelerClient.addPlayerMessage(msg);
        MissionNet.toServer(new TravelerChatC2S(msg));
        input.setValue("");
        scrollFromBottom = 0; // jump to the latest
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && input.isFocused()) {
            send();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= trX && mx <= trX + trW && my >= trTop && my <= trBottom) {
            int maxScroll = Math.max(0, totalLines - visibleRows);
            scrollFromBottom = Math.max(0, Math.min(maxScroll, scrollFromBottom + (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(g, panelX, panelY, panelW, panelH, PANEL_LINE);

        g.drawString(font, "FLUGEL THE TRAVELER", panelX + 12, panelY + 12, GOLD, false);
        g.drawString(font, "Whatever you say here, Flugel hears.", panelX + 12, panelY + 23, DIM, false);
        g.fill(panelX + 12, panelY + 32, panelX + panelW - 12, panelY + 33, PANEL_LINE);

        // build wrapped transcript
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (TravelerClient.Line line : TravelerClient.transcript()) {
            int color = line.fromPlayer() ? AQUA : TEXT;
            String prefix = line.fromPlayer() ? "You: " : "Traveler: ";
            for (FormattedCharSequence fcs : font.split(Component.literal(prefix + line.text()), trW)) {
                lines.add(fcs);
                colors.add(color);
            }
        }
        if (TravelerClient.awaiting()) {
            for (FormattedCharSequence fcs : font.split(Component.literal("Flugel is thinking…"), trW)) {
                lines.add(fcs);
                colors.add(DIM);
            }
        }
        totalLines = lines.size();
        int maxScroll = Math.max(0, totalLines - visibleRows);
        if (scrollFromBottom > maxScroll) {
            scrollFromBottom = maxScroll;
        }
        int start = Math.max(0, totalLines - visibleRows - scrollFromBottom);
        int y = trTop;
        for (int i = start; i < totalLines && i < start + visibleRows; i++) {
            g.drawString(font, lines.get(i), trX, y, colors.get(i), false);
            y += LINE_H;
        }
        // scroll hint
        if (maxScroll > 0) {
            String more = scrollFromBottom > 0 ? "▼ scroll" : "▲ scroll";
            g.drawString(font, more, panelX + panelW - 12 - font.width(more), trBottom - 9, DIM, false);
        }

        super.render(g, mouseX, mouseY, partial); // input + Send

        g.drawString(font, "Enter to send · Esc to close", panelX + 12, panelY + panelH - 9, DIM, false);
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
