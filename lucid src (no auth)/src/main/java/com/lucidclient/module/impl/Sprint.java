package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

/**
 * Sprint — keeps the player sprinting instead of walking.
 *
 * ── How it works ────────────────────────────────────────────────
 * Each client tick (while enabled), if the player is moving forward and
 * able to sprint, we force {@code player.setSprinting(true)} and hold the
 * vanilla sprint key down via {@code options.sprintKey.setPressed(true)}.
 * This makes every forward movement a sprint without the player having to
 * double-tap W or hold Ctrl. The vanilla sprint-stop conditions (hitting a
 * wall, taking damage, hunger too low) still apply — vanilla re-clears the
 * sprinting flag, and we re-assert it next tick when conditions allow.
 *
 * ── Settings ────────────────────────────────────────────────────
 *   Only When Moving — only sprint while actually pressing forward, so the
 *     player can stand still without the hunger drain of constant sprint.
 *   Toggle Key — standard module chrome.
 *
 * Settings:
 *   Toggle Key, Only When Moving.
 */
public class Sprint extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable auto-sprint. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final BooleanSetting onlyWhenMoving = addSetting(
        new BooleanSetting("Only When Moving", "Only sprint while pressing forward, so standing still drains no hunger.",
            true));

    /** Toggle key edge detection (matches AutoClicker's onTickAlways pattern). */
    private boolean wasKeyDown = false;

    public Sprint() {
        super("Sprint", "Always sprint instead of walk.", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        wasKeyDown = false;
    }

    @Override
    protected void onDisable() {
        // Release the injected sprint key so we don't leave it held down.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.sprintKey.setPressed(false);
        }
        // Let vanilla decide sprinting state from here.
        ClientPlayerEntity player = client != null ? client.player : null;
        if (player != null) {
            player.setSprinting(false);
        }
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching AutoClicker's pattern.
     */
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

        // "Moving" = pressing forward (W). In 1.21.11 the client input is a
        // PlayerInput record on Input.playerInput; forward() is the boolean.
        boolean movingForward = player.input != null
            && player.input.playerInput != null
            && player.input.playerInput.forward();

        if (onlyWhenMoving.isEnabled() && !movingForward) {
            // Standing still (or not pressing forward) — release sprint so we
            // don't drain hunger while idle.
            client.options.sprintKey.setPressed(false);
            player.setSprinting(false);
            return;
        }

        // Force the sprint key down so vanilla's sprint logic treats this as a
        // held sprint (consistent with holding Ctrl), and re-assert the
        // sprinting flag every tick in case vanilla cleared it.
        client.options.sprintKey.setPressed(true);
        player.setSprinting(true);
    }
}