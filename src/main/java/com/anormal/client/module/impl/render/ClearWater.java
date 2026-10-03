package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class ClearWater extends Module {
    public final BooleanSetting nightVision = new BooleanSetting("Night Vision", "Applies night vision while submerged", true);
    public final NumberSetting brightness = new NumberSetting("Brightness", "Gamma applied while submerged", 10.0, 1.0, 16.0, 1.0);
    public final BooleanSetting noWaterFog = new BooleanSetting("No Water Fog", "Removes underwater fog by forcing fullbright gamma", true);

    private double savedGamma = 1.0;

    public ClearWater() {
        super("ClearWater", "Removes underwater fog for crystal clear underwater vision", Category.RENDER);
        addSetting(nightVision);
        addSetting(brightness);
        addSetting(noWaterFog);
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
                if (Math.abs(target - current) > 0.001) mc.options.getGamma().setValue(target);
                // Also force fog reduction via gamma when noWaterFog is enabled
                if (noWaterFog.isEnabled() && mc.options.getGamma().getValue() < 5.0) {
                    mc.options.getGamma().setValue(5.0);
                }
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
        try {
            mc.options.getGamma().setValue(1.0);
        } catch (Throwable ignored) {}
    }
}
