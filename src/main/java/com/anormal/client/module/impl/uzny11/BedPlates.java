package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class BedPlates extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Bed plate range", 50.0, 10.0, 100.0, 5.0);
    public final BooleanSetting showBlocks = new BooleanSetting("Show Blocks", "Show blocks around bed", true);

    public BedPlates() {
        super("BedPlates", "Shows blocks around beds", Category.UZNY11);
        addSetting(range);
        addSetting(showBlocks);
    }

    @Override
    public void onTick() {
        // BedPlates logic
    }
}