package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

public class AimAssist extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Aim mode", "Normal", "Normal", "Silent", "Trigger");
    public final NumberSetting range = new NumberSetting("Range", "Target range", 4.5, 2.0, 6.0, 0.1);
    public final NumberSetting fov = new NumberSetting("FOV", "Field of view", 90.0, 10.0, 360.0, 1.0);
    public final NumberSetting speed = new NumberSetting("Speed", "Aim speed", 20.0, 1.0, 100.0, 1.0);
    public final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed", "Vertical aim speed", 10.0, 1.0, 50.0, 1.0);
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Target mobs", false);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Target invisibles", false);
    public final BooleanSetting teams = new BooleanSetting("Teams", "Ignore teammates", true);
    public final BooleanSetting walls = new BooleanSetting("Through Walls", "Aim through walls", false);
    public final BooleanSetting click = new BooleanSetting("Require Click", "Only aim while clicking", true);
    public final BooleanSetting smooth = new BooleanSetting("Smooth", "Smooth aim", true);

    private LivingEntity target;

    public AimAssist() {
        super("AimAssist", "Aims at nearby entities", Category.UZNY11);
        addSetting(mode);
        addSetting(range);
        addSetting(fov);
        addSetting(speed);
        addSetting(verticalSpeed);
        addSetting(players);
        addSetting(mobs);
        addSetting(invisibles);
        addSetting(teams);
        addSetting(walls);
        addSetting(click);
        addSetting(smooth);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (click.isEnabled() && !mc.options.attackKey.isPressed()) return;

        target = findTarget();
        if (target == null) return;

        if (getAngleTo(target) > fov.getValue()) return;

        if (mode.is("Silent")) {
            // Silent aim - would need packet manipulation
            faceTarget(target);
        } else if (mode.is("Trigger")) {
            if (getAngleTo(target) < 3.0) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            } else {
                faceTarget(target);
            }
        } else {
            faceTarget(target);
        }
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) {
                if (!mobs.isEnabled()) continue;
            } else {
                if (!players.isEnabled()) continue;
                if (teams.isEnabled() && mc.player.isTeammate((net.minecraft.entity.player.PlayerEntity) entity)) continue;
            }
            if (!invisibles.isEnabled() && entity.isInvisible()) continue;

            double dist = mc.player.distanceTo(entity);
            if (dist > range.getValue()) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private double getAngleTo(LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = Math.abs(MathHelper.wrapDegrees((float) (yaw - mc.player.getYaw())));
        return Math.min(diff, 360 - diff);
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dy = target.getY() + target.getHeight() / 2.0 - mc.player.getY() - getPlayerEyeHeight();

        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        if (smooth.isEnabled()) {
            float speed = speed.getValue().floatValue();
            float vSpeed = verticalSpeed.getValue().floatValue();

            float yawDiff = MathHelper.wrapDegrees((float) yaw - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees((float) pitch - mc.player.getPitch());

            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDiff, -speed, speed));
            mc.player.setPitch(mc.player.getPitch() + MathHelper.clamp(pitchDiff, -vSpeed, vSpeed));
        } else {
            mc.player.setYaw((float) yaw);
            mc.player.setPitch((float) pitch);
        }
    }

    private double getPlayerEyeHeight() {
        try {
            return mc.player.getEyeHeight(mc.player.getPose());
        } catch (Exception e) {
            return mc.player.getEyeHeight();
        }
    }
}