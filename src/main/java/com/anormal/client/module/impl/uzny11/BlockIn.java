package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class BlockIn extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Block range", 6.0, 3.0, 10.0, 0.5);
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate to place", true);

    public BlockIn() {
        super("BlockIn", "Places blocks around you", Category.UZNY11);
        addSetting(range);
        addSetting(rotate);
    }

    @Override
    public void onTick() {
        // BlockIn logic
    }
}