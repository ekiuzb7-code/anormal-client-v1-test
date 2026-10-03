package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public class FreeLook extends Module {
    public final ModeSetting activate = new ModeSetting("Activate", "Hold while key down or Toggle on press", "Toggle", "Hold", "Toggle");
    public final ModeSetting startingPosition = new ModeSetting("Starting Position", "Camera direction on activation", "Forward", "Forward", "Backward");
    public final BooleanSetting useCustomSensitivity = new BooleanSetting("Custom Sensitivity", "Override mouse sensitivity while active", false);
    public final NumberSetting sensitivity = new NumberSetting("Sensitivity", "Freelook mouse sensitivity", 0.5, 0.05, 2.0, 0.05);
    public final BooleanSetting restoreView = new BooleanSetting("Restore View", "Restores your view on disable", true);
    public final ModeSetting style = new ModeSetting("Style", "V1 or V2 implementation", "V1", "V1", "V2");
    public final NumberSetting v2SenseBoost = new NumberSetting("V2 Sense Boost", "Look sensitivity multiplier", 1.0, 0.1, 2.0, 0.1);
    public final BooleanSetting v2NoPitchLimit = new BooleanSetting("V2 No Pitch Limit", "Look fully up and down", true);

    private Perspective savedPerspective;
    private double savedSensitivity = -1;
    // Detached look: camera follows these, body keeps base facing
    private float baseYaw, basePitch;
    private float lookYaw, lookPitch;
    private float prevYaw, prevPitch;
    private boolean active = false;

    public FreeLook() {
        super("FreeLook", "Detached 3rd-person camera allowing free view rotations", Category.RENDER, GLFW.GLFW_KEY_V);
        addSetting(activate);
        addSetting(startingPosition);
        addSetting(useCustomSensitivity);
        addSetting(sensitivity);
        addSetting(restoreView);
        addSetting(style);
        addSetting(v2SenseBoost);
        addSetting(v2NoPitchLimit);
        v2SenseBoost.visibleIf(() -> style.is("V2"));
        v2NoPitchLimit.visibleIf(() -> style.is("V2"));
    }

    @Override
    public void onEnable() {
        active = false;
        if (mc.player != null) {
            baseYaw = mc.player.getYaw();
            basePitch = mc.player.getPitch();
            lookYaw = baseYaw;
            lookPitch = basePitch;
            prevYaw = baseYaw;
            prevPitch = basePitch;
            active = true;
        }
        try {
            savedPerspective = mc.options.getPerspective();
            // Forward = see where you're going (back cam), Backward = face cam (behind you)
            mc.options.setPerspective(startingPosition.is("Backward") ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);
            if (useCustomSensitivity.isEnabled()) {
                try {
                    savedSensitivity = mc.options.getMouseSensitivity().getValue();
                    mc.options.getMouseSensitivity().setValue(sensitivity.getValue());
                } catch (Throwable ignored) {
                    savedSensitivity = -1;
                }
            }
        } catch (Throwable t) {
            savedPerspective = null;
        }
    }

    @Override
    public void onDisable() {
        active = false;
        try {
            if (restoreView.isEnabled() && mc.player != null) {
                mc.player.setYaw(baseYaw);
                mc.player.setPitch(basePitch);
            }
            if (restoreView.isEnabled() && savedPerspective != null) mc.options.setPerspective(savedPerspective);
            if (savedSensitivity >= 0) {
                try {
                    mc.options.getMouseSensitivity().setValue(savedSensitivity);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        } finally {
            savedPerspective = null;
            savedSensitivity = -1;
        }
    }

    @Override
    public void onTick() {
        // Mouse-look drives the detached CAMERA (via CameraMixin):
        // just track player rotation for look deltas, DON'T restore body facing
        // CameraMixin calculates third-person position using CAMERA rotation
        if (active && mc.player != null) {
            try {
                float py = mc.player.getYaw();
                float pp = mc.player.getPitch();
                if (style.is("V2")) {
                    double boost = v2SenseBoost.getValue();
                    lookYaw += (py - prevYaw) * boost;
                    lookPitch += (pp - prevPitch) * boost;
                    if (!v2NoPitchLimit.isEnabled()) {
                        lookPitch = Math.max(-90.0f, Math.min(90.0f, lookPitch));
                    }
                } else {
                    lookYaw = py;
                    lookPitch = pp;
                }
                prevYaw = py;
                prevPitch = pp;
                // DON'T restore player yaw/pitch - CameraMixin needs them for position calc
            } catch (Throwable ignored) {}
        }
        // Hold mode: auto-disable the moment the bind is released
        if (!activate.is("Hold") || mc.getWindow() == null) return;
        try {
            long window = mc.getWindow().getHandle();
            int key = getKey();
            boolean down;
            if (KeybindSetting.isMouseCode(key)) {
                int btn = KeybindSetting.mouseButton(key);
                down = btn >= 0 && GLFW.glfwGetMouseButton(window, btn) == GLFW.GLFW_PRESS;
            } else if (key > 0 && key <= GLFW.GLFW_KEY_LAST) {
                down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            } else {
                return;
            }
            if (!down) setEnabled(false);
        } catch (Throwable ignored) {}
    }

    // Read by CameraMixin every frame while active
    public boolean isCameraActive() {
        return active && isEnabled() && mc.player != null;
    }

    public float getLookYaw() { return lookYaw; }
    public float getLookPitch() { return lookPitch; }
}
