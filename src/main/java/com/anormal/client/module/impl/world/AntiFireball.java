package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.Hand;

public class AntiFireball extends Module {
    public AntiFireball() {
        super("AntiFireball", "Automatically aims and hits incoming fireballs to deflect them", Category.WORLD);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireballEntity fireball && fireball.isAlive()) {
                if (mc.player.distanceTo(fireball) <= 4.0) {
                    mc.interactionManager.attackEntity(mc.player, fireball);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }
        }
    }
}
