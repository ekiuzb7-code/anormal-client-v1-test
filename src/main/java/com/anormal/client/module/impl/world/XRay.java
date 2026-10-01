package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class XRay extends Module {
    public final NumberSetting opacity = new NumberSetting("Opacity", "Opacity of non-ore blocks", 20.0, 0.0, 100.0, 5.0);

    public XRay() {
        super("XRay", "Makes common blocks transparent to easily locate underground ores", Category.WORLD);
        addSetting(opacity);
    }

    @Override
    public void onEnable() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }
}
