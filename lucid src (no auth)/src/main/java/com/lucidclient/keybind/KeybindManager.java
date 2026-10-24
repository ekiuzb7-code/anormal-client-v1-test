package com.lucidclient.keybind;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Holds the Lucid Client keybindings:
 *   Right Shift → Click GUI
 *
 * AnchorMacro now auto-triggers when the player holds a Respawn Anchor
 * (no dedicated keybind needed). AutoClicker / Freecam use per-module
 * KeybindSetting capture inside the ClickGUI instead of global keybinds.
 *
 * Registered with Fabric's KeyBindingHelper at startup.
 *
 * NOTE: Minecraft 1.21.11 changed KeyBinding to use a Category record
 * (KeyBinding.Category.MISC / MOVEMENT / etc.) instead of a plain String
 * category. We use MISC for the Lucid Client grouping.
 */
public final class KeybindManager {
    /** Lucid Client keybind category. Uses MISC for control-panel grouping. */
    public static final KeyBinding.Category CATEGORY = KeyBinding.Category.MISC;

    public final KeyBinding clickGuiKey;

    public KeybindManager() {
        this.clickGuiKey = new KeyBinding(
            "key.lucidclient.clickgui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            CATEGORY
        );
    }
}
