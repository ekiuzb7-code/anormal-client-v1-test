package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;

public class HitSelect extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Chance an attack is interrupted", 70.0, 0.0, 100.0, 1.0);
    public final ModeSetting mode = new ModeSetting("Mode", "Static pause or dynamic active selection", "Active", "Pause", "Active");
    public final ModeSetting preference = new ModeSetting("Preference", "Benefit favored in Active mode", "KB reduction", "KB reduction", "Critical hits");
    public final NumberSetting pauseTicks = new NumberSetting("Pause Ticks", "Sprint pause duration after a selected hit", 8.0, 2.0, 30.0, 1.0);
    private int pauseLeft;

    public HitSelect() {
        super("HitSelect", "Interrupts attacks via sprint resets for movement knockback and crit advantage", Category.COMBAT);
        addSetting(chance);
        addSetting(mode);
        addSetting(preference);
        addSetting(pauseTicks);
    }

    @Override
    public void onEnable() {
        pauseLeft = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (pauseLeft > 0) {
            pauseLeft--;
            mc.player.setSprinting(false);
            return;
        }
        if (!(mc.targetedEntity instanceof LivingEntity target) || !target.isAlive() || target == mc.player) return;
        if (mc.player.distanceTo(target) > 4.5) return;
        if (target.hurtTime <= 0) return;
        if (!mc.player.isSprinting()) return;
        if (Math.random() * 100.0 >= chance.getValue()) return;
        if (mode.is("Pause")) {
            trigger();
            return;
        }
        if (preference.is("KB reduction")) {
            if (mc.player.hurtTime > 0 || mc.player.forwardSpeed != 0) trigger();
        } else {
            if (!mc.player.isOnGround() && !mc.player.isGliding() && mc.player.getVelocity().y < -0.2) trigger();
        }
    }

    private void trigger() {
        mc.player.setSprinting(false);
        pauseLeft = Math.max(1, pauseTicks.getValue().intValue());
    }
}
