package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * AimAssist — High-precision, anticheat-safe crosshair assistance:
 * <ul>
 *   <li>Full 360-degree configurable FOV (up to 360° coverage).</li>
 *   <li>Smart multi-point aiming (Nearest bone, Head, Chest, Legs).</li>
 *   <li>Humanized easing curve with micro-slowdown near target.</li>
 *   <li>Anticheat GCD mouse-sensitivity emulation (Polar/GrimAC safe).</li>
 *   <li>Weapon check, block-break check, strafe boost.</li>
 * </ul>
 */
public class AimAssist extends Module {

    public final ModeSetting mode = new ModeSetting("Mode", "Aim smoothing curve",
            "Adaptive", "Adaptive", "Exponential", "Linear");

    public final ModeSetting aimPoint = new ModeSetting("Aim Point", "Target body part to track",
            "Nearest", "Nearest", "Head", "Chest", "Legs");

    public final NumberSetting horizontalSpeed = new NumberSetting("H-Speed", "Horizontal aim speed",
            4.0, 0.5, 15.0, 0.5);

    public final NumberSetting verticalSpeed = new NumberSetting("V-Speed", "Vertical aim speed",
            2.0, 0.0, 15.0, 0.5);

    public final NumberSetting fov = new NumberSetting("Max Angle", "Maximum field of view in degrees (up to 360°)",
            120.0, 10.0, 360.0, 5.0);

    public final NumberSetting distance = new NumberSetting("Distance", "Target range in blocks",
            4.8, 2.0, 10.0, 0.1);

    public final BooleanSetting requireMouseDown = new BooleanSetting("Require Mouse Down", "Only assist while holding left click",
            true);

    public final BooleanSetting onlyWeapon = new BooleanSetting("Only Weapon", "Only assist when holding a sword, axe or mace",
            false);

    public final BooleanSetting aimVertically = new BooleanSetting("Aim Vertically", "Enable vertical pitch tracking",
            true);

    public final BooleanSetting strafeIncrease = new BooleanSetting("Strafe Increase", "Boost speed while moving/strafing",
            true);

    public final BooleanSetting checkBlockBreak = new BooleanSetting("Check Block Break", "Pause while mining blocks",
            true);

