package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class AntiFall extends Module {
    public final BooleanSetting speedCheck = new BooleanSetting("Speed Check", "Ignore when Speed enabled", false);
    public final NumberSetting fallDist = new NumberSetting("Fall Dist", "Fall blocks before catch", 2.0, 0.1, 5.0, 0.1);

    public AntiFall() {
        super("AntiFall", "Prevents falling into void", Category.UZNY11);
        addSetting(speedCheck); addSetting(fallDist);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (mc.player.isOnGround() || mc.player.isGliding()) return;
            if (speedCheck.getValue() && mc.player.forwardSpeed != 0 && mc.player.isSprinting()) return;
            if (mc.player.getY() > -5) return;
            if (mc.player.fallDistance < fallDist.getValue()) return;
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x * 0.2, Math.max(v.y, 0.5), v.z * 0.2);
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    private int catches = 0;

    public int getCatches() {
        return catches;
    }

    @Override
    public void onDisable() {
        catches = 0;
    }
}
