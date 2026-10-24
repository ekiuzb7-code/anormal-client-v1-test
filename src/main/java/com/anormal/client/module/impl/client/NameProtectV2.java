package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.lucid.EnumSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * NameProtect — replaces your own Minecraft username in the scoreboard
 * sidebar with a chosen preset name, client-side only.
 *
 * <h3>What it does</h3>
 * On DonutSMP the scoreboard sidebar (right side of the screen) lists every
 * player and their balance/stats, including a row for <i>you</i> showing your
 * real Minecraft username. While this module is enabled, your row's name is
 * swapped for a preset ("You", "Steve", "Alex", "Hidden", or "???") so your
 * identity is hidden in screenshots / recordings / streams. Other players'
 * names are untouched, and the server still sees your real name — this is
 * purely cosmetic, client-side visual.
 *
 * <h3>How it works</h3>
 * A mixin ({@link java.lang.Object}) intercepts
 * {@link ScoreboardEntry#name()} (the public record accessor that returns the
 * display {@code Text} for a sidebar line) at {@code RETURN}. When this module
 * is active AND the entry's {@code owner()} equals the local player's
 * scoreboard name AND we're on DonutSMP, the mixin returns
 * {@code Text.literal(presetName)} instead. {@code Team.decorateName} then
 * runs on top of the replacement, so the team color/prefix DonutSMP applies
 * still wraps our name — only the content changes, not the styling.
 *
 * <h3>Scope</h3>
 * DonutSMP-only by default (matches the FakePay server gate): the swap only
 * fires when {@link MinecraftClient#getCurrentServerEntry()} address/name
 * contains "donut". Off-server the module is inert.
 *
 * <h3>Settings</h3>
 *   Toggle Key, Name (preset dropdown).
 *
 * Toggle key works even when the module is disabled (same pattern as
 * Sprint / Hitboxes / Freecam / SkinProtect).
 */
public class NameProtectV2 extends Module {

    // ── Static state read by the mixin (mirrors SkinProtect.isActive() /
    //    getSelectedPreset()). A static flag lets the Fabric/mixin handler
    //    no-op cheaply when the module is off.
    private static volatile boolean active = false;
    private static volatile String preset = "You";

    /** Preset replacement names (must match the EnumSetting option order below). */
    public static final String[] PRESETS = {"You", "Steve", "Alex", "Hidden", "???"};

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable name protection. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final EnumSetting namePreset = addSetting(
        new EnumSetting("Name", "Which preset name to show in the scoreboard sidebar instead of your username.",
            PRESETS, "You"));

    /** Toggle key edge detection (matches Sprint's onTickAlways pattern). */
    private boolean wasKeyDown = false;

    public NameProtectV2() {
        super("Name Protect",
            "Hides your username in the DonutSMP scoreboard sidebar with a preset name. Client-side only.",
            Category.PLAYER);
    }

    @Override
    public void onEnable() {
        wasKeyDown = false;
        // Read the current preset selection so the mixin knows which name to use.
        preset = PRESETS[namePreset.getSelectedIndex()];
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching Sprint / Hitboxes / Freecam / SkinProtect. Also syncs the
     * preset each tick so live changes in the ClickGUI take effect immediately.
     */
    @Override
    public void onTickAlways() {
        // Keep the selected preset in sync with the ClickGUI dropdown so
        // changing it while enabled updates the sidebar live.
        if (active) {
            int idx = namePreset.getSelectedIndex();
            if (idx >= 0 && idx < PRESETS.length) {
                preset = PRESETS[idx];
            }
        }

        if (mc.player == null || mc.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = mc.getWindow().getHandle();
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

    // ════════════════════════════════════════════════════════════
    //  Mixin read surface — static, no module instance required
    // ════════════════════════════════════════════════════════════

    /** Called by the mixin to check if name swapping is active. */
    public static boolean isActive() { return active; }

    /** Called by the mixin to know which preset name to substitute. */
    public static String getPresetName() { return preset; }

    /**
     * If the given scoreboard line text contains the local player's username
     * as a substring, return a rebuilt {@link Text} with the username replaced
     * by the chosen preset. Returns {@code null} when nothing was replaced (so
     * the caller leaves the original untouched).
     *
     * <p>DonutSMP puts the player's real username somewhere in the assembled
     * sidebar line (the scoreboard {@code owner} is a fake colour-code holder,
     * not the name, and team prefix/suffix may or may not be used). We search
     * the whole line's plain text for the username as a substring and swap it
     * in place, preserving the original style (colour/formatting) on the
     * rebuilt literal. The sidebar renderer only reads the flat string + style,
     * so collapsing the tree is safe.
     *
     * <p>Static so the mixin can call it without holding a module instance.
     */
    public static Text replaceUsernameInText(Text line) {
        if (!active) return null;
        if (line == null) return null;
        if (!isOnDonutSMP()) return null;

        
        if (mc.player == null) return null;

        String myName = mc.player.getNameForScoreboard();
        if (myName == null || myName.isEmpty()) return null;

        String p = preset;
        if (p == null || p.isEmpty()) return null;

        // Quick reject: if the username doesn't appear anywhere in the plain
        // text of the line, there's nothing to replace.
        if (!line.getString().contains(myName)) return null;

        String replaced = line.getString().replace(myName, p);
        return Text.literal(replaced).setStyle(line.getStyle());
    }

    /**
     * True when the current server looks like DonutSMP. Checks the server
     * address (the IP/domain the user connected to) and the saved-server
     * name for the substring "donut" (case-insensitive). Returns false when
     * not connected to any server (singleplayer/lobby) so the mod stays inert.
     */
    private static boolean isOnDonutSMP() {
        return isOnDonutSMPDebug();
    }

    /** Debug-visible version of {@link #isOnDonutSMP()} for diagnostic logging. */
    public static boolean isOnDonutSMPDebug() {
        try {
            
            if (mc == null) return false;
            ServerInfo entry = mc.getCurrentServerEntry();
            if (entry == null) return false; // singleplayer / not connected

            if (entry.address != null
                && entry.address.toLowerCase(Locale.ROOT).contains("donut")) {
                return true;
            }
            if (entry.name != null
                && entry.name.toLowerCase(Locale.ROOT).contains("donut")) {
                return true;
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid NameProtect server-detection threw", t);
        }
        return false;
    }
}