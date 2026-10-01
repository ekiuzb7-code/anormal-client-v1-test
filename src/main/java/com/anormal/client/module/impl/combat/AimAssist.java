package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Aim smooth speed", 3.0, 0.5, 10.0, 0.5);
    public final NumberSetting fov = new NumberSetting("FOV", "Field of view angle", 70.0, 10.0, 180.0, 5.0);
    public final NumberSetting distance = new NumberSetting("Distance", "Target range in blocks", 4.5, 2.0, 8.0, 0.5);
    public final BooleanSetting clickOnly = new BooleanSetting("Click Only", "Only aims while holding attack", true);

    public AimAssist() {
        super("AimAssist", "Smoothly adjusts crosshair towards nearby targets", Category.COMBAT);
        addSetting(speed);
        addSetting(fov);
        addSetting(distance);
        addSetting(clickOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (clickOnly.isEnabled() && !mc.options.attackKey.isPressed()) return;

        LivingEntity bestTarget = null;
        double closestAngle = fov.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity target && entity != mc.player && target.isAlive()) {
                double dist = mc.player.distanceTo(target);
                if (dist > distance.getValue()) continue;

                float[] rots = getRotations(target);
                float yawDiff = MathHelper.wrapDegrees(rots[0] - mc.player.getYaw());
                float pitchDiff = MathHelper.wrapDegrees(rots[1] - mc.player.getPitch());
                double angle = Math.hypot(yawDiff, pitchDiff);

                if (angle < closestAngle) {
                    closestAngle = angle;
                    bestTarget = target;
                }
            }
        }

        if (bestTarget != null) {
            float[] rots = getRotations(bestTarget);
            float yawDiff = MathHelper.wrapDegrees(rots[0] - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(rots[1] - mc.player.getPitch());

            float step = (float) (speed.getValue() * 0.8f);
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDiff, -step, step));
            mc.player.setPitch(mc.player.getPitch() + MathHelper.clamp(pitchDiff, -step / 2f, step / 2f));
        }
    }

    private float[] getRotations(LivingEntity target) {
        if (mc.player == null || target == null) return new float[]{0, 0};
        double playerX = mc.player.getX();
        double playerY = mc.player.getY() + mc.player.getStandingEyeHeight();
        double playerZ = mc.player.getZ();

        double targetX = target.getX();
        double targetY = target.getY() + (target.getHeight() * 0.75);
        double targetZ = target.getZ();

        double diffX = targetX - playerX;
        double diffY = targetY - playerY;
        double diffZ = targetZ - playerZ;

        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        return new float[]{yaw, pitch};
    }
}
