package com.summerbuddies.custommissions.client;

import com.summerbuddies.custommissions.net.MissionNet;
import com.summerbuddies.custommissions.net.SaveDescriptionDraftC2S;
import com.summerbuddies.custommissions.net.SubmitDescriptionC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * "Tell us about yourself" text box. The player's daily missions are authored from this self-description, so
 * the screen explains that and invites a vivid answer. Anti-frustration by design: closing it (Esc / "Later")
 * persists the typed text as a DRAFT server-side, restored next time — only "Send" commits it as final. Navy
 * panel + gold accents to match the {@link MissionsScreen} adventure-log look.
 */
public final class DescribeScreen extends Screen {

    private static final int PANEL_BG = 0xC8101826;
    private static final int PANEL_LINE = 0x66E8C170;
    private static final int GOLD = 0xFFE8C170;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFF8A8A99;

    private static final int CHAR_LIMIT = 1500;
    private static final String INTRO =
            "Tell us about yourself. Your daily missions are written from this — your playstyle, your goals, "
            + "what you love (building, combat, exploring, hoarding loot), and your character's story. "
            + "The more vivid you are, the better your quests.";
    private static final String PLACEHOLDER =
            "e.g. A wandering smith chasing the source of the Corruption. I love deep cave dives and rare ores, "
            + "fight cautiously with a bow, and I'm slowly gearing up to take on the big bosses.";

    private final boolean firstTime;
    private String current;
    private MultiLineEditBox editBox;
    private boolean submitted;

    private int panelX, panelY, panelW, panelH;
    private int hintY;

    public DescribeScreen(String initialText, boolean firstTime) {
        super(Component.literal("Who are you?"));
        this.current = initialText == null ? "" : initialText;
        this.firstTime = firstTime;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, width - 40);
        panelH = Math.min(220, height - 40);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int pad = 12;
        int btnH = 20;
        int btnY = panelY + panelH - btnH - 10; // buttons row pinned to the bottom
        hintY = btnY - 12;                       // hint + counter sit on their own line above the buttons
        int introTop = panelY + 26;
        int introLines = font.split(Component.literal(INTRO), panelW - pad * 2).size();
        int boxTop = introTop + introLines * 10 + 6;
        int boxBottom = hintY - 4;               // edit box ends above the hint line
        int boxW = panelW - pad * 2;
        int boxH = Math.max(40, boxBottom - boxTop);

        editBox = new MultiLineEditBox(font, panelX + pad, boxTop, boxW, boxH,
                Component.literal(PLACEHOLDER), Component.literal("Self-description"));
        editBox.setCharacterLimit(CHAR_LIMIT);
        editBox.setValueListener(s -> current = s);
        editBox.setValue(current);
        addRenderableWidget(editBox);

        int btnW = 96;
        addRenderableWidget(Button.builder(Component.literal("Send"), b -> submit())
                .bounds(panelX + panelW - pad - btnW, btnY, btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("Later"), b -> onClose())
                .bounds(panelX + pad, btnY, btnW, btnH).build());

        setInitialFocus(editBox);
    }

    private void submit() {
        submitted = true;
        MissionNet.toServer(new SubmitDescriptionC2S(current));
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void removed() {
        // Any close that is NOT a submit (Esc, "Later", disconnect) saves a draft — never lose the text.
        if (!submitted) {
            MissionNet.toServer(new SaveDescriptionDraftC2S(current));
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        outline(g, panelX, panelY, panelW, panelH, PANEL_LINE);

        int x = panelX + 12;
        int y = panelY + 12;
        g.drawString(font, firstTime ? "WELCOME, ADVENTURER" : "EDIT YOUR STORY", x, y, GOLD, false);
        y += 14;
        for (FormattedCharSequence line : font.split(Component.literal(INTRO), panelW - 24)) {
            g.drawString(font, line, x, y, TEXT, false);
            y += 10;
        }

        super.render(g, mouseX, mouseY, partial); // edit box + buttons

        String counter = current.length() + " / " + CHAR_LIMIT;
        g.drawString(font, counter, panelX + panelW - 12 - font.width(counter), hintY, DIM, false);
        g.drawString(font, "Esc / Later = save draft", panelX + 12, hintY, DIM, false);
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        // current is preserved via the value listener; re-init rebuilds the box and we restore the text.
        super.resize(mc, w, h);
        if (editBox != null) {
            editBox.setValue(current);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
