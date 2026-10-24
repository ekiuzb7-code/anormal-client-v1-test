package com.lucidclient.ui.component;

import com.lucidclient.ui.util.MathUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Base class for all Lucid Client UI components.
 *
 * Provides position/size, hover/press state with smooth animation,
 * and a standard set of input handlers (mouse, keyboard, scroll).
 *
 * Input handlers use unwrapped primitive arguments (double x, double y,
 * int button, int keyCode, char chr, …). The owning {@link
 * com.lucidclient.ui.screen.LucidScreen} unwraps Minecraft 1.21.11's
 * Click / KeyInput / CharInput records and forwards the primitives here.
 *
 * Subclasses (Button, Slider, Textbox, Checkbox, …) override the
 * handlers they care about and call super where appropriate.
 */
public abstract class Component {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected boolean focused = false;

    // Smooth animation state
    protected float hoverProgress = 0f;   // 0..1
    protected float pressProgress = 0f;   // 0..1
    protected boolean hovered;
    protected boolean pressed;

    public Component(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Render the component. delta = partial tick progress 0..1. */
    public abstract void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, float delta);

    // ── Input handlers (defaults return false = "not consumed") ──
    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) { return false; }
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) { return false; }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) { return false; }
    public boolean charTyped(char chr, int modifiers) { return false; }

    /** Update animation state. dtMs = delta time since last frame. */
    public void update(long dtMs) {
        // Smoothly approach hover/press targets
        float hoverTarget = hovered ? 1f : 0f;
        float pressTarget = pressed ? 1f : 0f;
        float hoverSpeed = 1f / 160f; // ~160ms
        float pressSpeed = 1f / 90f;  // ~90ms
        hoverProgress = approach(hoverProgress, hoverTarget, hoverSpeed * dtMs);
        pressProgress = approach(pressProgress, pressTarget, pressSpeed * dtMs);
    }

    protected static float approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return target;
    }

    /** Check whether a point is inside this component. */
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /** Refresh hovered state — called by the parent screen each frame. */
    public void updateHover(double mouseX, double mouseY) {
        this.hovered = enabled && contains(mouseX, mouseY);
    }

    // ── Getters / setters ─────────────────────────────────────
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setPos(int x, int y) { this.x = x; this.y = y; }
    public void setSize(int w, int h) { this.width = w; this.height = h; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public boolean isHovered() { return hovered; }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean f) { this.focused = f; }

    /** Eased hover value (ease-out-cubic) — useful for hover-lift / glow. */
    protected float easedHover() {
        float t = MathUtils.clamp(hoverProgress, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
