package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class Indicators extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Indicator range", 100.0, 10.0, 500.0, 10.0);
    public final BooleanSetting arrows = new BooleanSetting("Arrows", "Show arrow indicators", true);
    public final BooleanSetting fireballs = new BooleanSetting("Fireballs", "Show fireball indicators", true);
    public final BooleanSetting pearls = new BooleanSetting("Pearls", "Show pearl indicators", true);

    public Indicators() {
        super("Indicators", "Shows incoming projectile indicators", Category.UZNY11);
        addSetting(range);
        addSetting(arrows);
        addSetting(fireballs);
        addSetting(pearls);
    }

    @Override
    public void onTick() {
        // Indicators logic
    }
}