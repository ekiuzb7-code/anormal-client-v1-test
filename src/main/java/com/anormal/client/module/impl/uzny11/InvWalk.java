package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class InvWalk extends Module {
    public final BooleanSetting creative = new BooleanSetting("Creative", "Walk in creative inventory", false);

    public InvWalk() {
        super("InvWalk", "Allows movement in inventory", Category.UZNY11);
        addSetting(creative);
    }

    @Override
    public void onTick() {
        // InvWalk logic
    }
}