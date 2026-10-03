package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class KnockbackTest extends Module {
    public final NumberSetting horizontal = new NumberSetting("Horizontal", "Horizontal knockback %", 100.0, 0.0, 200.0, 1.0);
    public final NumberSetting vertical = new NumberSetting("Vertical", "Vertical knockback %", 100.0, 0.0, 200.0, 1.0);

    public KnockbackTest() {
        super("KnockbackTest", "Tests knockback values", Category.UZNY11);
        addSetting(horizontal);
        addSetting(vertical);
    }

    @Override
    public void onTick() {
        // KnockbackTest logic
    }
}