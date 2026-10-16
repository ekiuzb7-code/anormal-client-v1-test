package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Third-person free look. The player body keeps facing its original direction
 * while the camera can be rotated independently around it.
 *
 * <p>Mouse deltas are routed here by {@code EntityMixin} (the vanilla body
 * rotation is cancelled), and {@code CameraMixin} orbits the camera around the
 * player's eye using the detached look angles. This replaces the previous
 * implementation which placed the camera {@code fov} (≈70) blocks away and let
 * the player body turn with the mouse.</p>
 */
public class FreeLook extends Module {
    public final ModeSetting activate = new ModeSetting("Activate", "Hold while key down or Toggle on press", "Toggle", "Hold", "Toggle");
    public final ModeSetting startingPosition = new ModeSetting("Starting Position", "Camera direction on activation", "Forward", "Forward", "Backward");
    public final BooleanSetting useCustomSensitivity = new BooleanSetting("Custom Sensitivity", "Override mouse sensitivity while active", false);
    public final NumberSetting sensitivity = new NumberSetting("Sensitivity", "Freelook mouse sensitivity", 0.5, 0.05, 2.0, 0.05);
    public final BooleanSetting restoreView = new BooleanSetting("Restore View", "Restores your perspective on disable", true);
    public final ModeSetting style = new ModeSetting("Style", "V1 or V2 implementation", "V1", "V1", "V2");
    public final NumberSetting v2SenseBoost = new NumberSetting("V2 Sense Boost", "Look sensitivity multiplier", 1.0, 0.1, 2.0, 0.1);

    private Perspective savedPerspective;
    private double savedSensitivity = -1;

    // Detached camera look angles.
    private float lookYaw, lookPitch;
    private boolean active = false;

    public FreeLook() {
        super("FreeLook", "Detached 3rd-person camera allowing free view rotations", Category.RENDER, GLFW.GLFW_KEY_V);
        // Freecam / FreeLook are only useful while the key is held.
        getKeybindModeSetting().setValue("Hold");
        addSetting(activate);
        addSetting(startingPosition);
        addSetting(useCustomSensitivity);
        addSetting(sensitivity);
        addSetting(restoreView);
        addSetting(style);
        addSetting(v2SenseBoost);
        v2SenseBoost.visibleIf(() -> style.is("V2"));
    }

    @Override
    public void onEnable() {
        active = false;
        if (mc.player == null) return;

        lookYaw = mc.player.getYaw();
        lookPitch = mc.player.getPitch();
        active = true;

        try {
            savedPerspective = mc.options.getPerspective();
            // Forward = camera behind you, Backward = camera in front of you.
            mc.options.setPerspective(startingPosition.is("Backward") ? Perspective.THIRD_PERSON_FRONT : Perspective.THIRD_PERSON_BACK);

            if (useCustomSensitivity.isEnabled()) {
                savedSensitivity = mc.options.getMouseSensitivity().getValue();
                mc.options.getMouseSensitivity().setValue(sensitivity.getValue());
            } else {
                savedSensitivity = -1;
            }
        } catch (Throwable t) {
            savedPerspective = null;
        }
    }

    @Override
    public void onDisable() {
        active = false;
        try {
            if (restoreView.isEnabled() && savedPerspective != null) {
                mc.options.setPerspective(savedPerspective);
            }
            if (savedSensitivity >= 0) {
                mc.options.getMouseSensitivity().setValue(savedSensitivity);
            }
        } catch (Throwable ignored) {
        } finally {
            savedPerspective = null;
            savedSensitivity = -1;
        }
    }

    /** Called per frame by EntityMixin. Deltas are already scaled like vanilla. */
    public void addLook(double deltaYaw, double deltaPitch) {
        float boost = style.is("V2") ? v2SenseBoost.getValue().floatValue() : 1.0f;
        lookYaw = MathHelper.wrapDegrees(lookYaw + (float) deltaYaw * boost);
        lookPitch = MathHelper.clamp(lookPitch + (float) deltaPitch * boost, -90.0f, 90.0f);
    }

    @Override
    public void onTick() {
        // Hold mode: auto-disable the moment the bind is released.
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
        } catch (Throwable ignored) {
        }
    }

    public boolean isCameraActive() {
        return active && isEnabled() && mc.player != null;
    }

    public float getLookYaw() { return lookYaw; }
    public float getLookPitch() { return lookPitch; }
}
