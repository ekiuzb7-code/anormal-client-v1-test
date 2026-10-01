package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class Velocity extends Module {
    public final NumberSetting horizontal = new NumberSetting("Horizontal", "Horizontal knockback %", 0.0, 0.0, 100.0, 5.0);
    public final NumberSetting vertical = new NumberSetting("Vertical", "Vertical knockback %", 0.0, 0.0, 100.0, 5.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Knockback reduction chance %", 100.0, 10.0, 100.0, 5.0);

    public Velocity() {
        super("Velocity", "Reduces or eliminates knockback taken from damage", Category.COMBAT);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(chance);
    }
}
