package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

/**
 * AutoWalk — continuously walks forward without holding W.
 *
 * Each client tick (while enabled), we hold the vanilla forward key down
 * via {@code options.forwardKey.setPressed(true)}. The player walks in
 * whatever direction they're facing, exactly as if they were holding W.
 * When disabled (or when a GUI is open), we release the key so it doesn't
 * get stuck.
 *
 * Settings:
 *   Toggle Key, Sprint.
 */
public class AutoWalk extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable auto-walk. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final BooleanSetting sprint = addSetting(
        new BooleanSetting("Sprint", "Also sprint while auto-walking (drains hunger faster).",
            false));

    /** Toggle key edge detection. */
    private boolean wasKeyDown = false;

    public AutoWalk() {
        super("AutoWalk", "Automatically walks forward without holding W.", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        wasKeyDown = false;
    }

    @Override
    protected void onDisable() {
        // Release the injected keys so we don't leave them held down.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.forwardKey.setPressed(false);
            if (sprint.isEnabled()) {
                client.options.sprintKey.setPressed(false);
            }
        }
    }

    @Override
    public void onTickAlways(MinecraftClient client) {
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

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        // Don't force-walk while a screen is open (inventory, chat, etc.)
        if (client.currentScreen != null) {
            client.options.forwardKey.setPressed(false);
            return;
        }

        client.options.forwardKey.setPressed(true);
        if (sprint.isEnabled()) {
            client.options.sprintKey.setPressed(true);
            player.setSprinting(true);
        }
    }
}