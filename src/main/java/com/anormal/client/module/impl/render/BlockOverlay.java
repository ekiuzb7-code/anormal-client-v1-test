package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;

public class BlockOverlay extends Module {
    public final ColorSetting outlineColor = new ColorSetting("Outline Color", "Targeted block outline color", ColorUtils.rgba(255, 120, 0, 255));
    public final ColorSetting fillColor = new ColorSetting("Fill Color", "Targeted block face fill color", ColorUtils.rgba(255, 120, 0, 50));

    public BlockOverlay() {
        super("BlockOverlay", "Customizes block highlight colors when targeted", Category.RENDER);
        addSetting(outlineColor);
        addSetting(fillColor);
    }
}
