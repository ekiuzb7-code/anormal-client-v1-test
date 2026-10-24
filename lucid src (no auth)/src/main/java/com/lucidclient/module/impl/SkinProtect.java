package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * SkinProtect — replaces your own player skin (client-side only) with a
 * preset default skin (Steve, Alex, or random) while enabled.
 *
 * ── How it works ────────────────────────────────────────────────
 * A mixin ({@link com.lucidclient.mixin.AbstractClientPlayerEntityMixin})
 * intercepts {@code AbstractClientPlayerEntity.getSkin()} at RETURN. When
 * this module is active AND the entity being queried is the local player,
 * the mixin swaps the returned {@code SkinTextures} for one from
 * {@code DefaultSkinHelper} — so the renderer draws the preset skin
 * instead of your real one.
 *
 * This is purely client-side: the server and other players still see your
 * real skin. Useful for screenshots, recordings, or hiding your identity
 * in replays without changing your Minecraft account.
 *
 * ── Settings ────────────────────────────────────────────────────
 *   Skin    — which preset skin to use (Steve / Alex / Random).
 *
 * Toggle key works even when the module is disabled (same pattern as
 * Sprint / Hitboxes / Freecam).
 */
public class SkinProtect extends Module {

    // ── Static state read by the mixin (mirrors Freecam.isActive()) ──
    private static volatile boolean active = false;
    private static volatile int selectedPreset = 0;

    /** Preset indices (must match the EnumSetting option order below). */
    public static final int PRESET_STEVE = 0;
    public static final int PRESET_ALEX = 1;
    public static final int PRESET_RANDOM = 2;

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable skin protection. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final EnumSetting skinPreset = addSetting(
        new EnumSetting("Skin", "Which preset skin to use when protection is active.",
            new String[]{"Steve", "Alex", "Random"}, "Steve"));

    /** Toggle key edge detection (matches Sprint's onTickAlways pattern). */
    private boolean wasKeyDown = false;

    public SkinProtect() {
        super("Skin Protect", "Hides your real skin with a preset (Steve/Alex) client-side only.", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        wasKeyDown = false;
        // Read the current preset selection so the mixin knows which skin to use.
        selectedPreset = skinPreset.getSelectedIndex();
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching Sprint / Hitboxes / Freecam. Also syncs the preset index
     * each tick so live changes in the ClickGUI take effect immediately.
     */
    @Override
    public void onTickAlways(MinecraftClient client) {
        // Keep the selected preset in sync with the ClickGUI dropdown so
        // changing it while enabled updates the skin live.
        if (active) {
            selectedPreset = skinPreset.getSelectedIndex();
        }

        if (client.player == null || client.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = client.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        if (isDown && !wasKeyDown) {
            setEnabled(!isEnabled());
        }
        wasKeyDown = isDown;
    }

    /** Called by the mixin to check if skin swapping is active. */
    public static boolean isActive() { return active; }

    /** Called by the mixin to know which preset skin to use. */
    public static int getSelectedPreset() { return selectedPreset; }
}