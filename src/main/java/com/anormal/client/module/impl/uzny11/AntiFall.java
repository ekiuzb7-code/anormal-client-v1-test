package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class AntiFall extends Module {
    public AntiFall() {
        super("AntiFall", "Prevents falling off edges", Category.UZNY11);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.5) {
            mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
        }
    }
}