package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class AutoPearl extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "On Bind throws once, Aggro chases enemy pearls", "On Bind", "On Bind", "Aggro");
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Aim rotation speed", 10.0, 1.0, 30.0, 0.5);
    public final NumberSetting angleLimit = new NumberSetting("Angle Limit", "Max degrees from crosshair", 30.0, 5.0, 180.0, 1.0);
    public final NumberSetting minHealth = new NumberSetting("Min Health", "Required health to chase", 10.0, 1.0, 36.0, 0.5);
    public final NumberSetting distanceLimit = new NumberSetting("Distance Limit", "Min landing distance to chase", 6.0, 0.0, 40.0, 0.5);
    public final BooleanSetting verticalCheck = new BooleanSetting("Vertical Check", "Skip bad height differences", true);
    public final NumberSetting pearlCooldown = new NumberSetting("Pearl Cooldown", "Seconds between throws", 3.0, 0.5, 20.0, 0.5);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Chase without moving client camera", false);
    public final BooleanSetting limitToItems = new BooleanSetting("Limit to Items", "Only while holding a weapon", false);
    private int cooldownTicks = 0;
    private long lastOwnThrow = 0L;
    private boolean bindThrow = false;
    public AutoPearl() {
        super("AutoPearl", "Throws pearl to chase opponents who pearl away", Category.UZNY11);
        addSetting(mode); addSetting(aimSpeed); addSetting(angleLimit);
        addSetting(minHealth); addSetting(distanceLimit); addSetting(verticalCheck);
        addSetting(pearlCooldown); addSetting(silentAim); addSetting(limitToItems);
    }
    @Override
    public void onEnable() { cooldownTicks = 0; bindThrow = mode.is("On Bind"); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.player.isAlive()) return;
        if (cooldownTicks > 0) cooldownTicks--;
        if (mode.is("On Bind")) {
            if (bindThrow) {
                bindThrow = false;
                if (cooldownTicks <= 0 && passesGates()) throwPearlAtLook();
                else setEnabled(false);
            }
            return;
        }
        if (cooldownTicks > 0 || !passesGates()) return;
        EnderPearlEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl) || !pearl.isAlive()) continue;
            double dist = mc.player.distanceTo(pearl);
            if (dist < 4.0) continue;
            if (System.currentTimeMillis() - lastOwnThrow < 3000 && dist < 6.0) continue;
            if (dist < distanceLimit.getValue()) continue;
            if (verticalCheck.isEnabled() && Math.abs(pearl.getY() - mc.player.getY()) > 12.0) continue;
            double angle = crosshairAngle(pearl.getX(), pearl.getY(), pearl.getZ());
            if (angle > angleLimit.getValue()) continue;
            if (angle < bestAngle) {
                bestAngle = angle;
                best = pearl;
            }
        }
        if (best == null) return;
        int slot = findSlot("ender_pearl");
        if (slot == -1) return;
        int orig = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        if (!silentAim.isEnabled()) {
            Vec3d v = best.getVelocity();
            aimAt(best.getX() + v.x * 3.0, best.getY() + v.y * 3.0, best.getZ() + v.z * 3.0, aimSpeed.getValue());
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(orig);
        lastOwnThrow = System.currentTimeMillis();
        cooldownTicks = (int) (pearlCooldown.getValue() * 20.0);
    }

    private boolean passesGates() {
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() < minHealth.getValue()) return false;
        if (limitToItems.isEnabled() && !isWeaponHeld()) return false;
        return findSlot("ender_pearl") != -1;
    }

    private void throwPearlAtLook() {
        int slot = findSlot("ender_pearl");
        if (slot == -1) {
            setEnabled(false);
            return;
        }
        int orig = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(orig);
        lastOwnThrow = System.currentTimeMillis();
        cooldownTicks = (int) (pearlCooldown.getValue() * 20.0);
        setEnabled(false);
    }

    private boolean isWeaponHeld() {
        try {
            Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
            if (id == null) return false;
            String p = id.getPath();
            return p.endsWith("_sword") || p.endsWith("_axe") || p.equals("mace") || p.equals("trident");
        } catch (Throwable ignored) {
            return false;
        }
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
