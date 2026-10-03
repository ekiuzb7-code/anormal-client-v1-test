package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class HitFlick extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Target acquisition range", 4.2, 2.0, 6.0, 0.1);
    public final NumberSetting maxAngle = new NumberSetting("Max Angle", "Max angle from crosshair for valid target", 120.0, 10.0, 360.0, 5.0);
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting hostiles = new BooleanSetting("Hostiles", "Target hostile mobs", true);
    public final BooleanSetting passives = new BooleanSetting("Passives", "Target passive mobs", false);
    public final NumberSetting angle = new NumberSetting("Angle", "Flick direction in degrees", 90.0, 0.0, 360.0, 5.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to start a flick on valid attack", 60.0, 0.0, 100.0, 1.0);
    public final NumberSetting flickDelay = new NumberSetting("Flick Delay", "Minimum ticks between flick attempts", 6.0, 0.0, 40.0, 1.0);
    public final BooleanSetting randomizeOffset = new BooleanSetting("Randomize Offset", "Randomize the flick angle per attempt", false);
    public final NumberSetting randomizeRange = new NumberSetting("Randomize Range", "Max angle spread around Angle", 10.0, 0.0, 90.0, 1.0);
    public final BooleanSetting strafeInvert = new BooleanSetting("Strafe Invert", "Flip flick side when strafing with push", true);
    public final BooleanSetting selectHits = new BooleanSetting("Select Hits", "Only flick when target is vulnerable", true);
    public final BooleanSetting blink = new BooleanSetting("Blink", "Flick and restore within one tick", false);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", "Only flick holding sword axe mace or trident", false);
    private int tickCount;
    private int lastFlickTick = -1000;
    private int flickTicks;
    private float savedYaw;

    public HitFlick() {
        super("HitFlick", "Flicks off and back onto target during attacks to alter knockback angle", Category.COMBAT);
        addSetting(range);
        addSetting(maxAngle);
        addSetting(players);
        addSetting(hostiles);
        addSetting(passives);
        addSetting(angle);
        addSetting(chance);
        addSetting(flickDelay);
        addSetting(randomizeOffset);
        addSetting(randomizeRange);
        addSetting(strafeInvert);
        addSetting(selectHits);
        addSetting(blink);
        addSetting(weaponOnly);
    }

    @Override
    public void onEnable() {
        tickCount = 0;
        lastFlickTick = -1000;
        flickTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        tickCount++;
        if (flickTicks > 0) {
            flickTicks--;
            if (flickTicks == 0) mc.player.setYaw(savedYaw);
            return;
        }
        if (!(mc.targetedEntity instanceof LivingEntity target) || !target.isAlive()) return;
        if (!validType(target)) return;
        if (mc.player.distanceTo(target) > range.getValue()) return;
        if (angleTo(target) > maxAngle.getValue()) return;
        if (selectHits.isEnabled() && target.hurtTime <= 0) return;
        if (weaponOnly.isEnabled() && !isWeapon(mc.player.getMainHandStack().getItem())) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < 0.9f) return;
        if (tickCount - lastFlickTick < flickDelay.getValue().intValue()) return;
        if (!roll(chance.getValue())) return;
        double offset = angle.getValue();
        if (randomizeOffset.isEnabled()) offset += (Math.random() - 0.5) * randomizeRange.getValue();
        if (strafeInvert.isEnabled() && mc.player.sidewaysSpeed != 0 && Math.signum(mc.player.sidewaysSpeed) == Math.signum(offset)) offset = -offset;
        savedYaw = mc.player.getYaw();
        mc.player.setYaw(savedYaw + (float) offset);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.interactionManager.attackEntity(mc.player, target);
        lastFlickTick = tickCount;
        flickTicks = blink.isEnabled() ? 1 : 2;
    }

    private boolean validType(LivingEntity living) {
        if (living instanceof PlayerEntity p) {
            if (!players.isEnabled()) return false;
            return !mc.player.isTeammate(p);
        }
        if (living instanceof HostileEntity) return hostiles.isEnabled();
        if (living instanceof PassiveEntity) return passives.isEnabled();
        return false;
    }

    private double angleTo(LivingEntity living) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d point = new Vec3d(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
        Vec3d dir = point.subtract(eye).normalize();
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dotProduct(dir)))));
    }

    private boolean roll(double chance) {
        if (chance <= 0.0) return false;
        if (chance >= 100.0) return true;
        return Math.random() * 100.0 < chance;
    }

    private static boolean isWeapon(net.minecraft.item.Item item) {
        try {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) return false;
            String path = id.getPath();
            return path.endsWith("_sword") || path.endsWith("_axe") || path.equals("mace") || path.equals("trident");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
