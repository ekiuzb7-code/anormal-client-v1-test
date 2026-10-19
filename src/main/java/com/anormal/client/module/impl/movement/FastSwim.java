package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Swim faster by scaling the horizontal velocity while in water.
 *
 * <p>Vanilla swim speed is a hard cap applied by {@code Entity.travel} every tick, so simply
 * adding speed once is not enough - the horizontal component is rescaled every tick while you are
 * in the liquid, which is what makes the difference actually stick.</p>
 */
public class FastSwim extends Module {

    public final NumberSetting speed = new NumberSetting("Speed",
            "Horizontal speed multiplier in water", 1.6, 1.0, 4.0, 0.1);
    public final BooleanSetting onlySprinting = new BooleanSetting("Only Sprinting",
            "Only apply the boost while sprinting", false);
    public final BooleanSetting includeLava = new BooleanSetting("Include Lava",
            "Also apply in lava", false);
    public final BooleanSetting verticalBoost = new BooleanSetting("Vertical Boost",
            "Push you upwards while holding jump in water", true);
    public final NumberSetting boost = new NumberSetting("Boost",
            "Upward velocity added while jumping out of water", 0.12, 0.0, 0.5, 0.01);
    public final BooleanSetting onlySurface = new BooleanSetting("Only Near Surface",
            "Only apply while the head is close to the surface", false);

    public FastSwim() {
        super("FastSwim", "Swims faster in water and lava", Category.MOVEMENT);
        addSetting(speed);
        addSetting(onlySprinting);
        addSetting(includeLava);
        addSetting(verticalBoost);
        addSetting(boost);
        addSetting(onlySurface);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        try {
            boolean water = mc.player.isTouchingWater();
            boolean lava = includeLava.isEnabled() && mc.player.isInLava();
            if (!water && !lava) return;
            if (onlySprinting.isEnabled() && !mc.player.isSprinting()) return;
            if (onlySurface.isEnabled() && mc.player.isSubmergedInWater()) return;
            if (!hasMovementInput()) return;

            Vec3d velocity = mc.player.getVelocity();
            double factor = speed.getValue();
            double y = velocity.y;

            if (verticalBoost.isEnabled() && mc.options.jumpKey.isPressed()) {
                y = Math.max(y, boost.getValue());
            }

            mc.player.setVelocity(velocity.x * factor, y, velocity.z * factor);
        } catch (Throwable ignored) {
        }
    }

    private boolean hasMovementInput() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }
}
