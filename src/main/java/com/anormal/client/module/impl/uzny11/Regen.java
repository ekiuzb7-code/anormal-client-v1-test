package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class Regen extends Module {
    public final NumberSetting health = new NumberSetting("Health", "Minimum health to regen", 8.0, 0.5, 10.0, 0.5);

    public Regen() {
        super("Regen", "Regenerates health quicker", Category.UZNY11);
        addSetting(health);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.getHealth() * 2.0 >= health.getValue() * 2.0) return;
            if (!mc.player.isOnGround() || mc.options.jumpKey.isPressed()) return;
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    private int ticks = 0;

    public int getTicks() {
        return ticks;
    }


    @Override
    public void onDisable() {
        ticks = 0;
    }
}
