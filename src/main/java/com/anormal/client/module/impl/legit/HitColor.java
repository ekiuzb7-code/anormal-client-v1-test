package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;

public class HitColor extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Color entities turn when damaged", ColorUtils.rgba(255, 50, 50, 200));

    public HitColor() {
        super("HitColor", "Customizes the color tint of entities when taking damage", Category.LEGIT);
        addSetting(color);
    }
}
