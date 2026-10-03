package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class SafeWalk extends Module {
    public final NumberSetting height = new NumberSetting("Height", "Safe walk height", 1.0, 0.5, 2.0, 0.1);
    public final BooleanSetting onlyGround = new BooleanSetting("Only Ground", "Only when on ground", true);

    public SafeWalk() {
        super("SafeWalk", "Prevents falling off edges", Category.UZNY11);
        addSetting(height);
        addSetting(onlyGround);
    }

    @Override
    public void onTick() {
        // SafeWalk logic
    }
}