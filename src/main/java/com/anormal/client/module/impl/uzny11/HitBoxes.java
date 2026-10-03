package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class HitBoxes extends Module {
    public final NumberSetting expandAmount = new NumberSetting("Expand amount", "Hitbox expansion", 0.35, 0.0, 1.0, 0.01);

    public HitBoxes() {
        super("HitBoxes", "Expands entity hitboxes", Category.UZNY11);
        addSetting(expandAmount);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        try {
            if (!mc.options.attackKey.isPressed()) return;
            double reach = 3.0 + expandAmount.getValue() * 2.0;
            LivingEntity best = null;
            double bestDist = reach;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) continue;
                double d = mc.player.distanceTo(le);
                if (d < bestDist) { bestDist = d; best = le; }
            }
            if (best != null && bestDist > 3.0) {
                mc.interactionManager.attackEntity(mc.player, best);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        } catch (Throwable ignored) {}
    }

    private int hits = 0;

    public int getHits() {
        return hits;
    }

    @Override
    public void onDisable() {
        hits = 0;
    }
}
