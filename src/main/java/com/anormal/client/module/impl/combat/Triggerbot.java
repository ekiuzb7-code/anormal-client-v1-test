package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class Triggerbot extends Module {
    public final NumberSetting delayTicks = new NumberSetting("Delay Ticks", "Ticks between triggers", 0.0, 0.0, 10.0, 1.0);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", "Only trigger when holding weapon", true);
    public final BooleanSetting cooldownCheck = new BooleanSetting("Cooldown Check", "Wait for attack cooldown to reach 100%", true);

    private int delay = 0;

    public Triggerbot() {
        super("Triggerbot", "Automatically attacks when crosshair is hovering over a target", Category.COMBAT);
        addSetting(delayTicks);
        addSetting(weaponOnly);
        addSetting(cooldownCheck);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (delay > 0) {
            delay--;
            return;
        }

        if (cooldownCheck.isEnabled() && mc.player.getAttackCooldownProgress(0.5f) < 0.95f) {
            return;
        }

        if (mc.targetedEntity instanceof LivingEntity target && target.isAlive()) {
            if (mc.interactionManager != null) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                delay = delayTicks.getValue().intValue();
            }
        }
    }
}
