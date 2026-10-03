package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Aiming behavior mode", "Adaptive", "Simple", "Adaptive");
    public final NumberSetting horizontalSpeed = new NumberSetting("H-Speed", "Horizontal aim speed", 3.5, 0.5, 10.0, 0.5);
    public final NumberSetting verticalSpeed = new NumberSetting("V-Speed", "Vertical aim speed", 1.5, 0.0, 10.0, 0.5);
    public final NumberSetting fov = new NumberSetting("Max Angle", "Maximum angle from crosshair", 70.0, 10.0, 180.0, 5.0);
    public final NumberSetting distance = new NumberSetting("Distance", "Target range in blocks", 4.5, 2.0, 8.0, 0.5);
    public final BooleanSetting requireMouseDown = new BooleanSetting("Require Mouse Down", "Only aims while holding left click", true);
    public final BooleanSetting aimVertically = new BooleanSetting("Aim Vertically", "Enables vertical aim adjustment", true);
    public final BooleanSetting strafeIncrease = new BooleanSetting("Strafe Increase", "Increases speed while strafing", true);
    public final BooleanSetting checkBlockBreak = new BooleanSetting("Check Block Break", "Pauses while breaking blocks", true);
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target prioritization", "Distance", "Distance", "Yaw", "Health");

    public AimAssist() {
        super("AimAssist", "Smoothly adjusts crosshair towards nearby targets", Category.UZNY11);
        addSetting(mode);
        addSetting(horizontalSpeed);
        addSetting(verticalSpeed);
        addSetting(fov);
        addSetting(distance);
        addSetting(requireMouseDown);
        addSetting(aimVertically);
        addSetting(strafeIncrease);
        addSetting(checkBlockBreak);
        addSetting(targetMode);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (requireMouseDown.isEnabled() && !mc.options.attackKey.isPressed()) return;
        if (checkBlockBreak.isEnabled() && mc.interactionManager != null && mc.options.attackKey.isPressed() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) return;

        LivingEntity bestTarget = null;
        double bestPriority = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity target && entity != mc.player && target.isAlive()) {
                double dist = mc.player.distanceTo(target);
                if (dist > distance.getValue()) continue;

                float[] rots = getRotations(target);
                float yawDiff = MathHelper.wrapDegrees(rots[0] - mc.player.getYaw());
                float pitchDiff = MathHelper.wrapDegrees(rots[1] - mc.player.getPitch());
                double angle = Math.hypot(yawDiff, pitchDiff);

                if (angle > fov.getValue()) continue;

                double priority = dist;
                if ("Yaw".equals(targetMode.getValue())) {
                    priority = angle;
                } else if ("Health".equals(targetMode.getValue())) {
                    priority = target.getHealth();
                }

                if (priority < bestPriority) {
                    bestPriority = priority;
                    bestTarget = target;
                }
            }
        }

        if (bestTarget != null) {
            float[] rots = getRotations(bestTarget);
            float yawDiff = MathHelper.wrapDegrees(rots[0] - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(rots[1] - mc.player.getPitch());

            float hSpeed = horizontalSpeed.getValue().floatValue();
            if (strafeIncrease.isEnabled() && (mc.player.sidewaysSpeed != 0 || mc.player.forwardSpeed != 0)) {
                hSpeed *= 1.25f;
            }

            float stepYaw = hSpeed * 0.7f;
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDiff, -stepYaw, stepYaw));

            if (aimVertically.isEnabled()) {
                float stepPitch = verticalSpeed.getValue().floatValue() * 0.5f;
                mc.player.setPitch(mc.player.getPitch() + MathHelper.clamp(pitchDiff, -stepPitch, stepPitch));
            }
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
