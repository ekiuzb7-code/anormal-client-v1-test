package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class AutoHotbar extends Module {
    public final BooleanSetting sort = new BooleanSetting("Sort", "Auto sort hotbar", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Sort delay (ticks)", 2, 0, 20, 1);

    private int ticks = 0;

    public AutoHotbar() {
        super("AutoHotbar", "Automatically organizes hotbar", Category.UZNY11);
        addSetting(sort);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        // AutoHotbar logic
    }
}