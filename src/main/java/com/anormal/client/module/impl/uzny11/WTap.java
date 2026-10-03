package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class WTap extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "WTap chance %", 100.0, 0.0, 100.0, 1.0);
    public final NumberSetting releaseDelay = new NumberSetting("Release Delay", "Ticks to release W", 2, 1, 10, 1);
    public final NumberSetting pressDelay = new NumberSetting("Press Delay", "Ticks before pressing W again", 1, 0, 10, 1);
    public final BooleanSetting onlySprint = new BooleanSetting("Only Sprint", "Only WTap while sprinting", true);

    private int state = 0; // 0=waiting, 1=released, 2=pressed
    private int ticks = 0;
    private boolean wasSprinting = false;

    public WTap() {
        super("WTap", "Automatically W-Taps for better knockback", Category.UZNY11);
        addSetting(chance);
        addSetting(releaseDelay);
        addSetting(pressDelay);
        addSetting(onlySprint);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (onlySprint.isEnabled() && !mc.player.isSprinting()) return;
        if (mc.player.hurtTime <= 0) {
            state = 0;
            ticks = 0;
            return;
        }

        if (state == 0 && mc.options.forwardKey.isPressed()) {
            if (Math.random() * 100 < chance.getValue()) {
                state = 1;
                ticks = 0;
            }
        }

        if (state == 1) {
            mc.options.forwardKey.setPressed(false);
            if (++ticks >= releaseDelay.getValue().intValue()) {
                state = 2;
                ticks = 0;
            }
        } else if (state == 2) {
            if (++ticks >= pressDelay.getValue().intValue()) {
                state = 0;
            }
        }
    }
}