package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class Projectiles extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Projectile render range", 100.0, 10.0, 500.0, 10.0);
    public final BooleanSetting arrows = new BooleanSetting("Arrows", "Show arrows", true);
    public final BooleanSetting pearls = new BooleanSetting("Pearls", "Show pearls", true);
    public final BooleanSetting fireballs = new BooleanSetting("Fireballs", "Show fireballs", false);

    public Projectiles() {
        super("Projectiles", "Shows projectiles in flight", Category.UZNY11);
        addSetting(range);
        addSetting(arrows);
        addSetting(pearls);
        addSetting(fireballs);
    }

    @Override
    public void onTick() {
        // Projectiles logic
    }
}