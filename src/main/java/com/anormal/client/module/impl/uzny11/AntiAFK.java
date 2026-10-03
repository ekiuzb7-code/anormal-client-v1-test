package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class AntiAFK extends Module {
    public final NumberSetting delay = new NumberSetting("Delay", "Action delay (seconds)", 30, 5, 300, 5);
    public final BooleanSetting jump = new BooleanSetting("Jump", "Jump periodically", true);
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate camera", false);

    private int ticks = 0;

    public AntiAFK() {
        super("AntiAFK", "Prevents AFK kick", Category.UZNY11);
        addSetting(delay);
        addSetting(jump);
        addSetting(rotate);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (++ticks < delay.getValue().intValue() * 20) return;
        ticks = 0;

        if (jump.isEnabled()) mc.player.jump();
        if (rotate.isEnabled()) mc.player.setYaw(mc.player.getYaw() + 1);
    }
}