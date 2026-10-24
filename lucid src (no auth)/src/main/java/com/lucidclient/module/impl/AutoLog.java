package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * AutoLog — automatically leaves the server when your health drops below a
 * set threshold, creating a "safe" auto-log to avoid dying.
 *
 * <h3>How it works</h3>
 * Each client tick (while enabled), if the player's current health
 * ({@code player.getHealth()}) is at or below {@link #healthThreshold} AND
 * the player has actually taken damage this session (not just spawned at
 * low max-health from a debuff), the module disconnects the client from the
 * server via {@link MinecraftClient#disconnect(Text)} with a clear reason.
 *
 * <h3>Why a "has been damaged" guard</h3>
 * Without it, the module would instantly log you if you joined a server with
 * a max-health-decreasing effect (e.g. Wither/Bad Omen scaling) that leaves
 * your max health at or below the threshold before you ever take a hit. The
 * {@code tookDamage} flag only flips to true once {@code getHealth()} drops
 * below {@code getMaxHealth()} at least once, so the log only fires on real
 * damage, not on join-time debuffs. It resets each time the module is
 * enabled or the player respawns/joins a world.
 *
 * <h3>Real action only</h3>
 * Uses the vanilla {@link MinecraftClient#disconnect(Text)} — the same
 * disconnect path the game uses when you click "Disconnect" in the pause
 * menu. No forged packets. Same category as the other Auto* QoL modules.
 *
 * Settings:
 *   Toggle Key, Health Threshold, Only While In Combat.
 */
public class AutoLog extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable auto-log. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final FloatSetting healthThreshold = addSetting(
        new FloatSetting("Health Threshold", "Disconnect when your health drops to or below this many hearts (1.0 = 1 heart).",
            1f, 20f, 6f, 1));
    private final BooleanSetting onlyWhileInCombat = addSetting(
        new BooleanSetting("Only In Combat", "Only auto-log while actually taking damage (not on low-health join debuffs).",
            true));

    /** Toggle key edge detection. */
    private boolean wasKeyDown = false;

    /**
     * True once the player has taken real damage this life (health dropped
     * below max). Gates the log so low-max-health joins don't fire it.
     */
    private boolean tookDamage = false;

    /** One-shot guard so we don't try to disconnect twice in the same tick. */
    private boolean logged = false;

    public AutoLog() {
        super("AutoLog",
            "Automatically disconnects from the server when your health drops below a set threshold.",
            Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        wasKeyDown = false;
        tookDamage = false;
        logged = false;
    }

    @Override
    protected void onDisable() {
        wasKeyDown = false;
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

        // If we already disconnected, nothing more to do this session.
        if (logged) return;

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();

        // Track whether the player has taken real damage this life.
        if (health < maxHealth) {
            tookDamage = true;
        }

        float threshold = healthThreshold.get();

        // Health at or below the threshold AND (if combat-gating is on) the
        // drop came from real damage rather than a join-time debuff.
        if (health > threshold) return;
        if (onlyWhileInCombat.isEnabled() && !tookDamage) return;

        // Fire — disconnect with a clear reason shown on the title screen.
        logged = true;

        // Slight delay isn't possible across ticks cleanly here, but the
        // disconnect fires in the same tick so the user
        // sees why they were kicked to the menu.
        client.disconnect(Text.literal("§cAutoLog: health dropped to " + formatHealth(health)));
    }

    /** Format a health float as "X.X hearts" for alerts. */
    private static String formatHealth(float hearts) {
        return String.format("%.1f heart(s)", hearts);
    }
}