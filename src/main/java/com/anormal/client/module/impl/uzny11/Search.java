package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class Search extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Search range", 50.0, 10.0, 100.0, 5.0);
    public final BooleanSetting caveMode = new BooleanSetting("Cave Mode", "Only show exposed blocks", false);

    public Search() {
        super("Search", "Highlights specific blocks", Category.UZNY11);
        addSetting(range);
        addSetting(caveMode);
    }

    @Override
    public void onTick() {
        // Search logic
    }
}