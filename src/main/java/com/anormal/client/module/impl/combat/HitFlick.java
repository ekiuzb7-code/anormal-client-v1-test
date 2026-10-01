package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class HitFlick extends Module {
    public final NumberSetting angle = new NumberSetting("Angle", "Flick angle offset", 90.0, 0.0, 360.0, 10.0);
    public final NumberSetting chance = new NumberSetting("Chance", "HitFlick chance %", 80.0, 10.0, 100.0, 5.0);

    public HitFlick() {
        super("HitFlick", "Flicks crosshair to alter knockback trajectory", Category.COMBAT);
        addSetting(angle);
        addSetting(chance);
    }
}
