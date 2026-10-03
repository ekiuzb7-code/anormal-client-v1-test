package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class AutoPearl extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Pearl throw range", 50.0, 10.0, 100.0, 1.0);
    public final NumberSetting health = new NumberSetting("Health", "Min health to pearl", 10, 1, 20, 1);
    public final BooleanSetting auto = new BooleanSetting("Auto Pearl", "Auto pearl on low health", false);

    public AutoPearl() {
        super("AutoPearl", "Automatically throws ender pearls", Category.UZNY11);
        addSetting(range);
        addSetting(health);
        addSetting(auto);
    }

    @Override
    public void onTick() {
        // AutoPearl logic
    }
}