package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class KillAura extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Attack range", 4.5, 2.0, 6.0, 0.1);
    public final NumberSetting minCPS = new NumberSetting("Min CPS", "Minimum clicks per second", 8, 1, 20, 1);
    public final NumberSetting maxCPS = new NumberSetting("Max CPS", "Maximum clicks per second", 12, 1, 20, 1);
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Target mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Target animals", false);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Target invisible entities", false);
    public final BooleanSetting teams = new BooleanSetting("Teams", "Ignore teammates", true);
    public final BooleanSetting walls = new BooleanSetting("Through Walls", "Attack through walls", false);
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target selection", "Distance", "Distance", "Health", "Angle");
    public final BooleanSetting autoBlock = new BooleanSetting("Auto Block", "Auto block when not attacking", false);
    public final BooleanSetting swordOnly = new BooleanSetting("Sword Only", "Only attack with sword", true);
    public final BooleanSetting click = new BooleanSetting("Click", "Simulate clicks", true);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing arm", true);
    public final NumberSetting fov = new NumberSetting("FOV", "Field of view", 180.0, 30.0, 360.0, 1.0);

    private LivingEntity target;
    private int cpsDelay = 0;
    private boolean wasAttacking = false;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.UZNY11);
        addSetting(range);
        addSetting(minCPS);
        addSetting(maxCPS);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(invisibles);
        addSetting(teams);
        addSetting(walls);
        addSetting(targetMode);
        addSetting(autoBlock);
        addSetting(swordOnly);
        addSetting(click);
        addSetting(swing);
        addSetting(fov);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Find target
        target = findTarget();
        if (target == null) {
            if (wasAttacking && autoBlock.isEnabled()) {
                mc.options.useKey.setPressed(false);
            }
            wasAttacking = false;
            return;
        }

        // Check sword requirement
        if (swordOnly.isEnabled() && !isHoldingSword()) return;

        // Check FOV
        if (getAngleTo(target) > fov.getValue()) return;

        // Attack logic
        if (cpsDelay <= 0) {
            attackTarget();
            double min = Math.min(minCPS.getValue(), maxCPS.getValue());
            double max = Math.max(minCPS.getValue(), maxCPS.getValue());
            double cps = min + Math.random() * (max - min);
            cpsDelay = (int) Math.max(1, Math.round(20.0 / Math.max(0.5, cps)));
            wasAttacking = true;
        } else {
            cpsDelay--;
            if (wasAttacking && autoBlock.isEnabled() && cpsDelay > 5) {
                mc.options.useKey.setPressed(true);
            }
        }
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (living instanceof PlayerEntity p) {
                if (!players.isEnabled()) continue;
                if (teams.isEnabled() && mc.player.isTeammate(p)) continue;
            } else if (living.getType().getCategory() == net.minecraft.entity.EntityType.Category.MOB) {
                if (!mobs.isEnabled()) continue;
            } else if (living.getType().getCategory() == net.minecraft.entity.EntityType.Category.CREATURE) {
                if (!animals.isEnabled()) continue;
            } else continue;

            if (!invisibles.isEnabled() && living.isInvisible()) continue;
            if (!walls.isEnabled() && !mc.player.canSee(living)) continue;

            double dist = mc.player.distanceTo(living);
            if (dist > range.getValue()) continue;

            double score = switch (targetMode.getValue()) {
                case "Health" -> living.getHealth();
                case "Angle" -> getAngleTo(living);
                default -> dist;
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

        // Face target
        faceTarget(target);

        // Attack
        if (click.isEnabled()) {
            mc.interactionManager.attackEntity(mc.player, target);
        }
        if (swing.isEnabled()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        mc.player.setYaw((float) yaw);
        mc.player.setPitch((float) (-Math.toDegrees(Math.atan2(target.getY() - mc.player.getY(), Math.sqrt(dx * dx + dz * dz)))));
    }

    private double getAngleTo(LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double diff = Math.abs(MathHelper.wrapDegrees((float) (yaw - mc.player.getYaw())));
        return Math.min(diff, 360 - diff);
    }

    private boolean isHoldingSword() {
        ItemStack stack = mc.player.getMainHandStack();
        return stack.getItem() instanceof net.minecraft.item.SwordItem ||
               stack.getItem() instanceof net.minecraft.item.AxeItem ||
               stack.getItem() == Items.MACE;
    }
}