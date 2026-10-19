package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

/**
 * Fast ladder / vine climbing, ported from Wurst's FastLadder.
 *
 * <p>While on a climbable block with a wall in front, the upward velocity is simply replaced with
 * a constant. Vanilla caps ladder climbing at 0.2 blocks per tick; 0.2872 is the value that keeps
 * you moving up at full sprint speed without overshooting the ladder.</p>
 */
public class FastLadder extends Module {

    public final NumberSetting climbSpeed = new NumberSetting("Climb Speed",
            "Upward velocity per tick while climbing", 0.2872, 0.1, 0.6, 0.01);
    public final BooleanSetting requireInput = new BooleanSetting("Require Input",
            "Only climb fast while pressing a movement key", true);
    public final BooleanSetting requireWall = new BooleanSetting("Require Wall",
            "Only climb fast when pressed against the ladder", true);
    public final BooleanSetting onlyUpwards = new BooleanSetting("Only Upwards",
            "Do not touch the velocity while descending", false);

    public FastLadder() {
        super("FastLadder", "Climbs ladders and vines faster", Category.MOVEMENT);
        addSetting(climbSpeed);
        addSetting(requireInput);
        addSetting(requireWall);
        addSetting(onlyUpwards);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        try {
            if (!mc.player.isClimbing()) return;
            if (requireWall.isEnabled() && !mc.player.horizontalCollision) return;
            if (requireInput.isEnabled() && !hasMovementInput()) return;

            Vec3d velocity = mc.player.getVelocity();
            double speed = climbSpeed.getValue();
            if (onlyUpwards.isEnabled() && velocity.y < -0.05) return;

            mc.player.setVelocity(velocity.x, speed, velocity.z);
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
