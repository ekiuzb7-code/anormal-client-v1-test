package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class InvCleaner extends Module {
    public final BooleanSetting trash = new BooleanSetting("Trash", "Throw trash items", true);
    public final BooleanSetting best = new BooleanSetting("Keep Best", "Keep best items", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Clean delay (ticks)", 2, 0, 20, 1);

    private int ticks = 0;

    public InvCleaner() {
        super("InvCleaner", "Cleans inventory of trash items", Category.UZNY11);
        addSetting(trash);
        addSetting(best);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        // InvCleaner logic
    }
}