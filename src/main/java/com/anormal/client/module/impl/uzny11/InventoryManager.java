package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class InventoryManager extends Module {
    public final BooleanSetting armor = new BooleanSetting("Armor", "Manage armor", true);
    public final BooleanSetting hotbar = new BooleanSetting("Hotbar", "Manage hotbar", true);
    public final BooleanSetting cleaner = new BooleanSetting("Cleaner", "Throw trash items", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Action delay (ticks)", 4, 0, 20, 1);

    private int ticks = 0;

    public InventoryManager() {
        super("InventoryManager", "Complete inventory management", Category.UZNY11);
        addSetting(armor);
        addSetting(hotbar);
        addSetting(cleaner);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        // InventoryManager logic
    }
}