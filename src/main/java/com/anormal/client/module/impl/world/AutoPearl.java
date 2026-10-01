package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;

public class AutoPearl extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Chase mode", "On Bind", "On Bind", "Aggro");

    public AutoPearl() {
        super("AutoPearl", "Detects and throws pearls to chase escaping enemies", Category.WORLD);
        addSetting(mode);
    }
}
