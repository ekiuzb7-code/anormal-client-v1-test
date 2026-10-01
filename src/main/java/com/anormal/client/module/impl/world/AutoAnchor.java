package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class AutoAnchor extends Module {
    public final BooleanSetting doubleAnchor = new BooleanSetting("Double Anchor", "Places second anchor immediately", true);

    public AutoAnchor() {
        super("AutoAnchor", "Automatically places, charges and detonates Respawn Anchors", Category.WORLD);
        addSetting(doubleAnchor);
    }
}
