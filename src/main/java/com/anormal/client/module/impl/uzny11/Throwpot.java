package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

public class Throwpot extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Throw mode", "Dynamic", "Dynamic", "Single");
    public final BooleanSetting pots = new BooleanSetting("Potions", "Throw healing potions", true);
    public final BooleanSetting soup = new BooleanSetting("Soup", "Throw mushroom soup", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Throw delay (ticks)", 2, 0, 20, 1);

    public Throwpot() {
        super("Throwpot", "Throws/consumes healing items", Category.UZNY11);
        addSetting(mode);
        addSetting(pots);
        addSetting(soup);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        // Throwpot logic
    }
}