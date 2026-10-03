package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "What type of night vision to apply", "Night Vision", "Night Vision", "Gamma");
    public final BooleanSetting fade = new BooleanSetting("Fade", "Fades gamma in or out between brightness areas", true);

    private double savedGamma = 1.0;
    private boolean gammaApplied = false;

    // Vanilla clamps gamma to 0..1 in setValue (invalid values reset to default),
    // so Gamma mode writes the raw field like Wurst's forceSetValue.
    private void rawGamma(double v) {
        try {
            ((com.anormal.client.mixin.GammaAccessor) (Object) mc.options.getGamma()).setRawValue(v);
        } catch (Throwable ignored) {}
    }

    public Fullbright() {
        super("Fullbright", "Brightens the entire world to 100% night vision brightness", Category.UZNY11);
        addSetting(mode);
        addSetting(fade);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mode.is("Night Vision")) {
                restoreGamma();
                mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false, false));
            } else {
                double target = 16.0;
                double current = mc.options.getGamma().getValue();
                if (!gammaApplied) {
                    savedGamma = current;
                    gammaApplied = true;
                }
                double next = fade.isEnabled() ? approach(current, target, 0.8) : target;
                if (Math.abs(next - current) > 0.001) {
                    rawGamma(next);
                }
                // If raw write didn't stick, fall back to night vision
                // so Gamma mode still brightens instead of silently doing nothing.
                try {
                    if (mc.options.getGamma().getValue() < target - 0.5) {
                        mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false, false));
                    } else if (mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                        mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDisable() {
        try {
            if (mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        } catch (Throwable ignored) {}
        restoreGamma();
    }

    private void restoreGamma() {
        if (!gammaApplied) return;
        try {
            double current = mc.options.getGamma().getValue();
            if (fade.isEnabled() && isEnabled()) return;
            double next = fade.isEnabled() ? approach(current, savedGamma, 0.8) : savedGamma;
            rawGamma(next);
            if (Math.abs(next - savedGamma) < 0.01) {
                rawGamma(savedGamma);
                gammaApplied = false;
            }
        } catch (Throwable t) {
            gammaApplied = false;
        }
    }

    private double approach(double current, double target, double step) {
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }
}
