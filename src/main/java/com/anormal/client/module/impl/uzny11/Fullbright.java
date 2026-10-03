package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;

public class Fullbright extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Fullbright mode", "Gamma", "Gamma", "Night Vision");
    public final NumberSetting gamma = new NumberSetting("Gamma", "Gamma value", 10.0, 1.0, 100.0, 1.0);

    private double savedGamma = 1.0;

    public Fullbright() {
        super("Fullbright", "Brightens dark areas", Category.UZNY11);
        addSetting(mode);
        addSetting(gamma);
    }

    @Override
    public void onEnable() {
        savedGamma = mc.options.getGamma().getValue();
        if (mode.is("Gamma")) mc.options.getGamma().setValue(gamma.getValue());
        else mc.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 999999, 0, false, false, false));
    }

    @Override
    public void onDisable() {
        mc.options.getGamma().setValue(savedGamma);
        mc.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION);
    }
}