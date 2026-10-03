package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class CrystalAura extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Auto breaks placed crystals Manual assists held crystal", "Auto", "Auto", "Manual");
    public final ModeSetting targets = new ModeSetting("Targets", "Which entities count as targets", "Both", "Players", "Hostiles", "Both");
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target priority", "Distance", "Distance", "Yaw", "Health");
    public final NumberSetting range = new NumberSetting("Range", "Max distance for targets and crystals", 4.5, 2.0, 6.0, 0.5);
    public final NumberSetting maxAngle = new NumberSetting("Max Angle", "Max angle for target acquisition", 90.0, 10.0, 360.0, 5.0);
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Aim rotation speed placing and detonating", 8.0, 1.0, 20.0, 0.5);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks before activating placed crystals", 4.0, 0.0, 20.0, 1.0);
    public final BooleanSetting antiSuicide = new BooleanSetting("Anti-Suicide", "Skip detonations with fatal self damage", true);
    public final NumberSetting maxSelfDamage = new NumberSetting("Max Self Damage", "Max self damage allowed per detonation", 6.0, 0.0, 20.0, 0.5);
    public final ModeSetting optimization = new ModeSetting("Optimization", "Placement efficiency handling", "None", "None", "Rapid fire", "Predict");
    public final NumberSetting rapidMinEfficiency = new NumberSetting("Rapid Min Efficiency", "Min efficiency for rapid fire", 0.6, 0.0, 1.0, 0.05);
    public final BooleanSetting predictAttackVelocity = new BooleanSetting("Predict Attack Velocity", "Lead target movement in damage checks", false);
    public final NumberSetting minEfficiency = new NumberSetting("Min Efficiency", "Skip obstructed low damage crystals", 0.2, 0.0, 1.0, 0.05);
    public final BooleanSetting autoObsidian = new BooleanSetting("Auto Obsidian", "Detonate without holding crystals", true);
    public final BooleanSetting centerScreen = new BooleanSetting("Center Screen", "Render crystal count near screen center", true);
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Track targeted and attacked entities", true);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Color for targeted entities", ColorUtils.rgba(0, 230, 255, 255));
    public final ColorSetting attackColor = new ColorSetting("Attack Color", "Color for attacked entities", ColorUtils.rgba(255, 60, 60, 255));
    public final NumberSetting manualAimSpeed = new NumberSetting("Manual Aim Speed", "Aim speed in Manual mode", 8.0, 1.0, 20.0, 0.5);
    public final BooleanSetting manualAntiSuicide = new BooleanSetting("Manual Anti-Suicide", "Skip fatal breaks in Manual mode", true);
    public final NumberSetting manualMaxSelf = new NumberSetting("Manual Max Self", "Max self damage in Manual mode", 6.0, 0.0, 20.0, 0.5);
    public final NumberSetting manualDelay = new NumberSetting("Manual Delay", "Ticks between Manual break place cycles", 4.0, 0.0, 20.0, 1.0);
    public final ModeSetting manualOptimization = new ModeSetting("Manual Optimization", "Manual crystal replacement handling", "None", "None", "Rapid fire", "Predict");
    public final BooleanSetting placeObsidian = new BooleanSetting("Place Obsidian", "Manual works while hovering surfaces", true);
    public final BooleanSetting showTargetBlock = new BooleanSetting("Show Target Block", "Manual requires aimed placement surface", false);
    private int cooldown;
    private LivingEntity lastTarget;
    private EndCrystalEntity lastCrystal;
    private int crystalCount;

    public CrystalAura() {
        super("CrystalAura", "Places and detonates end crystals around targets", Category.UZNY11);
        addSetting(mode); addSetting(targets); addSetting(targetMode); addSetting(range); addSetting(maxAngle);
        addSetting(aimSpeed); addSetting(delay); addSetting(antiSuicide); addSetting(maxSelfDamage); addSetting(optimization);
        addSetting(rapidMinEfficiency); addSetting(predictAttackVelocity); addSetting(minEfficiency); addSetting(autoObsidian); addSetting(centerScreen);
        addSetting(showTarget); addSetting(targetColor); addSetting(attackColor); addSetting(manualAimSpeed); addSetting(manualAntiSuicide);
        addSetting(manualMaxSelf); addSetting(manualDelay); addSetting(manualOptimization); addSetting(placeObsidian); addSetting(showTargetBlock);
    }

    @Override
    public void onEnable() {
        cooldown = 0; lastTarget = null; lastCrystal = null; crystalCount = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        boolean manual = mode.is("Manual");
        if (manual && (mc.options == null || !mc.options.useKey.isPressed())) { lastTarget = null; return; }
        if (manual && showTargetBlock.isEnabled() && !(mc.crosshairTarget instanceof BlockHitResult)) return;
        if (cooldown > 0) cooldown--;
        LivingEntity target = selectTarget();
        if (target == null) { lastTarget = null; return; }
        if (showTarget.isEnabled()) lastTarget = target;
        // Auto place: no crystal in range to break -> put one on obsidian near target
        if (!manual && cooldown <= 0) {
            if (tryPlace(target, waitForPlace())) return;
        }
        EndCrystalEntity best = null;
        double bestEff = -1.0;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity c) || !c.isAlive()) continue;
            if (mc.player.distanceTo(c) > range.getValue() + (optimization.is("Predict") ? 1.0 : 0.0)) continue;
            double eff = efficiency(c, target);
            if (eff < minEfficiency.getValue() || eff <= bestEff) continue;
            best = c;
            bestEff = eff;
        }
        if (best == null) return;
        double self = Math.max(0.0, 4.0 - mc.player.distanceTo(best)) * 2.5;
        double cap = manual ? manualMaxSelf.getValue() : maxSelfDamage.getValue();
        boolean guard = manual ? manualAntiSuicide.isEnabled() : antiSuicide.isEnabled();
        if (guard && (self > cap || mc.player.getHealth() - self <= 0.0)) return;
        if (manual ? (!placeObsidian.isEnabled() && !heldCrystal()) : (!autoObsidian.isEnabled() && !heldCrystal())) return;
        String opt = manual ? manualOptimization.getValue() : optimization.getValue();
        int wait = manual ? manualDelay.getValue().intValue() : delay.getValue().intValue();
        boolean rapid = opt.equalsIgnoreCase("Rapid fire") && bestEff >= rapidMinEfficiency.getValue();
        if (!rapid && cooldown > 0) return;
        rotateTo(best, manual ? manualAimSpeed.getValue() : aimSpeed.getValue());
        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);
        crystalCount++;
        if (showTarget.isEnabled()) lastCrystal = best;
        cooldown = rapid ? 0 : (opt.equalsIgnoreCase("Predict") ? Math.max(0, wait - 2) : wait);
    }

    private int waitForPlace() {
        return delay.getValue().intValue();
    }

    // Places an end crystal on obsidian/bedrock near the target, returns true if placed
    private boolean tryPlace(LivingEntity target, int wait) {
        try {
            if (!autoObsidian.isEnabled() && !heldCrystal()) return false;
            int crystalSlot = -1;
            for (int i = 0; i < 9; i++) {
                try {
                    Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                    if (id != null && id.getPath().equals("end_crystal")) {
                        crystalSlot = i;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (crystalSlot == -1) return false;

            net.minecraft.util.math.BlockPos bestBase = null;
            double bestDist = Double.MAX_VALUE;
            net.minecraft.util.math.BlockPos origin = target.getBlockPos();
            for (int x = -3; x <= 3; x++)
                for (int y = -2; y <= 2; y++)
                    for (int z = -3; z <= 3; z++) {
                        net.minecraft.util.math.BlockPos base = origin.add(x, y, z);
                        net.minecraft.util.math.BlockPos above = base.up();
                        net.minecraft.util.math.BlockPos above2 = base.up(2);
                        try {
                            if (!mc.world.isChunkLoaded(base)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(base).getBlock()).getPath();
                            if (!path.equals("obsidian") && !path.equals("bedrock")) continue;
                            if (!mc.world.isAir(above) || !mc.world.isAir(above2)) continue;
                            double ddx = (base.getX() + 0.5) - mc.player.getX();
                            double ddy = (base.getY() + 0.5) - mc.player.getY();
                            double ddz = (base.getZ() + 0.5) - mc.player.getZ();
                            double d = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                            if (d <= range.getValue() + 1.0 && d < bestDist) {
                                bestDist = d;
                                bestBase = base;
                            }
                        } catch (Throwable ignored) {}
                    }
            if (bestBase == null) return false;

            // Anti-suicide on the predicted placement
            double sdx = (bestBase.getX() + 0.5) - mc.player.getX();
            double sdy = (bestBase.getY() + 0.5) - mc.player.getY();
            double sdz = (bestBase.getZ() + 0.5) - mc.player.getZ();
            double selfDist = Math.sqrt(sdx * sdx + sdy * sdy + sdz * sdz);
            double self = Math.max(0.0, 4.0 - selfDist) * 2.5;
            if (antiSuicide.isEnabled() && (self > maxSelfDamage.getValue() || mc.player.getHealth() - self <= 0.0)) return false;

            int prev = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(crystalSlot);
            try {
                BlockHitResult bhr = new BlockHitResult(
                        new Vec3d(bestBase.getX() + 0.5, bestBase.getY() + 1.0, bestBase.getZ() + 0.5),
                        net.minecraft.util.math.Direction.UP, bestBase, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
            } finally {
                try {
                    mc.player.getInventory().setSelectedSlot(prev);
                } catch (Throwable ignored) {}
            }
            cooldown = Math.max(1, wait);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private LivingEntity selectTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            if (living instanceof PlayerEntity p) {
                if (!targets.is("Both") && !targets.is("Players")) continue;
                if (mc.player.isTeammate(p)) continue;
            } else if (living instanceof HostileEntity) {
                if (!targets.is("Both") && !targets.is("Hostiles")) continue;
            } else continue;
            double dist = mc.player.distanceTo(living);
            if (dist > range.getValue()) continue;
            Vec3d aim = new Vec3d(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dotProduct(aim.subtract(eye).normalize())))));
            if (angle > maxAngle.getValue()) continue;
            double score = targetMode.is("Health") ? living.getHealth() : (targetMode.is("Yaw") ? angle : dist);
            if (score < bestScore) { bestScore = score; best = living; }
        }
        return best;
    }

    private double efficiency(EndCrystalEntity crystal, LivingEntity target) {
        double tx = target.getX();
        double tz = target.getZ();
        if (predictAttackVelocity.isEnabled()) {
            Vec3d v = target.getVelocity();
            tx += v.x * 3.0;
            tz += v.z * 3.0;
        }
        double dx = crystal.getX() - tx;
        double dz = crystal.getZ() - tz;
        double targetDist = Math.sqrt(dx * dx + dz * dz);
        double selfDist = mc.player.distanceTo(crystal);
        return Math.max(0.0, 1.0 - targetDist / range.getValue()) - Math.max(0.0, 1.0 - selfDist / range.getValue()) * 0.5;
    }

    private void rotateTo(EndCrystalEntity crystal, double speed) {
        double dx = crystal.getX() - mc.player.getX();
        double dz = crystal.getZ() - mc.player.getZ();
        float want = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float diff = MathHelper.wrapDegrees(want - mc.player.getYaw());
        float step = (float) speed * 0.7f;
        mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(diff, -step, step));
    }

    private boolean heldCrystal() {
        try {
            Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
            return id != null && id.getPath().equals("end_crystal");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!centerScreen.isEnabled() || mc.textRenderer == null || mc.getWindow() == null) return;
        String info = "CrystalAura [" + crystalCount + "]" + (lastTarget != null ? " " + lastTarget.getName().getString() : "");
        int x = mc.getWindow().getScaledWidth() / 2 - 70;
        int y = mc.getWindow().getScaledHeight() / 2 + 20;
        RenderUtils.drawText(context, mc.textRenderer, info, x, y, 0xFFFFFFFF, true);
    }
}
