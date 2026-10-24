package com.anormal.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Minimal single-line text input.
 *
 * <p>{@code TextFieldWidget} proved unreliable inside these custom panels - typing and pasting
 * silently did nothing, most likely because the widget expects to be a child of the screen with
 * the screen's own focus plumbing. This class keeps the state itself and handles the keys directly,
 * the same way the ClickGUI search box does, which is known to work here. Clipboard paste is read
 * straight from the client keyboard.</p>
 */
public final class TextField {

    private static final int BG = 0xFF1B1B21;
    private static final int BG_FOCUSED = 0xFF23232B;
    private static final int LINE = 0xFF3A3A42;
    private static final int LINE_FOCUSED = 0xFF5AA9FF;
    private static final int TEXT = 0xFFE8E8EC;
    private static final int TEXT_DIM = 0xFF6E6E7A;

    private final StringBuilder value = new StringBuilder();
    private int maxLength = 512;
    private boolean focused;
    private String placeholder = "";

    public TextField(String initial, String placeholder, int maxLength) {
        if (initial != null) value.append(initial);
        this.placeholder = placeholder == null ? "" : placeholder;
        this.maxLength = maxLength;
    }

    public String get() {
        return value.toString();
    }

    public void set(String text) {
        value.setLength(0);
        if (text != null) value.append(text);
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    // ------------------------------------------------------------------
    //  Rendering
    // ------------------------------------------------------------------

    public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, focused ? BG_FOCUSED : BG);
        ctx.fill(x, y + h - 1, x + w, y + h, focused ? LINE_FOCUSED : LINE);

        String shown = value.toString();
        boolean empty = shown.isEmpty();
        if (empty) shown = placeholder;

        // Keep the tail visible: a long path is useless if you only see its start.
        int maxWidth = w - 8;
        while (tr.getWidth(shown) > maxWidth && shown.length() > 1) {
            shown = shown.substring(1);
        }

        int color = empty ? TEXT_DIM : TEXT;
        ctx.drawText(tr, Text.literal(shown), x + 4, y + (h - 8) / 2, color, false);

        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = x + 4 + tr.getWidth(shown) + 1;
            ctx.fill(caretX, y + 3, caretX + 1, y + h - 3, LINE_FOCUSED);
        }
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    /** @return true when the click landed inside the field (and focused it) */
    public boolean click(double mx, double my, int x, int y, int w, int h) {
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            focused = true;
            return true;
        }
        return false;
    }

    /** @return true when the key was consumed */
    public boolean keyPressed(KeyInput input) {
        if (!focused) return false;
        int key = input.key();

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (value.length() > 0) value.setLength(value.length() - 1);
            return true;
        }
        // Ctrl+V pastes straight from the OS clipboard - the only reliable path here.
        // Screen.hasControlDown() no longer exists in this mapping; the modifier mask is
        // carried on the key event itself.
        boolean control = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (key == GLFW.GLFW_KEY_V && control) {
            try {
                String clip = MinecraftClient.getInstance().keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    String cleaned = clip.replace("\n", "").replace("\r", "");
                    int room = maxLength - value.length();
                    if (room > 0) value.append(cleaned, 0, Math.min(room, cleaned.length()));
                }
            } catch (Throwable ignored) {
            }
            return true;
        }
        return false;
    }

    /** @return true when the character was consumed */
    public boolean charTyped(CharInput input) {
        if (!focused) return false;
        String typed = input.asString();
        if (typed.isEmpty()) return false;
        char c = typed.charAt(0);
        if (c < 32 || c == 127) return false;
        if (value.length() < maxLength) value.append(c);
        return true;
    }
}
