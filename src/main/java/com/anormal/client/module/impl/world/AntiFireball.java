package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class AntiFireball extends Module {
    public final NumberSetting angleLimit = new NumberSetting("Angle Limit", "Max degrees from crosshair to deflect", 60.0, 10.0, 180.0, 1.0);
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Aim rotation speed to intercept", 12.0, 1.0, 30.0, 0.5);
    public final BooleanSetting stopMovement = new BooleanSetting("Stop Movement", "Hold movement while deflecting", true);
    public final BooleanSetting moveOnFinish = new BooleanSetting("Move on Finish", "Release movement after deflect", true);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Deflect without moving client camera", false);

    private boolean holding = false;

    public AntiFireball() {
        super("AntiFireball", "Aims and swings at incoming fireballs to deflect them", Category.WORLD);
        addSetting(angleLimit);
        addSetting(aimSpeed);
        addSetting(stopMovement);
        addSetting(silentAim);
        addSetting(moveOnFinish);
    }

    @Override
    public void onDisable() {
        release();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        FireballEntity best = null;
        double bestDist = 5.0;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof FireballEntity fireball) || !fireball.isAlive()) continue;
            double d = mc.player.distanceTo(fireball);
            if (d > bestDist) continue;
            if (crosshairAngle(fireball.getX(), fireball.getY(), fireball.getZ()) > angleLimit.getValue()) continue;
            bestDist = d;
            best = fireball;
        }
        if (best == null) {
            if (holding && moveOnFinish.isEnabled()) release();
            holding = false;
            return;
        }
        holding = true;
        if (stopMovement.isEnabled()) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
        if (!silentAim.isEnabled()) aimAt(best.getX(), best.getY(), best.getZ(), aimSpeed.getValue());
        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void release() {
        holding = false;
    }

    private double crosshairAngle(double x, double y, double z) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d to = new Vec3d(x - eye.x, y - eye.y, z - eye.z).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dotProduct(to)));
        return Math.toDegrees(Math.acos(dot));
    }

    private void aimAt(double x, double y, double z, double speed) {
        Vec3d eye = mc.player.getEyePos();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        float ty = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float tp = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float cur = mc.player.getYaw();
        float d = ty - cur;
        while (d > 180.0f) d -= 360.0f;
        while (d < -180.0f) d += 360.0f;
        mc.player.setYaw(cur + (float) Math.max(-speed, Math.min(speed, d)));
        float p = mc.player.getPitch();
        mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, p + (float) Math.max(-speed, Math.min(speed, tp - p)))));
    }
}
