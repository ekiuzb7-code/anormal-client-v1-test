package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class ClearWater extends Module {
    public final NumberSetting gamma = new NumberSetting("Gamma", "Underwater gamma", 10.0, 1.0, 100.0, 1.0);
    public final BooleanSetting nightVision = new BooleanSetting("Night Vision", "Underwater night vision", true);
    public final BooleanSetting noFog = new BooleanSetting("No Fog", "Remove water fog", true);

    private double savedGamma = 1.0;

    public ClearWater() {
        super("ClearWater", "Clear underwater vision", Category.UZNY11);
        addSetting(gamma);
        addSetting(nightVision);
        addSetting(noFog);
    }

    @Override
    public void onEnable() {
        savedGamma = mc.options.getGamma().getValue();
        mc.options.getGamma().setValue(gamma.getValue());
    }

    @Override
    public void onDisable() {
        mc.options.getGamma().setValue(savedGamma);
    }

    @Override
    public void onTick() {
        if (mc.player.isTouchingWater()) {
            mc.options.getGamma().setValue(gamma.getValue());
        }
    }
}