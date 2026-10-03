package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class AutoMace extends Module {
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting hostiles = new BooleanSetting("Hostiles", "Target hostile mobs", true);
    public final BooleanSetting aim = new BooleanSetting("Aim", "Aim at smash target while falling", true);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Aim without moving client camera", false);
    public final NumberSetting aimRange = new NumberSetting("Aim Range", "Max target distance", 4.5, 2.0, 8.0, 0.1);
    public final BooleanSetting attack = new BooleanSetting("Attack", "Auto attack smash target", true);
    public final NumberSetting extraDelay = new NumberSetting("Extra Delay", "Ticks after cooldown, negative is early", 0.0, -10.0, 20.0, 1.0);
    public final BooleanSetting autoUnequipElytra = new BooleanSetting("Auto Unequip Elytra", "Hold chestplate for smash fall", true);
    public final BooleanSetting reequipElytra = new BooleanSetting("Re-equip Elytra", "Hold elytra again after bounce", true);
    public final BooleanSetting smashOnly = new BooleanSetting("Smash Only", "Only swap while falling smash", true);
    public final ModeSetting maceSelection = new ModeSetting("Mace Selection", "Manual uses Mace Type, Auto picks best", "Manual", "Manual", "Auto");
    public final ModeSetting maceType = new ModeSetting("Mace Type", "Preferred mace for Manual selection", "Density", "Density", "Breach");
    public final BooleanSetting stunSlam = new BooleanSetting("Stun Slam", "Axe hit then mace vs raised shield", true);
    public final NumberSetting stunChance = new NumberSetting("Stun Chance", "Stun Slam activation chance", 50.0, 0.0, 100.0, 1.0);
    public final BooleanSetting limitToItems = new BooleanSetting("Limit to Items", "Only while holding a weapon", false);

    private int originalSlot = -1, noTargetTicks = 0;
    private boolean swapped = false;
    public AutoMace() {        super("AutoMace", "Swaps to mace and times smash attacks while falling", Category.UZNY11);
        addSetting(players); addSetting(hostiles); addSetting(aim); addSetting(silentAim);
        addSetting(aimRange); addSetting(attack); addSetting(extraDelay); addSetting(autoUnequipElytra);
        addSetting(reequipElytra); addSetting(smashOnly); addSetting(maceSelection); addSetting(maceType);
        addSetting(stunSlam); addSetting(stunChance); addSetting(limitToItems);
    }
    @Override
    public void onEnable() { originalSlot = -1; swapped = false; noTargetTicks = 0; }
    @Override
    public void onDisable() { restore(); }
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || !mc.player.isAlive()) {
            restore();
            return;
        }
        String held = heldPath();
        if (limitToItems.isEnabled() && !(held.endsWith("_sword") || held.endsWith("_axe") || held.equals("mace") || held.equals("trident"))) {
            restore();
            return;
        }
        if (mc.player.isGliding()) {
            if (!autoUnequipElytra.isEnabled()) { restore(); return; }
            int chest = findSlot("_chestplate", false);
            if (chest != -1 && !heldPath().equals("mace")) swapTo(chest);
            return;
        }
        boolean falling = !mc.player.isOnGround() && mc.player.getVelocity().y < -0.25;
        LivingEntity target = selectTarget();
        if (target == null) { if (++noTargetTicks > 20) restore(); return; }
        noTargetTicks = 0;
        if (smashOnly.isEnabled() && !falling) { restore(); return; }
        int mace = findSlot("mace", true);
        if (maceSelection.is("Auto") || maceSelection.is("Manual") && maceType.is("Breach")) {
            for (int i = 8; i >= 0; i--) { try { Identifier mid = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem()); if (mid != null && mid.getPath().equals("mace")) { mace = i; break; } } catch (Throwable ignored) {} }
        }
        if (mace == -1) { restore(); return; }
        if (stunSlam.isEnabled() && target.isBlocking() && Math.random() * 100.0 < stunChance.getValue()) {
            int axe = findSlot("_axe", false);
            if (axe != -1) {
                swapTo(axe);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.getInventory().setSelectedSlot(mace);
                swapped = true;
                return;
            }
        }
        if (!heldPath().equals("mace")) swapTo(mace);
        if (aim.isEnabled() && !silentAim.isEnabled()) aimAt(target.getX(), target.getY() + target.getHeight() / 2.0, target.getZ(), 12.0);
        if (attack.isEnabled()) {
            float progress = mc.player.getAttackCooldownProgress(0.5f);
            int extra = extraDelay.getValue().intValue();
            if (extra >= 0 ? progress >= 1.0f : progress >= Math.max(0.0f, 1.0f + extra / 20.0f)) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
        if (reequipElytra.isEnabled() && mc.player.getVelocity().y > 0.5 && swapped) {
            int elytra = findSlot("elytra", true);
            if (elytra != -1) mc.player.getInventory().setSelectedSlot(elytra);
        }
    }

    private LivingEntity selectTarget() {
        LivingEntity best = null;
        double bestDist = aimRange.getValue();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            if (living instanceof PlayerEntity p) {
                if (!players.isEnabled()) continue;
                try { if (mc.player.isTeammate(p)) continue; } catch (Throwable ignored) {}
            } else if (!(e instanceof HostileEntity) || !hostiles.isEnabled()) continue;
            double d = mc.player.distanceTo(living);
            if (d > bestDist) continue;
            bestDist = d;
            best = living;
        }
        return best;
    }

    private void aimAt(double x, double y, double z, double speed) {
        Vec3d eye = mc.player.getEyePos();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        float ty = (float) Math.toDegrees(Math.atan2(-dx, dz)), tp = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float cur = mc.player.getYaw(), d = ty - cur;
        while (d > 180.0f) d -= 360.0f; while (d < -180.0f) d += 360.0f;
        mc.player.setYaw(cur + (float) Math.max(-speed, Math.min(speed, d)));
        mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, mc.player.getPitch() + (float) Math.max(-speed, Math.min(speed, tp - mc.player.getPitch())))));
    }
    private String heldPath() {
        try { Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()); return id == null ? "" : id.getPath(); }
        catch (Throwable ignored) { return ""; }
    }
    private int findSlot(String path, boolean exact) {
        for (int i = 0; i < 9; i++) {
            try { Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem()); if (id != null && (exact ? id.getPath().equals(path) : id.getPath().endsWith(path))) return i; }
            catch (Throwable ignored) {}
        }
        return -1;
    }
    private void swapTo(int slot) { if (!swapped) originalSlot = mc.player.getInventory().getSelectedSlot(); mc.player.getInventory().setSelectedSlot(slot); swapped = true; }

    private void restore() {
        if (swapped && mc.player != null && originalSlot >= 0 && originalSlot < 9) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {}
        }
        swapped = false;
        originalSlot = -1;
        noTargetTicks = 0;
    }
}
