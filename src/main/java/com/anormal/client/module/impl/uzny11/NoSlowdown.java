package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

public class NoSlowdown extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "NoSlowdown mode", "Normal", "Normal", "Soul Sand", "Honey", "Cobweb");
    public final BooleanSetting items = new BooleanSetting("Items", "No slowdown when using items", true);
    public final BooleanSetting blocks = new BooleanSetting("Blocks", "No slowdown on blocks", true);

    public NoSlowdown() {
        super("NoSlowdown", "Removes movement slowdowns", Category.UZNY11);
        addSetting(mode);
        addSetting(items);
        addSetting(blocks);
    }

    @Override
    public void onTick() {
        // NoSlowdown logic
    }
}