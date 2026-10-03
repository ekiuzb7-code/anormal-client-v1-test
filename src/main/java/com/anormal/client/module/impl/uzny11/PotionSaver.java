package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class PotionSaver extends Module {
    public final NumberSetting duration = new NumberSetting("Duration", "Minimum duration to save (seconds)", 30, 5, 300, 5);
    public final BooleanSetting splash = new BooleanSetting("Splash Potions", "Save splash potions", true);
    public final BooleanSetting drinkable = new BooleanSetting("Drinkable Potions", "Save drinkable potions", true);

    public PotionSaver() {
        super("PotionSaver", "Prevents wasting potions with short duration", Category.UZNY11);
        addSetting(duration);
        addSetting(splash);
        addSetting(drinkable);
    }

    @Override
    public void onTick() {
        // PotionSaver logic - prevents using potions with low duration
    }
}