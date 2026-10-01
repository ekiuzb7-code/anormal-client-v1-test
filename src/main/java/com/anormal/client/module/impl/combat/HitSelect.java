package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

public class HitSelect extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Hit selection chance %", 90.0, 10.0, 100.0, 5.0);
    public final ModeSetting mode = new ModeSetting("Mode", "Selection mode", "Active", "Active", "Pause");

    public HitSelect() {
        super("HitSelect", "Pauses attacks intelligently to gain combo advantage and reduce knockback", Category.COMBAT);
        addSetting(chance);
        addSetting(mode);
    }
}
