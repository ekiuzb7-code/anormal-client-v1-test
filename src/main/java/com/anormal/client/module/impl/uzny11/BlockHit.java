package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.BooleanSetting;

public class BlockHit extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Block hit mode", "Normal", "Normal", "Predict", "Lag");
    public final NumberSetting chance = new NumberSetting("Chance", "Block hit chance %", 100.0, 0.0, 100.0, 1.0);
    public final BooleanSetting requireSword = new BooleanSetting("Sword Required", "Only with sword", true);

    public BlockHit() {
        super("BlockHit", "Automatically blocks when attacking", Category.UZNY11);
        addSetting(mode);
        addSetting(chance);
        addSetting(requireSword);
    }

    @Override
    public void onTick() {
        // BlockHit logic
    }
}