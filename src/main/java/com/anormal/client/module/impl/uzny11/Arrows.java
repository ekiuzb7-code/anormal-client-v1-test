package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class Arrows extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Arrow indicator range", 100.0, 10.0, 500.0, 10.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance to arrows", true);

    public Arrows() {
        super("Arrows", "Shows incoming arrows", Category.UZNY11);
        addSetting(range);
        addSetting(showDistance);
    }

    @Override
    public void onTick() {
        // Arrows logic
    }
}