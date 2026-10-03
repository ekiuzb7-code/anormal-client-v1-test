package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class ItemESP extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Item ESP range", 50.0, 10.0, 200.0, 5.0);
    public final BooleanSetting items = new BooleanSetting("Items", "Show dropped items", true);
    public final BooleanSetting xp = new BooleanSetting("XP Orbs", "Show XP orbs", true);

    public ItemESP() {
        super("ItemESP", "Shows dropped items through walls", Category.UZNY11);
        addSetting(range);
        addSetting(items);
        addSetting(xp);
    }

    @Override
    public void onTick() {
        // ItemESP logic
    }
}