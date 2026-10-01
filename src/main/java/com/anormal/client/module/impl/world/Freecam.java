package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class Freecam extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Flight camera speed", 1.5, 0.5, 5.0, 0.5);

    public Freecam() {
        super("Freecam", "Detaches camera from player allowing free flight exploration", Category.WORLD);
        addSetting(speed);
    }
}
