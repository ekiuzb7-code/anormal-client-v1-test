package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

public class SilentAura extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Attack range", 4.5, 2.0, 6.0, 0.1);
    public final NumberSetting minAPS = new NumberSetting("Min APS", "Minimum attacks per second", 8, 1, 20, 1);
    public final NumberSetting maxAPS = new NumberSetting("Max APS", "Maximum attacks per second", 12, 1, 20, 1);
    public final NumberSetting fov = new NumberSetting("FOV", "Field of view", 180.0, 30.0, 360.0, 1.0);
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Target mobs", false);
    public final BooleanSetting teams = new BooleanSetting("Teams", "Ignore teammates", true);
    public final BooleanSetting walls = new BooleanSetting("Through Walls", "Attack through walls", false);
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target selection", "Distance", "Distance", "Health", "Angle");
    public final BooleanSetting swordOnly = new BooleanSetting("Sword Only", "Only attack with sword", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing arm client-side", true);
    public final NumberSetting extraSwing = new NumberSetting("Extra Swing", "Extra swing distance", 1.0, 0.0, 3.0, 0.1);

    private LivingEntity target;
    private int apsDelay = 0;

    public SilentAura() {
        super("SilentAura", "Attacks without rotating client view", Category.UZNY11);
        addSetting(range);
        addSetting(minAPS);
        addSetting(maxAPS);
        addSetting(fov);
        addSetting(players);
        addSetting(mobs);
        addSetting(teams);
        addSetting(walls);
        addSetting(targetMode);
        addSetting(swordOnly);
        addSetting(swing);
        addSetting(extraSwing);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        target = findTarget();
        if (target == null) return;

        if (getAngleTo(target) > fov.getValue()) return;
        if (swordOnly.isEnabled() && !isHoldingSword()) return;

        if (apsDelay <= 0) {
            attackTarget();
            double min = Math.min(minAPS.getValue(), maxAPS.getValue());
            double max = Math.max(minAPS.getValue(), maxAPS.getValue());
            double aps = min + Math.random() * (max - min);
            apsDelay = (int) Math.max(1, Math.round(20.0 / Math.max(0.5, aps)));
        } else {
            apsDelay--;
        }
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (living instanceof net.minecraft.entity.player.PlayerEntity p) {
                if (!players.isEnabled()) continue;
                if (teams.isEnabled() && mc.player.isTeammate(p)) continue;
            } else {
                // Only target players for silent aura
                continue;
            }

            double dist = mc.player.distanceTo(living);
            if (dist > range.getValue()) continue;

            double score = switch (targetMode.getValue()) {
                case "Health" -> living.getHealth();
                case "Angle" -> getAngleTo(living);
                default -> mc.player.distanceTo(living);
            };
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private void attackTarget() {
        if (target == null) return;

        // Silent attack - server-side rotation only
        mc.interactionManager.attackEntity(mc.player, target);
        if (swing.isEnabled()) {
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    private double getAngleTo(LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = Math.abs(MathHelper.wrapDegrees((float) (yaw - mc.player.getYaw())));
        return Math.min(diff, 360 - diff);
    }

    private boolean isHoldingSword() {
        Item item = mc.player.getMainHandStack().getItem();
        String path = Registries.ITEM.getId(item).getPath();
        return path.contains("_sword") || path.contains("_axe");
    }
}