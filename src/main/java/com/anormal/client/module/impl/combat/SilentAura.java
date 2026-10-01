package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class SilentAura extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Target acquisition range", 4.0, 2.0, 6.0, 0.2);
    public final BooleanSetting cooldownCheck = new BooleanSetting("1.9+ Cooldown", "Wait for weapon attack cooldown", true);
    public final BooleanSetting playersOnly = new BooleanSetting("Players Only", "Only attack player entities", false);

    public SilentAura() {
        super("SilentAura", "Attacks nearby entities without snapping camera view", Category.COMBAT);
        addSetting(range);
        addSetting(cooldownCheck);
        addSetting(playersOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (cooldownCheck.isEnabled() && mc.player.getAttackCooldownProgress(0.5f) < 0.95f) {
            return;
        }

        LivingEntity closest = null;
        double closestDist = range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living && entity != mc.player && living.isAlive()) {
                double dist = mc.player.distanceTo(living);
                if (dist <= closestDist) {
                    closestDist = dist;
                    closest = living;
                }
            }
        }

        if (closest != null && mc.interactionManager != null) {
            mc.interactionManager.attackEntity(mc.player, closest);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }
}
