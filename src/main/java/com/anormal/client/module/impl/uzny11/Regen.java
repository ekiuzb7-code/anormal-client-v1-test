package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class Regen extends Module {
    public final NumberSetting health = new NumberSetting("Health", "Health threshold", 16, 1, 20, 1);
    public final NumberSetting delay = new NumberSetting("Delay", "Eat delay (ticks)", 4, 0, 20, 1);
    public final BooleanSetting food = new BooleanSetting("Food", "Eat food", true);
    public final BooleanSetting pots = new BooleanSetting("Potions", "Drink regen potions", true);

    private int ticks = 0;

    public Regen() {
        super("Regen", "Automatically regenerates health", Category.UZNY11);
        addSetting(health);
        addSetting(delay);
        addSetting(food);
        addSetting(pots);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.getHealth() > health.getValue()) return;

        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        // Eat food or drink potion
    }
}