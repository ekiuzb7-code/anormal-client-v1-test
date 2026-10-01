package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;

public class Tracers extends Module {
    public final ColorSetting color = new ColorSetting("Color", "Tracer line color", ColorUtils.rgba(255, 200, 50, 200));

    public Tracers() {
        super("Tracers", "Renders directional lines from crosshair towards nearby targets", Category.RENDER);
        addSetting(color);
    }
}
