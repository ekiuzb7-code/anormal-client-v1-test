package com.lucidclient.ui.component;

import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * A modern text input with rounded corners, placeholder text, and a blinking caret.
 *
 * keyPressed handles Backspace / Delete / Escape.
 * charTyped handles character input (printable ASCII).
 *
 * All drawing goes through {@link RenderUtils} which delegates to the
 * standard 1.21.11 yarn-mapped {@link DrawContext#fill} /
 * {@link DrawContext#drawText} / {@link TextRenderer#getWidth} /
 * {@link TextRenderer#fontHeight} APIs.
 */
public class Textbox extends Component {
    private String text = "";
    private String placeholder;
    private int maxLength = 256;
    private int caretBlink;
    private Runnable onChange;

    public Textbox(int x, int y, int w, int h, String placeholder) {
        super(x, y, w, h);
        this.placeholder = placeholder;
        this.caretBlink = 0;
    }

    public void setOnChange(Runnable r) { this.onChange = r; }

    @Override
    public void update(long dtMs) {
        super.update(dtMs);
        caretBlink = (caretBlink + (int) dtMs) % 1100; // ~1.1s blink cycle
    }

    @Override
    public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        float h = easedHover();
        // Aurora glass input — accent glow when focused.
        int bg = focused
            ? Colors.withAlpha(Colors.AURORA_GLASS, 255)
            : Colors.withAlpha(0xCC14141F, 220);
        int border = focused
            ? Colors.AURORA_ACCENT_HOVER
            : Colors.lerp(Colors.AURORA_BORDER,
                          Colors.AURORA_BORDER_STRONG, h);

        if (focused) {
            RenderUtils.drawGlowBorder(ctx, x, y, width, height, LucidTheme.RADIUS_SMALL, Colors.AURORA_ACCENT, 255);
        }
        RenderUtils.drawRoundedRect(ctx, x, y, width, height, LucidTheme.RADIUS_SMALL, bg);
        RenderUtils.drawRoundedRectBorder(ctx, x, y, width, height, LucidTheme.RADIUS_SMALL, border, 1);

        int padX = 10;
        int textY = RenderUtils.centerTextY(tr, y, height);

        String display = text;
        int displayColor = Colors.AURORA_TEXT;
        if (text.isEmpty()) {
            display = placeholder;
            displayColor = Colors.AURORA_TEXT_FAINT;
        }
        // Truncate if too wide
        int maxW = width - padX * 2 - 2;
        while (!display.isEmpty() && tr.getWidth(display) > maxW) {
            display = display.substring(0, display.length() - 1);
        }
        RenderUtils.drawText(ctx, tr, display, x + padX, textY, displayColor, false);

        // Caret (blink) — Aurora accent.
        if (focused && caretBlink < 550 && !text.isEmpty()) {
            int caretX = x + padX + tr.getWidth(text);
            ctx.fill(caretX, y + 5, caretX + 1, y + height - 5, Colors.AURORA_ACCENT_HOVER);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;
        if (button == 0 && contains(mx, my)) {
            focused = true;
            return true;
        } else {
            focused = false;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
            if (onChange != null) onChange.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && !text.isEmpty()) {
            text = "";
            if (onChange != null) onChange.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focused = false;
            return true;
        }
        // ── Paste (Ctrl+V / Cmd+V on macOS) ───────────────────────
        // Minecraft 1.21.11 doesn't route clipboard paste through
        // charTyped; we catch the key combo here and pull from the
        // system clipboard via the MinecraftKeyboard.
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean cmd  = (modifiers & GLFW.GLFW_MOD_SUPER) != 0;
        if ((ctrl || cmd) && (keyCode == GLFW.GLFW_KEY_V)) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            String clip = mc.keyboard.getClipboard();
            if (clip != null) {
                // Strip newlines/tabs so the key stays on one line
                clip = clip.replaceAll("[\\r\\n\\t]", "").trim();
                if (!clip.isEmpty() && text.length() + clip.length() <= maxLength) {
                    text += clip;
                    if (onChange != null) onChange.run();
                }
            }
            return true;
        }
        // ── Select-all + copy (Ctrl+A / Ctrl+C) for convenience ──
        if ((ctrl || cmd) && keyCode == GLFW.GLFW_KEY_A) {
            // No selection state; just no-op (consume so it doesn't beep)
            return true;
        }
        if ((ctrl || cmd) && keyCode == GLFW.GLFW_KEY_C) {
            if (!text.isEmpty()) {
                net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
                mc.keyboard.setClipboard(text);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!focused) return false;
        // Filter to printable chars
        if (chr >= 32 && chr < 127 && text.length() < maxLength) {
            text += chr;
            if (onChange != null) onChange.run();
            return true;
        }
        return false;
    }

    public String getText() { return text; }
    public void setText(String t) { this.text = t == null ? "" : t; }
    public void setPlaceholder(String p) { this.placeholder = p; }
}
