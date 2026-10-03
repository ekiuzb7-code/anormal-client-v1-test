package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class PropHunt extends Module {
    public final NumberSetting range = new NumberSetting("Range", "PropHunt range", 30.0, 5.0, 100.0, 5.0);
    public final BooleanSetting highlight = new BooleanSetting("Highlight", "Highlight fake blocks", true);

    public PropHunt() {
        super("PropHunt", "Finds prop hunt players", Category.UZNY11);
        addSetting(range);
        addSetting(highlight);
    }

    @Override
    public void onTick() {
        // PropHunt logic
    }
}