    public final BooleanSetting gcdFix = new BooleanSetting("GCD Fix", "Quantize rotations to mouse sensitivity (bypasses anticheats)",
            true);

    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target prioritization priority",
            "Distance", "Distance", "Angle", "Health");

    public AimAssist() {
        super("AimAssist", "Smoothly adjusts crosshair towards targets (supports up to 360° FOV)", Category.COMBAT);
        addSetting(mode);
        addSetting(aimPoint);
        addSetting(horizontalSpeed);
        addSetting(verticalSpeed);
        addSetting(fov);
        addSetting(distance);
        addSetting(requireMouseDown);
        addSetting(onlyWeapon);
        addSetting(aimVertically);
        addSetting(strafeIncrease);
        addSetting(checkBlockBreak);
        addSetting(gcdFix);
        addSetting(targetMode);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Mouse click requirement
        if (requireMouseDown.isEnabled() && !mc.options.attackKey.isPressed()) return;

        // Weapon check
        if (onlyWeapon.isEnabled() && !isHoldingWeapon()) return;

        // Block break check
        if (checkBlockBreak.isEnabled() && mc.interactionManager != null && mc.options.attackKey.isPressed()
                && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return;
        }

        LivingEntity bestTarget = null;
        double bestPriority = Double.MAX_VALUE;
        double maxFov = fov.getValue();
        double maxDist = distance.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity target) || entity == mc.player || !target.isAlive()) continue;
            if (!com.anormal.client.module.impl.world.TargetFilter.isValid(target)) continue;

            double dist = mc.player.distanceTo(target);
            if (dist > maxDist) continue;

            Vec3d targetPoint = getTargetAimPoint(target);
            float[] rots = getRotationsTo(targetPoint);
            float yawDiff = MathHelper.wrapDegrees(rots[0] - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(rots[1] - mc.player.getPitch());
            double angle = Math.hypot(yawDiff, pitchDiff);

            // Allow up to 360 degrees full sphere targeting
            if (maxFov < 360.0 && angle > maxFov) continue;

            double priority = dist;
            switch (targetMode.getValue()) {
                case "Angle" -> priority = angle;
                case "Health" -> priority = target.getHealth();
                default -> priority = dist;
            }

            if (priority < bestPriority) {
                bestPriority = priority;
                bestTarget = target;
            }
        }

        if (bestTarget != null) {
            Vec3d aimVec = getTargetAimPoint(bestTarget);
            float[] rots = getRotationsTo(aimVec);

            float yawDiff = MathHelper.wrapDegrees(rots[0] - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(rots[1] - mc.player.getPitch());

            // Check if crosshair is already hovering target box
            boolean hovering = isHovering(bestTarget);

            float hSpeed = (float) (double) horizontalSpeed.getValue();
            float vSpeed = (float) (double) verticalSpeed.getValue();

            if (hovering) {
                // Micro-slowdown for maximum accuracy when on target
                hSpeed *= 0.45f;
                vSpeed *= 0.45f;
            }

            if (strafeIncrease.isEnabled() && (mc.player.sidewaysSpeed != 0 || mc.player.forwardSpeed != 0)) {
                hSpeed *= 1.25f;
            }

            float stepYaw;
            float stepPitch;

            switch (mode.getValue()) {
                case "Exponential" -> {
                    // Smooth cubic deceleration as distance to crosshair closes
                    float factor = Math.min(1.0f, Math.abs(yawDiff) / 45.0f);
                    stepYaw = hSpeed * (0.2f + 0.8f * factor * factor);
                    stepPitch = vSpeed * (0.2f + 0.8f * factor * factor);
                }
                case "Linear" -> {
                    stepYaw = hSpeed * 0.6f;
                    stepPitch = vSpeed * 0.5f;
                }
                default -> { // Adaptive
                    // Adaptive curve: fast when far, smooth when close
                    float absYaw = Math.abs(yawDiff);
                    float mult = absYaw > 60.0f ? 1.5f : absYaw > 20.0f ? 1.0f : 0.65f;
                    stepYaw = hSpeed * 0.7f * mult;
                    stepPitch = vSpeed * 0.5f * mult;
                }
            }

            float deltaYaw = MathHelper.clamp(yawDiff, -stepYaw, stepYaw);
            float deltaPitch = MathHelper.clamp(pitchDiff, -stepPitch, stepPitch);

            // Anticheat GCD sensitivity quantization
            if (gcdFix.isEnabled()) {
                float gcd = getGcd();
                deltaYaw = Math.round(deltaYaw / gcd) * gcd;
                deltaPitch = Math.round(deltaPitch / gcd) * gcd;
            }

            mc.player.setYaw(mc.player.getYaw() + deltaYaw);

            if (aimVertically.isEnabled()) {
                float targetPitch = mc.player.getPitch() + deltaPitch;
                mc.player.setPitch(MathHelper.clamp(targetPitch, -90.0f, 90.0f));
            }
        }
    }

    private Vec3d getTargetAimPoint(LivingEntity target) {
        Box box = target.getBoundingBox();
        double eyeY = mc.player.getY() + mc.player.getStandingEyeHeight();

        return switch (aimPoint.getValue()) {
            case "Head" -> new Vec3d(target.getX(), box.maxY - 0.2, target.getZ());
            case "Chest" -> new Vec3d(target.getX(), box.minY + (box.maxY - box.minY) * 0.6, target.getZ());
            case "Legs" -> new Vec3d(target.getX(), box.minY + (box.maxY - box.minY) * 0.25, target.getZ());
            default -> { // Nearest
                double closestY = MathHelper.clamp(eyeY, box.minY + 0.1, box.maxY - 0.1);
                yield new Vec3d(target.getX(), closestY, target.getZ());
            }
        };
    }

    private float[] getRotationsTo(Vec3d targetPoint) {
        if (mc.player == null) return new float[]{0, 0};

        double pX = mc.player.getX();
        double pY = mc.player.getY() + mc.player.getStandingEyeHeight();
        double pZ = mc.player.getZ();

        double dx = targetPoint.x - pX;
        double dy = targetPoint.y - pY;
        double dz = targetPoint.z - pZ;

        double dXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dXZ));

        return new float[]{yaw, pitch};
    }

    private boolean isHovering(LivingEntity target) {
        if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr) {
            return ehr.getEntity() == target;
        }
        return false;
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        String name = Registries.ITEM.getId(stack.getItem()).getPath();
        return name.contains("sword") || name.contains("axe") || name.contains("mace");
    }

    private float getGcd() {
        try {
            float sens = mc.options.getMouseSensitivity().getValue().floatValue();
            float f = sens * 0.6f + 0.2f;
            return f * f * f * 1.2f;
        } catch (Throwable t) {
            return 0.15f;
        }
    }
}
