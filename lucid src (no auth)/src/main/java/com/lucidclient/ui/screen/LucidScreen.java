package com.lucidclient.ui.screen;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.KeybindSetting;
import com.lucidclient.module.setting.Setting;
import com.lucidclient.module.setting.StringSetting;
import com.lucidclient.ui.component.Component;
import com.lucidclient.ui.component.ContextMenu;
import com.lucidclient.ui.component.Tooltip;
import com.lucidclient.ui.theme.LucidTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Base Lucid Client screen.
 *
 * Provides:
 *   - Smooth opening animation (fade + 95%→100% scale)
 *   - Frosted-white background overlay (softly fades in)
 *   - A delta-time clock for component animations (60fps smoothing)
 *   - Centralised tooltip + context menu management
 *   - "Closing" reverse animation before actually closing
 *   - Keybind capture: when any module's {@link KeybindSetting} is in
 *     "waiting" mode, the next key press becomes that setting's binding
 *     (Escape cancels waiting).
 *
 * NOTE: Minecraft 1.21.11 refactored input handling to use Click /
 * KeyInput / CharInput records. We unwrap them here and forward
 * (x, y, button, keyCode, char, …) primitives to the {@link Component}
 * input handlers, which keep their simple unwrapped signatures.
 */
public abstract class LucidScreen extends Screen {
    protected final List<Component> components = new ArrayList<>();
    protected final Tooltip tooltip = new Tooltip();
    protected final ContextMenu contextMenu = new ContextMenu();

    protected float openProgress = 0f;   // 0..1
    protected boolean closing = false;

    private long lastFrameMs;
    private long deltaMs;

    // Background opacity multiplier (1.0 = nearly opaque white)
    protected float backgroundOpacity = 0.96f;

