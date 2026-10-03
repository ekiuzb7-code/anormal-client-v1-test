package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Strafe extends Module {
    public final NumberSetting distance = new NumberSetting("Distance", "Strafe orbit distance", 3.3, 0.1, 6.0, 0.1);
    public final NumberSetting speed = new NumberSetting("Speed", "Strafe speed", 0.5, 0.1, 1.0, 0.05);
    public final NumberSetting targetMinAngle = new NumberSetting("Target minimum angle", "Min target angle", 120.0, 1.0, 360.0, 5.0);
    public final NumberSetting selfMinAngle = new NumberSetting("Your minimum angle", "Min self angle", 90.0, 1.0, 360.0, 5.0);

    public Strafe() {
        super("Strafe", "Strafes around targets", Category.UZNY11);
        addSetting(distance); addSetting(speed); addSetting(targetMinAngle); addSetting(selfMinAngle);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (!(mc.targetedEntity instanceof LivingEntity t) || !t.isAlive()) return;
            double d = mc.player.distanceTo(t);
            float yawDiff = Math.abs(MathHelper.wrapDegrees(yawTo(t) - mc.player.getYaw()));
            if (yawDiff < targetMinAngle.getValue() / 4.0) return;
            if (yawDiff < selfMinAngle.getValue() / 4.0) return;
            double want = distance.getValue();
            double ang = Math.toRadians(yawTo(t));
            double tx = t.getX() - Math.sin(ang) * want * -1.0;
            double tz = t.getZ() + Math.cos(ang) * want * -1.0;
            double dx = tx - mc.player.getX();
            double dz = tz - mc.player.getZ();
            double len = Math.hypot(dx, dz);
            if (len < 0.3 || len > want + 4.0) return;
            Vec3d v = mc.player.getVelocity();
            double s = speed.getValue() * 0.35;
            mc.player.setVelocity(v.x + (dx / len) * s, v.y, v.z + (dz / len) * s);
        } catch (Throwable ignored) {}
    }

    private float yawTo(LivingEntity t) {
        return (float) Math.toDegrees(Math.atan2(-(t.getX() - mc.player.getX()), t.getZ() - mc.player.getZ()));
    }

    private int strafeTicks = 0;

    public int getStrafeTicks() {
        return strafeTicks;
    }
}
