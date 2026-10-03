package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class Criticals extends Module {
    public final NumberSetting height = new NumberSetting("Height", "Auto-hop height", 0.42, 0.1, 0.42, 0.01);
    public final NumberSetting range = new NumberSetting("Range", "Only hop with enemy in range", 4.0, 1.0, 6.0, 0.5);
    public final BooleanSetting optimizeCooldown = new BooleanSetting("Optimize Cooldown", "Hop timed to attack cooldown", true);
    public final BooleanSetting onlyGround = new BooleanSetting("Only Ground", "Hop only from ground", true);

    public Criticals() {
        super("Criticals", "Auto-hops so hits land as criticals", Category.COMBAT);
        addSetting(height);
        addSetting(range);
        addSetting(optimizeCooldown);
        addSetting(onlyGround);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (onlyGround.isEnabled() && !mc.player.isOnGround()) return;
        if (!mc.player.isOnGround()) return;
        if (mc.player.isGliding() || mc.player.isTouchingWater() || mc.player.isInLava()) return;
        if (optimizeCooldown.isEnabled() && mc.player.getAttackCooldownProgress(0.5f) < 0.9f) return;

        double r = range.getValue();
        boolean enemyNear = false;
        try {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof LivingEntity living && e != mc.player && living.isAlive()) {
                    if (mc.player.distanceTo(living) <= r) {
                        enemyNear = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            return;
        }
        if (!enemyNear) return;
        try {
            mc.player.getVelocity();
            mc.player.setVelocity(mc.player.getVelocity().x, Math.min(0.42, Math.max(0.1, height.getValue())), mc.player.getVelocity().z);
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }
}
