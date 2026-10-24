package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * A search bar with a magnifying glass icon and placeholder text.
 * Disabled until modules exist (per spec), but functional.
 *
 * v1.12.1: Added a {@code dark} flag — when true the search bar renders
 * in the dark Lucid palette to match the rest of the ClickGUI.
 *
 * Pill-shaped, optional disabled state, blinking caret when focused.
 *
 * All drawing goes through {@link RenderUtils} which delegates to the
 * standard 1.21.11 yarn-mapped {@link DrawContext#fill} /
 * {@link DrawContext#drawText} / {@link TextRenderer#getWidth} APIs.
 */
public class SearchBar extends Component {
    private String text = "";
    private String placeholder;
    private boolean disabled;
    private final boolean dark;
    private int caretBlink;
    private Runnable onChange;

    public SearchBar(int x, int y, int w, int h, String placeholder) {
        this(x, y, w, h, placeholder, false);
    }

    public SearchBar(int x, int y, int w, int h, String placeholder, boolean dark) {
        super(x, y, w, h);
        this.placeholder = placeholder;
        this.disabled = false;
        this.dark = dark;
    }

    public void setDisabled(boolean d) { this.disabled = d; }
    public void setOnChange(Runnable r) { this.onChange = r; }

    @Override
    public void update(long dtMs) {
        super.update(dtMs);
        caretBlink = (caretBlink + (int) dtMs) % 1100;
    }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        float h = easedHover();

        // Theme-resolved colors.
        int panel  = dark ? Colors.DARK_PANEL       : Colors.PANEL;
        int card   = dark ? Colors.DARK_CARD_HOVER  : Colors.CARD;
        int border = dark ? Colors.DARK_BORDER      : Colors.BORDER;
        int borderStrong = dark ? Colors.DARK_BORDER_STRONG : Colors.BORDER_STRONG;
        int accent = dark ? Colors.DARK_ACCENT       : Colors.ACCENT;
        int textPrimary = dark ? Colors.DARK_TEXT_PRIMARY  : Colors.TEXT_PRIMARY;
        int textMuted   = dark ? Colors.DARK_TEXT_MUTED    : Colors.TEXT_MUTED;
        int textSecondary = dark ? Colors.DARK_TEXT_SECONDARY : Colors.TEXT_SECONDARY;

        int bg = disabled ? panel : (focused ? card : Colors.lerp(panel, card, h));
        int bd = disabled ? border : (focused ? accent : Colors.lerp(border, borderStrong, h));

        RenderUtils.drawRoundedRect(ctx, x, y, width, height, LucidTheme.RADIUS_PILL, bg);
        RenderUtils.drawRoundedRectBorder(ctx, x, y, width, height, LucidTheme.RADIUS_PILL, bd, 1);

        // Search icon
        int iconSize = 14;
        int iconX = x + 12;
        int iconY = y + (height - iconSize) / 2;
        int iconColor = disabled ? textMuted : textSecondary;
        Icons.drawSearch(ctx, iconX, iconY, iconSize, iconColor);

        int padX = 12 + iconSize + 8;
        int textY = RenderUtils.centerTextY(tr, y, height);
        String display = text;
        int color = disabled ? textMuted : textPrimary;
        if (text.isEmpty()) {
            display = placeholder;
            color = textMuted;
        }
        int maxW = width - padX - 14;
        while (!display.isEmpty() && tr.getWidth(display) > maxW) {
            display = display.substring(0, display.length() - 1);
        }
        RenderUtils.drawText(ctx, tr, display, x + padX, textY, color, false);

        // Caret
        if (!disabled && focused && caretBlink < 550 && !text.isEmpty()) {
            int cx = x + padX + tr.getWidth(text);
            ctx.fill(cx, y + 6, cx + 1, y + height - 6, accent);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || disabled) return false;
        if (button == 0 && contains(mx, my)) {
            focused = true;
            return true;
        }
        focused = false;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || disabled) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
            if (onChange != null) onChange.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { focused = false; return true; }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused || disabled) return false;
        if (chr >= 32 && chr < 127 && text.length() < 64) {
            text += chr;
            if (onChange != null) onChange.run();
            return true;
        }
        return false;
    }

    public String getText() { return text; }
    public void clear() { text = ""; }
    public boolean isDisabled() { return disabled; }
    public boolean isFocused() { return focused; }
}
