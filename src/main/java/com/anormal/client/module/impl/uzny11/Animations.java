package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.BooleanSetting;

public class Animations extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Animation mode", "Old", "Old", "New", "Smooth", "None");
    public final BooleanSetting eat = new BooleanSetting("Eat", "Custom eat animation", true);
    public final BooleanSetting drink = new BooleanSetting("Drink", "Custom drink animation", true);
    public final BooleanSetting block = new BooleanSetting("Block", "Custom block animation", true);

    public Animations() {
        super("Animations", "Custom player animations", Category.UZNY11);
        addSetting(mode);
        addSetting(eat);
        addSetting(drink);
        addSetting(block);
    }

    @Override
    public void onTick() {
        // Animations logic
    }
}