package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class Triggerbot extends Module {
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting hostiles = new BooleanSetting("Hostiles", "Target hostile mobs", true);
    public final BooleanSetting passives = new BooleanSetting("Passives", "Target passive mobs", false);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Target invisible entities", false);
    public final NumberSetting extraDelay = new NumberSetting("Extra Delay", "Ticks after cooldown negative attacks early", 1.0, -10.0, 20.0, 1.0);
    public final NumberSetting mouseOverDelay = new NumberSetting("Mouse Over Delay", "Ticks hovering before attacking", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting requireMouseDown = new BooleanSetting("Require Mouse Down", "Only attack while holding attack", false);
    public final BooleanSetting ignoreActivationClick = new BooleanSetting("Ignore Activation Click", "Ignore first click unless hovering ready target", false);
    public final BooleanSetting airCrits = new BooleanSetting("Air Crits", "Only attack airborne on falling crit", false);
    public final BooleanSetting shieldCheck = new BooleanSetting("Shield Check", "Skip actively shielding players", true);
    public final BooleanSetting selectFirstHit = new BooleanSetting("Select First Hit", "Wait for opponent to hit you first", false);
    public final NumberSetting targetMissChance = new NumberSetting("Target Miss %", "Chance to swing with no target", 0.0, 0.0, 100.0, 1.0);
    public final NumberSetting earlyHitChance = new NumberSetting("Early Hit %", "Chance to attack before cooldown ready", 0.0, 0.0, 100.0, 1.0);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", "Only trigger while holding a weapon", false);
    private int hoverTicks;
    private boolean firstHitTaken;
    private boolean activationIgnored;

    public Triggerbot() {
        super("Triggerbot", "Automatically attacks when hovering a valid target", Category.COMBAT);
        addSetting(players);
        addSetting(hostiles);
        addSetting(passives);
        addSetting(invisibles);
        addSetting(extraDelay);
        addSetting(mouseOverDelay);
        addSetting(requireMouseDown);
        addSetting(ignoreActivationClick);
        addSetting(airCrits);
        addSetting(shieldCheck);
        addSetting(selectFirstHit);
        addSetting(targetMissChance);
        addSetting(earlyHitChance);
        addSetting(weaponOnly);
    }

    @Override
    public void onEnable() {
        hoverTicks = 0;
        firstHitTaken = false;
        activationIgnored = !ignoreActivationClick.isEnabled();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!mc.player.isAlive()) return;
        if (mc.player.hurtTime > 0) firstHitTaken = true;
        if (weaponOnly.isEnabled() && !isWeapon(mc.player.getMainHandStack().getItem())) {
            hoverTicks = 0;
            return;
        }
        if (requireMouseDown.isEnabled() && !mc.options.attackKey.isPressed()) {
            hoverTicks = 0;
            return;
        }
        LivingEntity target = validTarget();
        if (target == null) {
            hoverTicks = 0;
            if (roll(targetMissChance.getValue()) && cooldownReady()) mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }
        hoverTicks++;
        if (!activationIgnored) {
            activationIgnored = true;
            return;
        }
        if (hoverTicks < mouseOverDelay.getValue().intValue()) return;
        if (selectFirstHit.isEnabled() && !firstHitTaken) return;
        if (airCrits.isEnabled() && !mc.player.isOnGround() && !mc.player.isGliding() && mc.player.getVelocity().y >= -0.2) return;
        if (shieldCheck.isEnabled() && target instanceof PlayerEntity p && p.isBlocking() && !isAxe(mc.player.getMainHandStack().getItem())) return;
        if (!cooldownReady()) return;
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        hoverTicks = 0;
    }

    private boolean cooldownReady() {
        float progress = mc.player.getAttackCooldownProgress(0.5f);
        int extra = extraDelay.getValue().intValue();
        boolean ready = extra >= 0 ? progress >= 1.0f : progress >= Math.max(0.0f, 1.0f + extra / 20.0f);
        if (ready) return true;
        return roll(earlyHitChance.getValue());
    }

    private LivingEntity validTarget() {
        if (!(mc.targetedEntity instanceof LivingEntity living) || !living.isAlive() || living == mc.player) return null;
        if (!invisibles.isEnabled() && living.isInvisible()) return null;
        if (living instanceof PlayerEntity p) {
            if (!players.isEnabled()) return null;
            if (mc.player.isTeammate(p)) return null;
        } else if (living instanceof HostileEntity) {
            if (!hostiles.isEnabled()) return null;
        } else if (living instanceof PassiveEntity) {
            if (!passives.isEnabled()) return null;
        } else return null;
        return living;
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

    private static boolean isAxe(net.minecraft.item.Item item) {
        try {
            Identifier id = Registries.ITEM.getId(item);
            return id != null && id.getPath().endsWith("_axe");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
