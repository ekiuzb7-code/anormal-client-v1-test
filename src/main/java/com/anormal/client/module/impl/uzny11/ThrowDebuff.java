package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

public class ThrowDebuff extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Throw mode", "All", "All", "One Each", "First");
    public final BooleanSetting harming = new BooleanSetting("Harming", "Throw harming potions", true);
    public final BooleanSetting weakness = new BooleanSetting("Weakness", "Throw weakness potions", true);
    public final BooleanSetting poison = new BooleanSetting("Poison", "Throw poison potions", true);
    public final BooleanSetting slowness = new BooleanSetting("Slowness", "Throw slowness potions", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Throw delay (ticks)", 4, 0, 20, 1);

    private int ticks = 0;

    public ThrowDebuff() {
        super("ThrowDebuff", "Throws debuff potions", Category.UZNY11);
        addSetting(mode);
        addSetting(harming);
        addSetting(weakness);
        addSetting(poison);
        addSetting(slowness);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        // ThrowDebuff logic
    }
}