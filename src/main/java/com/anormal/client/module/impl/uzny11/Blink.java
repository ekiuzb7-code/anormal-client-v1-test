package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

public class Blink extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Blink mode", "Normal", "Normal", "Pulse");
    public final BooleanSetting render = new BooleanSetting("Render", "Render ghost position", true);

    public Blink() {
        super("Blink", "Blinks your position", Category.UZNY11);
        addSetting(mode);
        addSetting(render);
    }

    @Override
    public void onTick() {
        // Blink logic - would need packet manipulation
    }
}