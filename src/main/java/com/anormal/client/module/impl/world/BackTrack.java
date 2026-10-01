package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class BackTrack extends Module {
    public final NumberSetting latency = new NumberSetting("Latency (ms)", "Target position delay in ms", 100.0, 20.0, 500.0, 10.0);

    public BackTrack() {
        super("BackTrack", "Freezes target hitbox position momentarily for easier combos", Category.WORLD);
        addSetting(latency);
    }
}
