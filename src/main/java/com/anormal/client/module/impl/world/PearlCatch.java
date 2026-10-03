package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class PearlCatch extends Module {
    public final ModeSetting aimMode = new ModeSetting("Aim Mode", "Upward throws pearl up, Current Aim uses look dir", "Upward", "Upward", "Current Aim");
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Aim rotation speed per throw", 10.0, 1.0, 30.0, 0.5);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Sequence without moving client camera", false);
    public final NumberSetting chargeDelay = new NumberSetting("Charge Delay", "Ticks between pearl and wind charge", 6.0, 0.0, 40.0, 1.0);

    private int stage = 0;
    private int timer = 0;
    private int originalSlot = -1;

    public PearlCatch() {
        super("PearlCatch", "Throws pearl then wind charge to redirect its flight", Category.WORLD);
        addSetting(aimMode);
        addSetting(aimSpeed);
        addSetting(silentAim);
        addSetting(chargeDelay);
    }

    @Override
    public void onEnable() {
        stage = 0;
        timer = 0;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && originalSlot >= 0 && originalSlot < 9) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {}
        }
        originalSlot = -1;
        stage = 0;
        timer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (stage == 0) {
            int pearl = findSlot("ender_pearl");
            int charge = findSlot("wind_charge");
            if (pearl == -1 || charge == -1) {
                setEnabled(false);
                return;
            }
            originalSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(pearl);
            if (!silentAim.isEnabled() && aimMode.is("Upward")) {
                float cur = mc.player.getPitch();
                float step = (float) Math.max(-aimSpeed.getValue(), Math.min(aimSpeed.getValue(), -90.0f - cur));
                mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, cur + step)));
            }
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.getInventory().setSelectedSlot(originalSlot);
            timer = Math.max(0, chargeDelay.getValue().intValue());
            stage = 1;
        } else {
            double ix = mc.player.getX();
            double iy = mc.player.getY() + 3.0;
            double iz = mc.player.getZ();
            boolean linedUp = true;
            if (!silentAim.isEnabled()) {
                linedUp = aimAt(ix, iy, iz, aimSpeed.getValue());
            }
            if (timer > 0) {
                timer--;
                return;
            }
            if (!linedUp && timer > -20) {
                timer--;
                return;
            }
            int charge = findSlot("wind_charge");
            if (charge == -1) {
                setEnabled(false);
                return;
            }
            mc.player.getInventory().setSelectedSlot(charge);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (originalSlot >= 0 && originalSlot < 9) mc.player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
            stage = 0;
            setEnabled(false);
        }
    }

    private boolean aimAt(double x, double y, double z, double speed) {
        Vec3d eye = mc.player.getEyePos();
        double dx = x - eye.x;
        double dy = y - eye.y;
        double dz = z - eye.z;
        float ty = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float tp = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        stepYaw(ty, speed);
        float cur = mc.player.getPitch();
        float s = (float) Math.max(-speed, Math.min(speed, tp - cur));
        mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, cur + s)));
        return Math.abs(ty - mc.player.getYaw()) < 15.0f && Math.abs(tp - mc.player.getPitch()) < 15.0f;
    }

    private void stepYaw(float target, double maxStep) {
        float cur = mc.player.getYaw();
        float d = target - cur;
        while (d > 180.0f) d -= 360.0f;
        while (d < -180.0f) d += 360.0f;
        mc.player.setYaw(cur + (float) Math.max(-maxStep, Math.min(maxStep, d)));
    }

    private int findSlot(String path) {
        for (int i = 0; i < 9; i++) {
            try {
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id != null && id.getPath().equals(path)) return i;
            } catch (Throwable ignored) {}
        }
        return -1;
    }
}
