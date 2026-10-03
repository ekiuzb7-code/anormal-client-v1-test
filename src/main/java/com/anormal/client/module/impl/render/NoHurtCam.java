package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;

public class NoHurtCam extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Tilt is removed by the camera hook, flash by zeroing hurt time", "Both", "Both", "Tilt", "Flash");

    public NoHurtCam() {
        super("NoHurtCam", "Removes camera screen shake and tilt when taking damage", Category.RENDER);
        addSetting(mode);

    }

    @Override
    public void onTick() {
        if (mc.player == null || mode.is("Tilt")) return;
        try {
            if (mc.player.hurtTime > 0) mc.player.hurtTime = 0;
        } catch (Throwable ignored) {}
    }
}
