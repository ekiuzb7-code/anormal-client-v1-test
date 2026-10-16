package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Ported from Wurst's Zoom feature.
 *
 * <p>While active it divides the rendered FOV by the zoom level and scales the
 * mouse sensitivity down by the same factor, so aiming while zoomed stays
 * precise instead of flying across the screen.</p>
 *
 * <p>Wurst's settings, mirrored here:</p>
 * <ul>
 *   <li>"Zoom level" - how much to zoom in (default 3x, 1..50)</li>
 *   <li>"Use mouse wheel" - scroll while zooming to zoom in further</li>
 *   <li>"Zoom in screens" - keep zooming while chat/inventory is open</li>
 * </ul>
 *
 * <p>This module defaults to <b>Hold</b> keybind mode, so holding the key
 * zooms and releasing returns to normal.</p>
 */
public class Zoom extends Module {
    public final NumberSetting level = new NumberSetting("Zoom Level", "How much to zoom in", 3.0, 1.0, 50.0, 0.1);
    public final BooleanSetting scroll = new BooleanSetting("Use Mouse Wheel", "Scroll while zooming to zoom in even further", true);
    public final NumberSetting scrollStep = new NumberSetting("Scroll Step", "How much one scroll notch multiplies the zoom level", 1.1, 1.01, 2.0, 0.01);
    public final BooleanSetting zoomInScreens = new BooleanSetting("Zoom In Screens", "Keep zooming while chat or inventory is open", false);
    public final BooleanSetting smooth = new BooleanSetting("Smooth Zoom", "Fade the zoom level in and out instead of snapping", true);
    public final NumberSetting smoothSpeed = new NumberSetting("Smooth Speed", "How fast the zoom fades in and out", 10.0, 1.0, 30.0, 1.0);
    public final BooleanSetting reduceSensitivity = new BooleanSetting("Reduce Sensitivity", "Scale the mouse sensitivity down while zoomed, like Wurst does", true);

    /** Zoom level actually used this frame - eases towards {@code level} for a smooth feel. */
    private double appliedLevel = 3.0;
    /** True when the previous frame was not zooming, so we can snap immediately. */
    private boolean wasZooming = false;
    /** Mouse sensitivity the player had before we scaled it down. */
    private double savedSensitivity = -1.0;

    public Zoom() {
        super("Zoom", "Allows you to zoom in", Category.RENDER);
        addSetting(level);
        addSetting(scroll);
        addSetting(scrollStep);
        addSetting(zoomInScreens);
        addSetting(smooth);
        addSetting(smoothSpeed);
        addSetting(reduceSensitivity);

        // Zoom only makes sense while the key is pressed.
        getKeybindModeSetting().setValue("Hold");
    }

    @Override
    public void onDisable() {
        wasZooming = false;
        restoreSensitivity();
    }

    /** Called by the mouse mixin whenever the wheel is scrolled in game. */
    public void onMouseScroll(double amount) {
        if (!scroll.isEnabled()) return;
        double step = scrollStep.getValue();
        if (amount > 0) appliedLevel = clamp(appliedLevel * step, 1.0, 50.0);
        else if (amount < 0) appliedLevel = clamp(appliedLevel / step, 1.0, 50.0);
        // Make the adjusted level the new configured level, like Wurst does.
        level.setValue(clamp(appliedLevel, 1.0, 50.0));
    }

    /** True when the mouse wheel should be consumed by the zoom. */
    public boolean isControllingScroll() {
        return isActive() && scroll.isEnabled();
    }

    /** False while a screen is open unless "Zoom In Screens" is enabled. */
    public boolean isActive() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (client != null && client.currentScreen != null && !zoomInScreens.isEnabled()) return false;
        } catch (Throwable ignored) {
        }
        return isEnabled();
    }

    /**
     * Applies the per-frame zoom: returns the FOV divisor and scales the mouse
     * sensitivity to match. Called every frame by the GameRenderer mixin.
     */
    public double applyFrame() {
        MinecraftClient client = MinecraftClient.getInstance();
        double target = level.getValue();

        if (!smooth.isEnabled() || !wasZooming) {
            appliedLevel = target;
        } else {
            double t = Math.min(1.0, smoothSpeed.getValue() / 20.0);
            appliedLevel += (target - appliedLevel) * t;
        }
        wasZooming = true;

        if (reduceSensitivity.isEnabled()) {
            try {
                if (savedSensitivity < 0) {
                    savedSensitivity = client.options.getMouseSensitivity().getValue();
                }
                client.options.getMouseSensitivity().setValue(savedSensitivity / Math.max(1.0, appliedLevel));
            } catch (Throwable ignored) {
            }
        }

        return Math.max(1.0, appliedLevel);
    }

    /** Restores the player's own mouse sensitivity. */
    public void restoreSensitivity() {
        try {
            if (savedSensitivity >= 0) {
                MinecraftClient.getInstance().options.getMouseSensitivity().setValue(savedSensitivity);
                savedSensitivity = -1.0;
            }
        } catch (Throwable ignored) {
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
