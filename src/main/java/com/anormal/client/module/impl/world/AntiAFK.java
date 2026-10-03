package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class AntiAFK extends Module {
    public final NumberSetting startDelay = new NumberSetting("Start Delay", "Idle seconds before activation", 30.0, 5.0, 300.0, 5.0);
    public final NumberSetting frequency = new NumberSetting("Frequency", "Movement actions per minute", 6.0, 1.0, 30.0, 1.0);
    public final BooleanSetting keepClose = new BooleanSetting("Keep Close", "Alternate directions to stay in place", true);
    public final BooleanSetting rotation = new BooleanSetting("Rotation", "Also adjust look angles", true);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Rotate without moving camera", false);
    public final NumberSetting maxYawChange = new NumberSetting("Max Yaw Change", "Yaw step per action", 8.0, 1.0, 45.0, 1.0);
    public final NumberSetting maxPitchChange = new NumberSetting("Max Pitch Change", "Pitch step per action", 4.0, 0.0, 30.0, 1.0);
    public final BooleanSetting jump = new BooleanSetting("Jump", "Also jump on each action", false);
    public final BooleanSetting testNow = new BooleanSetting("Test Now", "Do one action immediately", false);

    private int idleTicks = 0;
    private int actionTicks = 0;
    private int moveTicks = 0;
    private boolean forward = true;

    public AntiAFK() {
        super("Anti-AFK", "Prevents AFK kicks with periodic micro movements", Category.WORLD);
        addSetting(startDelay);
        addSetting(frequency);
        addSetting(keepClose);
        addSetting(rotation);
        addSetting(silentAim);
        addSetting(maxYawChange);
        addSetting(maxPitchChange);
        addSetting(jump);
        addSetting(testNow);
    }

    @Override
    public void onEnable() {
        idleTicks = 0;
        actionTicks = 0;
        moveTicks = 0;
    }

    @Override
    public void onDisable() {
        if (mc.options != null) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
        }
        moveTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        // Manual test: acts right now so you can see it working
        if (testNow.isEnabled()) {
            testNow.setValue(false);
            idleTicks = 0;
            actionTicks = 0;
            doAction();
            return;
        }
        if (moveTicks > 0) {
            if (--moveTicks <= 0) {
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(false);
                mc.options.jumpKey.setPressed(false);
            }
            return;
        }
        idleTicks++;
        if (idleTicks < startDelay.getValue() * 20.0) return;
        int interval = (int) Math.max(20.0, 1200.0 / Math.max(1.0, frequency.getValue()));
        if (++actionTicks < interval) return;
        actionTicks = 0;
        doAction();
    }

    private void doAction() {
        if (keepClose.isEnabled()) forward = !forward;
        else forward = true;
        mc.options.forwardKey.setPressed(forward);
        mc.options.backKey.setPressed(!forward);
        if (jump.isEnabled()) mc.options.jumpKey.setPressed(true);
        moveTicks = 4;
        if (rotation.isEnabled() && !silentAim.isEnabled()) {
            mc.player.setYaw(mc.player.getYaw() + (forward ? 1 : -1) * (float) maxYawChange.getValue().doubleValue());
            float p = mc.player.getPitch();
            float step = (forward ? 1 : -1) * (float) maxPitchChange.getValue().doubleValue();
            mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, p + step)));
        }
    }
}
