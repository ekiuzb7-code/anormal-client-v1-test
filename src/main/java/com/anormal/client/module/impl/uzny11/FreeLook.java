package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;

public class FreeLook extends Module {
    public final ModeSetting activate = new ModeSetting("Activate", "Hold while key down or Toggle on press", "Toggle", "Hold", "Toggle");
    public final ModeSetting startingPosition = new ModeSetting("Starting Position", "Camera direction on activation", "Forward", "Forward", "Backward");
    public final BooleanSetting useCustomSensitivity = new BooleanSetting("Custom Sensitivity", "Override mouse sensitivity", false);
    public final NumberSetting sensitivity = new NumberSetting("Sensitivity", "Freelook mouse sensitivity", 0.5, 0.05, 2.0, 0.05);

    private Perspective savedPerspective;
    private double savedSensitivity = -1;
    private float baseYaw, basePitch;
    private float lookYaw, lookPitch;
    private boolean active = false;

    public FreeLook() {
        super("FreeLook", "Detached 3rd-person camera, look without turning", Category.UZNY11, GLFW.GLFW_KEY_V);
        addSetting(activate);
        addSetting(startingPosition);
        addSetting(useCustomSensitivity);
        addSetting(sensitivity);
    }

    @Override
    public void onEnable() {
        active = false;
        if (mc.player != null) {
            baseYaw = mc.player.getYaw();
            basePitch = mc.player.getPitch();
            lookYaw = baseYaw;
            lookPitch = basePitch;
            active = true;
        }
        try {
            savedPerspective = mc.options.getPerspective();
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
            if (mc.player != null) {
                mc.player.setYaw(baseYaw);
                mc.player.setPitch(basePitch);
            }
            if (savedPerspective != null) mc.options.setPerspective(savedPerspective);
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
        if (active && mc.player != null) {
            try {
                lookYaw = mc.player.getYaw();
                lookPitch = mc.player.getPitch();
                mc.player.setYaw(baseYaw);
                mc.player.setPitch(basePitch);
            } catch (Throwable ignored) {}
        }
        if (!activate.is("Hold") || mc.getWindow() == null) return;
        try {
            long window = mc.getWindow().getHandle();
            int key = getKey();
            boolean down;
            if (com.anormal.client.setting.KeybindSetting.isMouseCode(key)) {
                int btn = com.anormal.client.setting.KeybindSetting.mouseButton(key);
                down = btn >= 0 && GLFW.glfwGetMouseButton(window, btn) == GLFW.GLFW_PRESS;
            } else if (key > 0 && key <= GLFW.GLFW_KEY_LAST) {
                down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            } else {
                return;
            }
            if (!down) setEnabled(false);
        } catch (Throwable ignored) {}
    }

    public boolean isCameraActive() {
        return active && isEnabled() && mc.player != null;
    }

    public float getLookYaw() { return lookYaw; }
    public float getLookPitch() { return lookPitch; }
}
