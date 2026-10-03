package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class Refill extends Module {
    public final NumberSetting health = new NumberSetting("Health", "Health threshold", 10, 1, 20, 1);
    public final BooleanSetting soup = new BooleanSetting("Soup", "Refill mushroom soup", true);
    public final BooleanSetting pots = new BooleanSetting("Potions", "Refill healing potions", true);

    public Refill() {
        super("Refill", "Refills hotbar with healing items", Category.UZNY11);
        addSetting(health);
        addSetting(soup);
        addSetting(pots);
    }

    @Override
    public void onTick() {
        // Refill logic
    }
}