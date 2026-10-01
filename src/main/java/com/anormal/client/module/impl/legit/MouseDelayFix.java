package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class MouseDelayFix extends Module {
    public MouseDelayFix() {
        super("MouseDelayFix", "Fixes mouse polling rate aiming inaccuracy and frame delays", Category.LEGIT);
        setEnabled(true);
    }
}
