package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class BedBreaker extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Bed break range", 6.0, 3.0, 10.0, 0.1);
    public final BooleanSetting walls = new BooleanSetting("Through Walls", "Break through walls", true);

    public BedBreaker() {
        super("BedBreaker", "Breaks beds through walls", Category.UZNY11);
        addSetting(range);
        addSetting(walls);
    }

    @Override
    public void onTick() {
        // BedBreaker logic
    }
}