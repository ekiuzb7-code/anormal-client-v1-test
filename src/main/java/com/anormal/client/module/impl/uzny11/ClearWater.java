package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class ClearWater extends Module {
    public final BooleanSetting nightVision = new BooleanSetting("Night Vision", "Applies night vision while submerged", true);
    public final NumberSetting brightness = new NumberSetting("Brightness", "Gamma applied while submerged", 10.0, 1.0, 16.0, 1.0);

    private double savedGamma = 1.0;
    private boolean gammaApplied = false;

    public ClearWater() {
        super("ClearWater", "Removes underwater fog for crystal clear underwater vision", Category.UZNY11);
        addSetting(nightVision);
        addSetting(brightness);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        boolean under = false;
        try {
            under = mc.player.isTouchingWater();
        } catch (Throwable ignored) {}
        try {
            if (under) {
                if (nightVision.isEnabled())
                    mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0, false, false, false));
                double target = brightness.getValue();
                double current = mc.options.getGamma().getValue();
                if (!gammaApplied) {
                    savedGamma = current;
                    gammaApplied = true;
                }
                if (Math.abs(target - current) > 0.001) mc.options.getGamma().setValue(target);
            } else restoreGamma();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        restoreGamma();
        try {
            if (mc.player != null && nightVision.isEnabled()) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        } catch (Throwable ignored) {}
    }

    private void restoreGamma() {
        if (!gammaApplied) return;
        try {
            mc.options.getGamma().setValue(savedGamma);
        } catch (Throwable ignored) {
        } finally {
            gammaApplied = false;
        }
    }
}
