package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.util.math.Vec3d;

public class NoFall extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "NoFall method", "Normal", "Normal", "AntiCheat");

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.UZNY11);
        addSetting(mode);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.isOnGround() || mc.player.isGliding() || mc.player.isTouchingWater()) return;
            if (mc.player.fallDistance < 3.0f) return;
            if (mode.is("AntiCheat")) {
                Vec3d v = mc.player.getVelocity();
                if (v.y < -0.5) mc.player.setVelocity(v.x, -0.15, v.z);
                mc.player.fallDistance = 0.0f;
            } else {
                mc.player.fallDistance = 0.0f;
            }
        } catch (Throwable ignored) {}
    }

    private int saves = 0;

    public int getSaves() {
        return saves;
    }

    @Override
    public void onDisable() {
        saves = 0;
    }
}
