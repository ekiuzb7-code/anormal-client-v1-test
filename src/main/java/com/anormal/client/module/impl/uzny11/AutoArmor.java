package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class AutoArmor extends Module {
    public final BooleanSetting openInv = new BooleanSetting("Open Inventory", "Open inventory to swap", true);
    public final BooleanSetting durability = new BooleanSetting("Durability", "Consider durability", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Swap delay (ticks)", 4, 0, 20, 1);

    private int ticks = 0;

    public AutoArmor() {
        super("AutoArmor", "Automatically equips best armor", Category.UZNY11);
        addSetting(openInv);
        addSetting(durability);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        // AutoArmor logic
    }
}