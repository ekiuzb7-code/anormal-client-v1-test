package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class AutoFish extends Module {
    public final BooleanSetting recast = new BooleanSetting("Auto Recast", "Automatically recast", true);

    public AutoFish() {
        super("AutoFish", "Automatically fishes for you", Category.UZNY11);
        addSetting(recast);
    }

    @Override
    public void onTick() {
        // AutoFish logic
    }
}