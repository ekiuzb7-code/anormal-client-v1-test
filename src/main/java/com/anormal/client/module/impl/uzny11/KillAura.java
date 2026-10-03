package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class KillAura extends Module {
    public final NumberSetting minAps = new NumberSetting("Min APS", "Minimum attacks per second", 6.0, 1.0, 20.0, 0.5);
    public final NumberSetting maxAps = new NumberSetting("Max APS", "Maximum attacks per second", 13.0, 1.0, 20.0, 0.5);
    public final NumberSetting swingRange = new NumberSetting("Swing range", "Swing initiation range", 4.0, 0.0, 6.0, 0.1);
    public final NumberSetting attackRange = new NumberSetting("Attack range", "Attack range", 3.5, 0.0, 6.0, 0.1);
    public final BooleanSetting requireMouseDown = new BooleanSetting("Require mouse down", "Only while attack held", false);
    public final BooleanSetting disableOnDeath = new BooleanSetting("Disable on death", "Disable when you die", false);
    public final BooleanSetting showTarget = new BooleanSetting("Show target", "Highlight targets", false);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Target highlight", 0x32FFC870);
    public final ColorSetting attackColor = new ColorSetting("Attack Color", "Attack highlight", 0x64FF0000);
    public final BooleanSetting limitToItems = new BooleanSetting("Limit to items", "Only with swords held", false);
    public final BooleanSetting guiCheck = new BooleanSetting("GUI check", "No attack in GUIs", true);
    public final BooleanSetting perfectSwing = new BooleanSetting("Perfect swing", "Only on full cooldown", false);
    public final NumberSetting maxAngle = new NumberSetting("Max angle", "Max target angle", 90.0, 1.0, 360.0, 5.0);
    public final NumberSetting maxTargets = new NumberSetting("Max targets", "Max targets per swing", 1.0, 1.0, 6.0, 1.0);
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target priority", "Distance", "Distance", "Yaw", "Armor", "Threat", "Health");
    private final Random random = new Random();
    private long lastAttack = 0;

    public KillAura() {
        super("KillAura", "Attacks players around you", Category.UZNY11);
        addSetting(minAps); addSetting(maxAps); addSetting(swingRange); addSetting(attackRange);
        addSetting(requireMouseDown); addSetting(disableOnDeath); addSetting(showTarget);
        addSetting(targetColor); addSetting(attackColor); addSetting(limitToItems);
        addSetting(guiCheck); addSetting(perfectSwing); addSetting(maxAngle);
        addSetting(maxTargets); addSetting(targetMode);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        try {
            if (disableOnDeath.getValue() && mc.player.getHealth() <= 0.0f) return;
            if (requireMouseDown.getValue() && !mc.options.attackKey.isPressed()) return;
            if (guiCheck.getValue() && mc.currentScreen != null) return;
            if (limitToItems.getValue()) {
                try { if (!Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath().endsWith("sword")) return; }
                catch (Throwable t) { return; }
            }
            List<LivingEntity> targets = new ArrayList<>();
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) continue;
                double d = mc.player.distanceTo(le);
                if (d > Math.max(swingRange.getValue(), attackRange.getValue())) continue;
                float yawDiff = Math.abs(MathHelper.wrapDegrees(yawTo(le) - mc.player.getYaw()));
                if (yawDiff > maxAngle.getValue() / 2.0) continue;
                targets.add(le);
            }
            targets.sort((a, b) -> {
                if (targetMode.is("Health")) return Float.compare(a.getHealth(), b.getHealth());
                if (targetMode.is("Yaw")) return Float.compare(Math.abs(MathHelper.wrapDegrees(yawTo(a) - mc.player.getYaw())), Math.abs(MathHelper.wrapDegrees(yawTo(b) - mc.player.getYaw())));
                return Double.compare(mc.player.distanceTo(a), mc.player.distanceTo(b));
            });
            if (targets.isEmpty()) return;
            if (perfectSwing.getValue() && mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
            double cps = Math.min(minAps.getValue(), maxAps.getValue()) + random.nextDouble() * Math.abs(maxAps.getValue() - minAps.getValue());
            if (System.currentTimeMillis() - lastAttack < 1000.0 / Math.max(1.0, cps)) return;
            int n = Math.min(maxTargets.getValue().intValue(), targets.size());
            for (int i = 0; i < n; i++) {
                LivingEntity t = targets.get(i);
                if (mc.player.distanceTo(t) <= attackRange.getValue()) {
                    mc.interactionManager.attackEntity(mc.player, t);
                }
                if (t instanceof PlayerEntity && showTarget.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
            }
            mc.player.swingHand(Hand.MAIN_HAND);
            lastAttack = System.currentTimeMillis();
            if (showTarget.getValue()) { int c = targetColor.getValue(); int a = attackColor.getValue(); if (c == 0 && a == 0) return; }
        } catch (Throwable ignored) {}
    }

    private float yawTo(LivingEntity t) {
        return (float) Math.toDegrees(Math.atan2(-(t.getX() - mc.player.getX()), t.getZ() - mc.player.getZ()));
    }
}
