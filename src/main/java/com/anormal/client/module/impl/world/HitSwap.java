package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class HitSwap extends Module {
    public final BooleanSetting maces = new BooleanSetting("Maces", "Swap to Mace for breach damage", true);
    public final BooleanSetting axes = new BooleanSetting("Axes", "Swap to Axe to disable shields", true);

    public HitSwap() {
        super("HitSwap", "Swaps weapon right as attack connects to apply special weapon attributes", Category.WORLD);
        addSetting(maces);
        addSetting(axes);
    }
}
