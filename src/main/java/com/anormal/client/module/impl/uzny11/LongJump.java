package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

public class LongJump extends Module {
    public final NumberSetting boost = new NumberSetting("Boost", "Jump boost factor", 4.1, 3.0, 5.0, 0.1);
    public final BooleanSetting autoDisable = new BooleanSetting("Toggle", "Disable after landing", true);

    public LongJump() {
        super("LongJump", "Long jump boost", Category.UZNY11);
        addSetting(boost); addSetting(autoDisable);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.isOnGround() && mc.player.forwardSpeed != 0 && mc.options.jumpKey.isPressed()) {
                double rad = Math.toRadians(mc.player.getYaw());
                double b = (boost.getValue() - 3.0) * 0.35 + 0.7;
                Vec3d v = mc.player.getVelocity();
                mc.player.setVelocity(-Math.sin(rad) * b, 0.42, Math.cos(rad) * b);
            } else if (!mc.player.isOnGround() && v_len() > 0.1) {
                Vec3d v = mc.player.getVelocity();
                mc.player.setVelocity(v.x * 1.005, v.y, v.z * 1.005);
            }
            if (autoDisable.getValue() && mc.player.isOnGround() && mc.player.getVelocity().lengthSquared() < 0.05 && !mc.options.jumpKey.isPressed()) return;
        } catch (Throwable ignored) {}
    }

    private double v_len() {
        try { return mc.player.getVelocity().length(); } catch (Throwable t) { return 0; }
    }

    private int jumps = 0;

    public int getJumps() {
        return jumps;
    }

    @Override
    public void onDisable() {
        jumps = 0;
    }
}
