package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class AutoMace extends Module {
    public final BooleanSetting smashOnly = new BooleanSetting("Smash Only", "Only swaps to Mace when falling for smash", true);
    public final BooleanSetting autoUnequipElytra = new BooleanSetting("Auto Chestplate", "Swaps to chestplate for smash", true);

    public AutoMace() {
        super("AutoMace", "Automatically selects Mace and times smash attacks while falling", Category.WORLD);
        addSetting(smashOnly);
        addSetting(autoUnequipElytra);
    }
}
