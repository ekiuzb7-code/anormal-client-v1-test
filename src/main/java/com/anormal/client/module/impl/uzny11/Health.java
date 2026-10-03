package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class Health extends Module {
    public final NumberSetting scale = new NumberSetting("Scale", "Health display scale", 1.0, 0.5, 2.0, 0.1);
    public final BooleanSetting hearts = new BooleanSetting("Hearts", "Show as hearts", true);

    public Health() {
        super("Health", "Shows health display", Category.UZNY11);
        addSetting(scale);
        addSetting(hearts);
    }

    @Override
    public void onTick() {
        // Health display logic
    }
}