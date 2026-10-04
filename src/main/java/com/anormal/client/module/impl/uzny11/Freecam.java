package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class Freecam extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Freecam speed", 1.5, 0.5, 5.0, 0.1);
    public final BooleanSetting noClip = new BooleanSetting("No Clip", "Phase through blocks", true);

    private double camX, camY, camZ;
    private float camYaw, camPitch;

    public Freecam() {
        super("Freecam", "Free camera mode", Category.UZNY11);
        addSetting(speed);
        addSetting(noClip);
    }

    @Override
    public void onEnable() {
        camX = mc.player.getX();
        camY = mc.player.getY();
        camZ = mc.player.getZ();
        camYaw = mc.player.getYaw();
        camPitch = mc.player.getPitch();
    }

    @Override
    public void onTick() {
        // Freecam logic - would need mixin for camera
    }
}