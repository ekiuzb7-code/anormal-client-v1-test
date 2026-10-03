package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class Trajectories extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Trajectory range", 50.0, 10.0, 100.0, 5.0);
    public final BooleanSetting arrows = new BooleanSetting("Arrows", "Show arrow trajectories", true);
    public final BooleanSetting pearls = new BooleanSetting("Pearls", "Show pearl trajectories", true);
    public final BooleanSetting fireballs = new BooleanSetting("Fireballs", "Show fireball trajectories", false);

    public Trajectories() {
        super("Trajectories", "Shows projectile trajectories", Category.UZNY11);
        addSetting(range);
        addSetting(arrows);
        addSetting(pearls);
        addSetting(fireballs);
    }

    @Override
    public void onTick() {
        // Trajectories logic
    }
}