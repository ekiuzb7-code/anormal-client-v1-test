package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.MathHelper;

public class BowAimbot extends Module {
    public final NumberSetting angleLimit = new NumberSetting("Angle limit", "Max angle to target", 45.0, 1.0, 180.0, 5.0);
    public final NumberSetting aimSpeed = new NumberSetting("Aim speed", "Aim adjust speed", 9.0, 1.0, 10.0, 0.1);
    public final BooleanSetting stopMovement = new BooleanSetting("Stop movement", "Stand still while aiming", false);
    public final BooleanSetting moveOnFinish = new BooleanSetting("Move on finish", "Resume movement after", false);
    public final BooleanSetting silentAim = new BooleanSetting("Silent aim", "Aim without moving view", false);

    public BowAimbot() {
        super("BowAimbot", "Aims bow at nearest player", Category.UZNY11);
        addSetting(angleLimit); addSetting(aimSpeed); addSetting(stopMovement);
        addSetting(moveOnFinish); addSetting(silentAim);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.options == null) return;
        try {
            String held = "";
            try { held = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath(); } catch (Throwable t) {}
            if (!held.contains("bow")) return;
            if (!mc.options.useKey.isPressed()) { if (moveOnFinish.getValue()) mc.options.forwardKey.setPressed(true); return; }
            PlayerEntity best = null;
            double bestDist = 60.0;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof PlayerEntity p) || e == mc.player || !p.isAlive()) continue;
                double d = mc.player.distanceTo(p);
                if (d > bestDist) continue;
                float yawDiff = Math.abs(MathHelper.wrapDegrees(yawTo(p) - mc.player.getYaw()));
                if (yawDiff > angleLimit.getValue()) continue;
                bestDist = d; best = p;
            }
            if (best == null) return;
            if (stopMovement.getValue()) mc.options.forwardKey.setPressed(false);
            float step = aimSpeed.getValue().floatValue();
            if (!silentAim.getValue()) {
                mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yawTo(best) - mc.player.getYaw()), -step, step));
                mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(MathHelper.wrapDegrees(pitchTo(best) - mc.player.getPitch()), -step, step), -90, 90));
            } else {
                mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yawTo(best) - mc.player.getYaw()), -step * 0.5f, step * 0.5f));
            }
        } catch (Throwable ignored) {}
    }

    private float yawTo(PlayerEntity t) {
        return (float) Math.toDegrees(Math.atan2(-(t.getX() - mc.player.getX()), t.getZ() - mc.player.getZ()));
    }

    private float pitchTo(PlayerEntity t) {
        double dx = t.getX() - mc.player.getX();
        double dz = t.getZ() - mc.player.getZ();
        double dy = (t.getY() + 1.2) - (mc.player.getY() + mc.player.getStandingEyeHeight());
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }
}
