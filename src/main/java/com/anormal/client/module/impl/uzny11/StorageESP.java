package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class StorageESP extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Storage ESP range", 50.0, 10.0, 200.0, 5.0);
    public final BooleanSetting chests = new BooleanSetting("Chests", "Show chests", true);
    public final BooleanSetting barrels = new BooleanSetting("Barrels", "Show barrels", true);
    public final BooleanSetting shulkers = new BooleanSetting("Shulkers", "Show shulker boxes", true);
    public final BooleanSetting enderChests = new BooleanSetting("Ender Chests", "Show ender chests", true);

    public StorageESP() {
        super("StorageESP", "Shows storage containers through walls", Category.UZNY11);
        addSetting(range);
        addSetting(chests);
        addSetting(barrels);
        addSetting(shulkers);
        addSetting(enderChests);
    }

    @Override
    public void onTick() {
        // StorageESP logic
    }
}