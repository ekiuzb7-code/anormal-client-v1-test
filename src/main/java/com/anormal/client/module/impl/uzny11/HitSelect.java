package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.BooleanSetting;

public class HitSelect extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Hit selection mode", "Normal", "Normal", "Predict", "Delay");
    public final NumberSetting chance = new NumberSetting("Chance", "Hit select chance %", 100.0, 0.0, 100.0, 1.0);
    public final NumberSetting delay = new NumberSetting("Delay", "Delay between hits (ticks)", 2, 0, 10, 1);
    public final BooleanSetting preferCrit = new BooleanSetting("Prefer Crit", "Prefer critical hits", true);

    public HitSelect() {
        super("HitSelect", "Selects optimal hits for combat", Category.UZNY11);
        addSetting(mode);
        addSetting(chance);
        addSetting(delay);
        addSetting(preferCrit);
    }

    @Override
    public void onTick() {
        // HitSelect logic - would modify attack timing
    }
}