package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class JumpReset extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Jump timing chance %", 90.0, 10.0, 100.0, 5.0);

    public JumpReset() {
        super("JumpReset", "Jumps precisely when taking damage to reduce knockback", Category.COMBAT);
        addSetting(chance);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.hurtTime == 9 && mc.player.isOnGround()) {
            if (Math.random() * 100.0 <= chance.getValue()) {
                mc.player.jump();
            }
        }
    }
}
