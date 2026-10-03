package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class SpawnerFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Spawner finder range", 50.0, 10.0, 200.0, 5.0);
    public final BooleanSetting showType = new BooleanSetting("Show Type", "Show spawner type", true);

    public SpawnerFinder() {
        super("SpawnerFinder", "Finds mob spawners", Category.UZNY11);
        addSetting(range);
        addSetting(showType);
    }

    @Override
    public void onTick() {
        // SpawnerFinder logic
    }
}