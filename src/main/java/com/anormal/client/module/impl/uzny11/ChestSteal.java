package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class ChestSteal extends Module {
    public final BooleanSetting bestOnly = new BooleanSetting("Best Only", "Only take better items", false);
    public final BooleanSetting keepOpen = new BooleanSetting("Keep Open", "Keep chest open", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Click delay (ticks)", 2, 0, 20, 1);

    private int ticks = 0;

    public ChestSteal() {
        super("ChestSteal", "Automatically steals from chests", Category.UZNY11);
        addSetting(bestOnly);
        addSetting(keepOpen);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen == null) return;

        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        // ChestSteal logic
    }
}