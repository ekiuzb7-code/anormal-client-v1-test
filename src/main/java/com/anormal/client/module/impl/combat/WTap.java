package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class WTap extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Trigger probability %", 100.0, 10.0, 100.0, 5.0);
    private int resetTicks = 0;

    public WTap() {
        super("WTap", "Automatically resets sprint after hitting a player for maximum knockback", Category.COMBAT);
        addSetting(chance);
    }

    public void onHit() {
        if (!isEnabled() || mc.player == null) return;
        if (Math.random() * 100.0 <= chance.getValue()) {
            mc.player.setSprinting(false);
            resetTicks = 2;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (resetTicks > 0) {
            resetTicks--;
            if (resetTicks == 0 && mc.player.forwardSpeed > 0) {
                mc.player.setSprinting(true);
            }
        }
    }
}
