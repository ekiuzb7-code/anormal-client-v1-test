package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class Reach extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Reach distance in blocks", 3.5, 3.0, 6.0, 0.1);

    public Reach() {
        super("Reach", "Modifies attack and interaction distance (singleplayer/testing)", Category.COMBAT);
        addSetting(range);
    }
}
