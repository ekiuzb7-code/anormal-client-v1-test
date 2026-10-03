package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class Parkour extends Module {
    public Parkour() {
        super("Parkour", "Automatically jumps when reaching the edge of a block", Category.UZNY11);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isOnGround() && !mc.player.isSneaking()) {
            if (mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0, -0.5, 0).expand(-0.001, 0, -0.001)).iterator().hasNext()) {
                // Ground below
            } else if (mc.player.forwardSpeed > 0) {
                mc.player.jump();
            }
        }
    }
}
