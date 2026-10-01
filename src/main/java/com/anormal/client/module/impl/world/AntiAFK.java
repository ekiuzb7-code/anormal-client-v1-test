package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class AntiAFK extends Module {
    private int timer = 0;

    public AntiAFK() {
        super("Anti-AFK", "Prevents AFK kicks by issuing periodic micro movements", Category.WORLD);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        timer++;
        if (timer >= 100) {
            timer = 0;
            mc.player.setYaw(mc.player.getYaw() + 1.0f);
        }
    }
}
