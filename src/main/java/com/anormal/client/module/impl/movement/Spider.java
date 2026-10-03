package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class Spider extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Climb method", "Vanilla", "Vanilla", "Smart");
    public final NumberSetting motion = new NumberSetting("Motion", "Climb upward speed", 0.3, 0.05, 0.8, 0.05);

    public Spider() {
        super("Spider", "Climbs walls like a spider", Category.MOVEMENT);
        addSetting(mode);
        addSetting(motion);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (!mc.player.horizontalCollision) return;
            if (mode.is("Smart") && mc.player.isOnGround()) return;
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, motion.getValue(), v.z);
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }
}
