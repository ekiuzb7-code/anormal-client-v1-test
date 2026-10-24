package com.anormal.client.gui;

import com.anormal.client.module.impl.player.NameShower;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Full editor for {@link NameShower}: nickname and rank text on top, every module setting below.
 *
 * <p>The ClickGUI cannot host text boxes, and a panel with only two fields left the toggles
 * unreachable, so this screen carries both: the two text fields plus a scrollable list of the
 * module's own settings (rendered by {@link SettingsPanel}).</p>
 */
public class NameShowerScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 360;

    private static final int BG = 0xF0161619;
    private static final int BORDER = 0xFF3A3A42;
    private static final int BUTTON = 0xFF2C2C34;
    private static final int BUTTON_HOVER = 0xFF3A3A46;
    private static final int ACCENT = 0xFF5AA9FF;
    private static final int TEXT = 0xFFE8E8EC;
    private static final int TEXT_DIM = 0xFF8A8A96;

    private final NameShower module;

    private TextField nameField;
    private TextField rankField;

    /** Common SMP ranks, including the media tags most servers hand out. */
    private static final String[] RANKS = {
            "VIP", "VIP+", "MVP", "MVP+", "MVP++", "LEGEND", "ELITE", "TITAN",
            "MEDIA", "YT", "TWITCH", "TIKTOK", "FAMOUS", "DONATOR", "GOD", "OWNER"
    };

    private int chipX, chipY, chipW, chipH;
    private final java.util.List<int[]> chipRects = new java.util.ArrayList<>();
    private final java.util.List<String> chipLabels = new java.util.ArrayList<>();

    private int px, py;
    private int fieldX, fieldW;
    private int listX, listY, listW, listH;
    private int scroll;
    private int applyX, applyY, applyW, applyH;
    private int closeX, closeY, closeW, closeH;

    public NameShowerScreen(NameShower module) {
        super(Text.literal("NameShower"));
        this.module = module;
    }

    @Override
    protected void init() {
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        fieldX = px + 96;
        fieldW = PANEL_W - 116;

        nameField = new TextField(module.nickname.get(), "your real name", 32);
        nameField.setFocused(true);

        rankField = new TextField(module.rank.get(), "MVP+", 24);

        // Rank chips: two rows of quick picks under the rank box.
        chipX = px + 16;
        chipY = py + 76;
        chipH = 13;
        layoutChips();

        listX = px + 8;
        listY = py + 132;
        listW = PANEL_W - 16;
        listH = PANEL_H - 132 - 42;
        scroll = 0;

        applyW = 70;
        applyH = 18;
        applyX = px + 16;
        applyY = py + PANEL_H - 30;

        closeW = 64;
        closeH = 18;
        closeX = px + PANEL_W - 16 - closeW;
        closeY = applyY;
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x99000000);
        ctx.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, BORDER);
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, BG);
        ctx.fill(px, py, px + PANEL_W, py + 22, 0xFF1D1D22);

        ctx.drawText(textRenderer, "NameShower", px + 16, py + 7, ACCENT, false);

        ctx.drawText(textRenderer, "Nickname", px + 16, py + 33, TEXT_DIM, false);
        nameField.render(ctx, textRenderer, fieldX, py + 28, fieldW, 18);

        ctx.drawText(textRenderer, "Rank", px + 16, py + 57, TEXT_DIM, false);
        rankField.render(ctx, textRenderer, fieldX, py + 52, fieldW, 18);

        // Quick picks: click a chip to drop that rank into the field.
        for (int i = 0; i < chipRects.size(); i++) {
            int[] r = chipRects.get(i);
            boolean hov = mouseX >= r[0] && mouseX <= r[0] + r[2] && mouseY >= r[1] && mouseY <= r[1] + r[3];
            boolean active = chipLabels.get(i).equalsIgnoreCase(rankField.get().trim());
            ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    active ? ACCENT : (hov ? 0xFF3A3A46 : 0xFF26262E));
            ctx.drawText(textRenderer, chipLabels.get(i), r[0] + 4, r[1] + 3,
                    active ? 0xFF0E0E12 : TEXT, false);
        }

        ctx.drawText(textRenderer, "Preview", px + 16, py + 112, TEXT_DIM, false);
        ctx.drawText(textRenderer, buildPreview(), px + 96, py + 112, TEXT, false);

        // Divider before the settings list.
        ctx.fill(px + 8, py + 126, px + PANEL_W - 8, py + 127, BORDER);

        SettingsPanel.render(ctx, textRenderer, module.getSettings(),
                listX, listY, listW, listH, scroll, mouseX, mouseY);

        // Scrollbar hint when the list overflows.
        int contentH = SettingsPanel.contentHeight(module.getSettings());
        if (contentH > listH) {
            int thumbH = Math.max(12, (int) (listH * (listH / (double) contentH)));
            int maxScroll = contentH - listH;
            int thumbY = listY + (int) ((listH - thumbH) * (scroll / (double) maxScroll));
            ctx.fill(px + PANEL_W - 5, listY, px + PANEL_W - 3, listY + listH, 0x33000000);
            ctx.fill(px + PANEL_W - 5, thumbY, px + PANEL_W - 3, thumbY + thumbH, ACCENT);
        }

        drawButton(ctx, applyX, applyY, applyW, applyH, "Apply",
                inRect(mouseX, mouseY, applyX, applyY, applyW, applyH));
        drawButton(ctx, closeX, closeY, closeW, closeH, "Close",
                inRect(mouseX, mouseY, closeX, closeY, closeW, closeH));

        super.render(ctx, mouseX, mouseY, delta);
    }

    private Text buildPreview() {
        String nick = nameField.get();
        if (nick == null || nick.isBlank()) nick = "YourName";

        String rank = rankField.get();
        if (rank == null || rank.isBlank() || !module.showRank.isEnabled()) {
            return Text.literal(nick).styled(s -> s.withColor(module.nameColor.getColor()));
        }

        var rankPart = Text.literal(rank).styled(s -> s
                .withColor(module.rankColor.getColor())
                .withBold(module.boldRank.isEnabled()));
        var namePart = Text.literal(nick).styled(s -> s.withColor(module.nameColor.getColor()));
        var bracket = Text.literal("[").styled(s -> s.withColor(module.rankColor.getColor()));
        var bracketEnd = Text.literal("]").styled(s -> s.withColor(module.rankColor.getColor()));

        return switch (module.rankStyle.getValue()) {
            case "Rank | Name" -> Text.empty().append(rankPart).append(Text.literal(" | ")).append(namePart);
            case "Name [Rank]" -> Text.empty().append(namePart).append(Text.literal(" "))
                    .append(bracket).append(rankPart).append(bracketEnd);
            case "Rank Name" -> Text.empty().append(rankPart).append(Text.literal(" ")).append(namePart);
            default -> Text.empty().append(bracket).append(rankPart).append(bracketEnd)
                    .append(Text.literal(" ")).append(namePart);
        };
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label, boolean hovered) {
        ctx.fill(x, y, x + w, y + h, hovered ? BUTTON_HOVER : BUTTON);
        ctx.fill(x, y, x + w, y + 1, BORDER);
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER);
        ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2,
                y + (h - 8) / 2, TEXT, false);
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ------------------------------------------------------------------
    //  Input - both click signatures, so the panel works whichever one is dispatched
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button(), click);
    }

    /** Kept for older dispatch paths; Screen itself only declares the Click overload. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button,
                new Click(mouseX, mouseY, new MouseInput(button, 0)));
    }

    private boolean handleClick(double rawX, double rawY, int button, Click click) {
        // Some dispatch paths hand over window pixels instead of GUI-scaled coordinates. When the
        // value is outside the screen it can only be a window pixel, so normalise it here rather
        // than letting every hit test silently miss.
        double mouseX = rawX > width ? rawX / client.getWindow().getScaleFactor() : rawX;
        double mouseY = rawY > height ? rawY / client.getWindow().getScaleFactor() : rawY;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (inRect(mx, my, applyX, applyY, applyW, applyH)) {
            apply();
            // Apply saves and drops back into the game - it is the "done" button, not a preview.
            close();
            return true;
        }
        if (inRect(mx, my, closeX, closeY, closeW, closeH)) {
            apply();
            close();
            return true;
        }
        if (SettingsPanel.click(module.getSettings(), mouseX, mouseY,
                listX, listY, listW, listH, scroll, button)) {
            return true;
        }

        // Text fields take focus on click; only one keeps it.
        for (int i = 0; i < chipRects.size(); i++) {
            int[] r = chipRects.get(i);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                rankField.set(chipLabels.get(i));
                module.rank.set(chipLabels.get(i));
                return true;
            }
        }

        if (nameField != null && nameField.click(mouseX, mouseY, fieldX, py + 28, fieldW, 18)) {
            rankField.setFocused(false);
            return true;
        }
        if (rankField != null && rankField.click(mouseX, mouseY, fieldX, py + 52, fieldW, 18)) {
            nameField.setFocused(false);
            return true;
        }
        return super.mouseClicked(click, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int contentH = SettingsPanel.contentHeight(module.getSettings());
        if (contentH > listH) {
            scroll = Math.max(0, Math.min(contentH - listH, scroll - (int) (vertical * SettingsPanel.ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            apply();
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            apply();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_TAB) {
            boolean toRank = nameField.isFocused();
            nameField.setFocused(!toRank);
            rankField.setFocused(toRank);
            return true;
        }
        if (nameField != null && nameField.keyPressed(input)) return true;
        if (rankField != null && rankField.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (nameField != null && nameField.charTyped(input)) return true;
        if (rankField != null && rankField.charTyped(input)) return true;
        return super.charTyped(input);
    }

    /** Writes the text fields back into the module settings. */
    private void apply() {
        if (nameField != null) module.nickname.set(nameField.get());
        if (rankField != null) module.rank.set(rankField.get());
    }

    /** Lays the rank chips out in rows that fit the panel width. */
    private void layoutChips() {
        chipRects.clear();
        chipLabels.clear();
        int x = chipX;
        int y = chipY;
        for (String rank : RANKS) {
            int w = textRenderer.getWidth(rank) + 8;
            if (x + w > px + PANEL_W - 16) {
                x = chipX;
                y += chipH + 3;
            }
            chipRects.add(new int[]{x, y, w, chipH});
            chipLabels.add(rank);
            x += w + 4;
        }
        chipW = PANEL_W;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