    protected LucidScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        lastFrameMs = System.currentTimeMillis();
        components.clear();
        buildUI();
    }

    /** Subclasses populate {@link #components} and any other state here. */
    protected abstract void buildUI();

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Compute dt and advance all animations. */
    private void updateClock() {
        long now = System.currentTimeMillis();
        deltaMs = Math.min(50, now - lastFrameMs); // cap at 50ms
        lastFrameMs = now;

        float target = closing ? 0f : 1f;
        float speed = 1f / LucidTheme.DURATION_OPEN;
        openProgress += (target - openProgress) * Math.min(1f, speed * deltaMs);
        if (openProgress < 0.001f && closing) {
            // Animation done — actually close
            this.close();
            return;
        }
        if (openProgress > 0.999f) openProgress = 1f;

        // Update components
        for (Component c : components) {
            c.update(deltaMs);
        }
        tooltip.update(deltaMs);
        contextMenu.update(deltaMs);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateClock();

        float p = openProgress;

        // Apply center scale transform for the opening pop-in.
        // Background painting is handled by the renderBackground
        // override below (called by vanilla's renderWithTooltip
        // BEFORE this method — see drawBackground hook).
        float scale = 0.95f + 0.05f * p;
        int cx = this.width / 2;
        int cy = this.height / 2;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) cx, (float) cy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate((float) -cx, (float) -cy);

        // Update hover state for all components
        for (Component c : components) {
            c.updateHover(mouseX, mouseY);
        }

        // Render content
        renderContent(context, mouseX, mouseY, delta);

        context.getMatrices().popMatrix();

        // ── Overlays (not scaled) ───────────────────────────────
        renderOverlays(context, mouseX, mouseY, delta);
    }

    /**
     * Replaces vanilla's {@code Screen.renderBackground} path so we can route
     * the background through our {@link #drawBackground} hook and SKIP the
     * heavy {@code renderDarkening()} that vanilla calls last (which is what
     * makes custom screens look "black"). Subclasses override
     * {@link #drawBackground} to choose their own backdrop (the ClickGUI
     * blurs the world and paints its own scrim). {@code openProgress} here is last
     * frame's value (renderBackground fires before render() updates it);
     * the easing is smooth enough that the one-frame lag is invisible.
     */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        drawBackground(ctx, openProgress);
    }

    /**
     * Draw the screen background behind the content. Default implementation
     * registers the vanilla {@code applyBlur} post-effect so the world behind
     * a menu is frosted without being dimmed.
     *
     * @param ctx      the draw context
     * @param progress open-progress 0..1 (fades background in)
     */
    protected void drawBackground(DrawContext ctx, float progress) {
        // Vanilla's Screen.applyBlur(ctx) only fires if the user's "Menu
        // Background Blurriness" option is >= 1; default is 0, so it does
        // nothing. Call DrawContext.applyBlur() directly to bypass the
        // option guard and ALWAYS get the blur. NO scrim fill at all —
        // the world stays COMPLETELY SEE-THROUGH behind the frosted blur.
        ctx.applyBlur();
    }

    /** Subclasses render their main content here. */
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        for (Component c : components) {
            if (c.isVisible()) c.render(ctx, this.textRenderer, mouseX, mouseY, delta);
        }
    }

    /** Render tooltip + context menu on top (unscaled). */
    protected void renderOverlays(DrawContext ctx, int mouseX, int mouseY, float delta) {
        contextMenu.render(ctx, this.textRenderer, mouseX, mouseY);
        tooltip.render(ctx, this.textRenderer, mouseX, mouseY, this.width, this.height);
    }

    // ── Input forwarding (1.21.11 uses Click/KeyInput/CharInput records) ──
    @Override
    public boolean mouseClicked(Click click, boolean repeat) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();

        // Context menu takes priority when open.
        if (contextMenu.isOpen()) {
            return contextMenu.mouseClicked(mx, my, button);
        }
        for (Component c : components) {
            if (c.mouseClicked(mx, my, button)) return true;
        }
        return super.mouseClicked(click, repeat);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        for (Component c : components) {
            if (c.mouseReleased(mx, my, button)) return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mx = click.x();
        double my = click.y();
        int button = click.button();
        for (Component c : components) {
            if (c.mouseDragged(mx, my, button, dx, dy)) return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Component c : components) {
            if (c.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();

        // ── Focused StringSetting capture ──────────────────────
        // If a text field is focused, route key events to it directly.
        // Backspace deletes the last char; Escape/Enter defocus; any
        // other key is left to fall through (so the keybind-capture
        // block below never fires while typing).
        StringSetting focused = findFocusedStringSetting();
        if (focused != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                focused.backspace();
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                focused.setFocused(false);
            }
            return true;
        }

        // ── Keybind capture ────────────────────────────────────
        // If any module's KeybindSetting is in "waiting" mode, the next
        // key press becomes that setting's binding (Escape cancels).
        KeybindSetting waiting = findWaitingKeybindSetting();
        if (waiting != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                waiting.cancelWaiting();
            } else {
                waiting.setKey(keyCode);
            }
            return true;
        }

        for (Component c : components) {
            if (c.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean keyReleased(KeyInput keyInput) {
        int keyCode = keyInput.key();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();
        for (Component c : components) {
            if (c.keyReleased(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyReleased(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        char chr = (char) charInput.codepoint();
        int modifiers = charInput.modifiers();

        // ── Focused StringSetting capture ──────────────────────
        // Route typed characters to the focused text field so the user
        // can edit the value. Mirrors the keybind-capture pattern.
        StringSetting focused = findFocusedStringSetting();
        if (focused != null) {
            focused.appendChar(chr);
            return true;
        }

        for (Component c : components) {
            if (c.charTyped(chr, modifiers)) return true;
        }
        return super.charTyped(charInput);
    }

    /**
     * Scan every module's settings for a {@link KeybindSetting} that is in
     * "waiting" mode (the user clicked the keybind control to rebind it).
     * Returns the first such setting, or null if none are waiting.
     */
    protected KeybindSetting findWaitingKeybindSetting() {
        LucidClient client = LucidClient.getInstance();
        if (client == null || client.getModuleManager() == null) return null;
        for (Module module : client.getModuleManager().getAll()) {
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof KeybindSetting) {
                    KeybindSetting ks = (KeybindSetting) setting;
                    if (ks.isWaiting()) return ks;
                }
            }
        }
        return null;
    }

    /**
     * Scan every module's settings for a {@link StringSetting} that is
     * "focused" (the user clicked the text field to edit it). Returns the
     * first such setting, or null if none are focused. Mirrors
     * {@link #findWaitingKeybindSetting()} so keyboard input can be routed
     * to the focused field directly.
     */
    protected StringSetting findFocusedStringSetting() {
        LucidClient client = LucidClient.getInstance();
        if (client == null || client.getModuleManager() == null) return null;
        for (Module module : client.getModuleManager().getAll()) {
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof StringSetting) {
                    StringSetting ss = (StringSetting) setting;
                    if (ss.isFocused()) return ss;
                }
            }
        }
        return null;
    }

    /** Start the closing animation; the screen will truly close when it completes. */
    public void requestClose() {
        closing = true;
    }

    public float getOpenProgress() { return openProgress; }
    public boolean isClosing() { return closing; }
    public long getDeltaMs() { return deltaMs; }
}
