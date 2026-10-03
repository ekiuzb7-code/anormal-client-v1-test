package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class SilentAura extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Target acquisition range", 4.2, 2.0, 6.0, 0.1);
    public final NumberSetting maxAngle = new NumberSetting("Max Angle", "Max angle from crosshair for valid target", 120.0, 10.0, 360.0, 5.0);
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target priority", "Distance", "Distance", "Yaw", "Armor", "Threat", "Health");
    public final ModeSetting targetArea = new ModeSetting("Target Area", "Aim point on hitbox", "Closest", "Center", "Closest");
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting hostiles = new BooleanSetting("Hostiles", "Target hostile mobs", true);
    public final BooleanSetting passives = new BooleanSetting("Passives", "Target passive mobs", false);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Target invisible entities", false);
    public final BooleanSetting teams = new BooleanSetting("Ignore Teammates", "Skip same-team players", true);
    public final ModeSetting clickMode = new ModeSetting("Click Mode", "CPS or Trigger timing", "Trigger", "CPS", "Trigger");
    public final NumberSetting minAps = new NumberSetting("Min APS", "Min attacks per second (CPS mode)", 9.0, 1.0, 20.0, 0.5);
    public final NumberSetting maxAps = new NumberSetting("Max APS", "Max attacks per second (CPS mode)", 12.0, 1.0, 20.0, 0.5);
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Server-side head movement speed", 8.0, 1.0, 20.0, 0.5);
    public final NumberSetting extraSwing = new NumberSetting("Extra Swing", "Swing distance beyond attack range", 1.0, 0.0, 3.0, 0.1);
    public final NumberSetting extraDelay = new NumberSetting("Extra Delay", "Ticks after cooldown ready (neg = early)", 1.0, -10.0, 20.0, 1.0);
    public final NumberSetting mouseOverDelay = new NumberSetting("Mouse Over Delay", "Ticks hovering before attack", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting selectFirstHit = new BooleanSetting("Select First Hit", "Wait for opponent to hit you first", false);
    public final BooleanSetting ignoreActivationClick = new BooleanSetting("Ignore Activation Click", "Ignore first manual click unless hovering + ready", false);
    public final BooleanSetting airCrits = new BooleanSetting("Air Crits", "Only attack airborne on falling crit", false);
    public final BooleanSetting shieldCheck = new BooleanSetting("Shield Check", "Skip actively shielding players", true);
    public final NumberSetting targetMissChance = new NumberSetting("Target Miss %", "Chance to swing with no target (miss)", 5.0, 0.0, 100.0, 1.0);
    public final NumberSetting earlyHitChance = new NumberSetting("Early Hit %", "Chance to attack before cooldown ready", 0.0, 0.0, 100.0, 1.0);
    public final BooleanSetting requireMouseDown = new BooleanSetting("Require Mouse Down", "Only active while holding attack", false);
    public final BooleanSetting breakBlocks = new BooleanSetting("Break Blocks", "Pause while breaking blocks", true);
    public final NumberSetting breakDelay = new NumberSetting("Break Delay", "Ticks before pausing for block break", 4.0, 0.0, 20.0, 1.0);
    public final BooleanSetting disableOnDeath = new BooleanSetting("Disable on Death", "Auto-disable on death", true);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", "Only with sword/axe/mace held", false);
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Highlight targeted/attacked entities", false);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Color for targeted entities", ColorUtils.rgba(0, 230, 255, 255));
    public final ColorSetting attackColor = new ColorSetting("Attack Color", "Color for attacked entities", ColorUtils.rgba(255, 60, 60, 255));

    private LivingEntity currentTarget;
    private LivingEntity lastAttacked;
    private int hoverTicks;
    private int breakTicks;
    private int cpsTicks;
    private boolean firstHitTaken;
    private boolean activationIgnored;

    public SilentAura() {
        super("SilentAura", "AimAssist + AutoClicker without moving client view (server-side aim)", Category.UZNY11);
        addSetting(range);
        addSetting(maxAngle);
        addSetting(targetMode);
        addSetting(targetArea);
        addSetting(players);
        addSetting(hostiles);
        addSetting(passives);
        addSetting(invisibles);
        addSetting(teams);
        addSetting(clickMode);
        addSetting(minAps);
        addSetting(maxAps);
        addSetting(aimSpeed);
        addSetting(extraSwing);
        addSetting(extraDelay);
        addSetting(mouseOverDelay);
        addSetting(selectFirstHit);
        addSetting(ignoreActivationClick);
        addSetting(airCrits);
        addSetting(shieldCheck);
        addSetting(targetMissChance);
        addSetting(earlyHitChance);
        addSetting(requireMouseDown);
        addSetting(breakBlocks);
        addSetting(breakDelay);
        addSetting(disableOnDeath);
        addSetting(weaponOnly);
        addSetting(showTarget);
        addSetting(targetColor);
        addSetting(attackColor);
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        lastAttacked = null;
        hoverTicks = 0;
        breakTicks = 0;
        cpsTicks = 0;
        firstHitTaken = false;
        activationIgnored = !ignoreActivationClick.isEnabled();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null) return;

        if (disableOnDeath.isEnabled() && !mc.player.isAlive()) {
            setEnabled(false);
            return;
        }
        if (!mc.player.isAlive()) {
            firstHitTaken = false;
            currentTarget = null;
            return;
        }

        if (mc.player.hurtTime > 0) firstHitTaken = true;

        if (weaponOnly.isEnabled() && !isWeapon(mc.player.getMainHandStack().getItem())) {
            currentTarget = null;
            return;
        }

        if (requireMouseDown.isEnabled() && !mc.options.attackKey.isPressed()) {
            currentTarget = null;
            hoverTicks = 0;
            return;
        }

        if (breakBlocks.isEnabled() && mc.options.attackKey.isPressed() && mc.targetedEntity == null
                && mc.crosshairTarget != null) {
            if (++breakTicks < breakDelay.getValue().intValue()) {
            } else {
                currentTarget = null;
                return;
            }
        } else {
            breakTicks = 0;
        }

        currentTarget = selectTarget();

        if (currentTarget == null) {
            hoverTicks = 0;
            if (clickMode.is("CPS") && roll(targetMissChance.getValue()) && cpsReady()) {
                mc.player.swingHand(Hand.MAIN_HAND);
                scheduleNextCps();
            }
            return;
        }

        boolean hovering = isHovering(currentTarget);
        hoverTicks = hovering ? hoverTicks + 1 : 0;

        if (clickMode.is("CPS")) {
            if (cpsReady() && passesCpsGates(currentTarget)) {
                attack(currentTarget);
                scheduleNextCps();
            }
        } else {
            if (triggerReady(currentTarget)) attack(currentTarget);
        }
    }

    private LivingEntity selectTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (e instanceof EndCrystalEntity) continue;
            if (!invisibles.isEnabled() && living.isInvisible()) continue;
            if (living instanceof PlayerEntity p) {
                if (!players.isEnabled()) continue;
                if (teams.isEnabled() && mc.player.isTeammate(p)) continue;
            } else if (e instanceof HostileEntity) {
                if (!hostiles.isEnabled()) continue;
            } else if (e instanceof PassiveEntity) {
                if (!passives.isEnabled()) continue;
            }

            double dist = mc.player.distanceTo(living);
            if (dist > range.getValue()) continue;

            Vec3d aimPoint = aimPoint(living);
            Vec3d toTarget = aimPoint.subtract(eye).normalize();
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dotProduct(toTarget)))));
            if (angle > maxAngle.getValue()) continue;

            double score = switch (targetMode.getValue().toLowerCase()) {
                case "yaw" -> angle;
                case "health" -> living.getHealth() + living.getAbsorptionAmount();
                case "armor" -> -living.getArmor();
                case "threat" -> -threatScore(living);
                default -> dist;
            };
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private Vec3d aimPoint(LivingEntity living) {
        if (targetArea.is("Center"))
            return new Vec3d(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
        Vec3d eye = mc.player.getEyePos();
        double y = Math.max(living.getY(), Math.min(eye.y, living.getY() + living.getHeight()));
        return new Vec3d(living.getX(), y, living.getZ());
    }

    private double threatScore(LivingEntity living) {
        double score = living.getHealth();
        if (living instanceof PlayerEntity p) {
            String heldId = heldItemId(p);
            if (heldId.endsWith("_sword")) score += 4;
            if (heldId.endsWith("_axe")) score += 3;
        }
        return score;
    }

    private boolean isHovering(LivingEntity living) {
        if (mc.targetedEntity == living) return true;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d toTarget = aimPoint(living).subtract(eye).normalize();
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dotProduct(toTarget)))));
        return angle < 3.0;
    }

    private boolean cpsReady() {
        return --cpsTicks <= 0;
    }

    private void scheduleNextCps() {
        double min = Math.min(minAps.getValue(), maxAps.getValue());
        double max = Math.max(minAps.getValue(), maxAps.getValue());
        double aps = min + Math.random() * Math.max(0.01, max - min);
        cpsTicks = (int) Math.max(1, Math.round(20.0 / Math.max(0.5, aps)));
    }

    private boolean passesCpsGates(LivingEntity target) {
        float progress = mc.player.getAttackCooldownProgress(0.5f);
        if (progress < 0.95f && !roll(earlyHitChance.getValue())) return false;
        return passesSharedGates(target);
    }

    private boolean triggerReady(LivingEntity target) {
        float progress = mc.player.getAttackCooldownProgress(0.5f);
        int needTicks = extraDelay.getValue().intValue();
        boolean cooldownOk = needTicks >= 0
                ? progress >= 1.0f && hoverTicks >= needTicks
                : progress >= Math.max(0.0f, 1.0f + needTicks / 20.0f);
        if (!cooldownOk && !roll(earlyHitChance.getValue())) return false;
        if (hoverTicks < mouseOverDelay.getValue().intValue()) return false;
        return passesSharedGates(target);
    }

    private boolean passesSharedGates(LivingEntity target) {
        if (selectFirstHit.isEnabled() && !firstHitTaken) return false;

        if (!activationIgnored && requireMouseDown.isEnabled()) {
            activationIgnored = true;
            if (!isHovering(target)) return false;
        }

        if (airCrits.isEnabled() && !mc.player.isOnGround() && !mc.player.isGliding()) {
            if (mc.player.getVelocity().y >= -0.2) return false;
        }

        if (shieldCheck.isEnabled() && target instanceof PlayerEntity p && p.isBlocking()) {
            if (!hasAxeCounter()) return false;
        }
        return true;
    }

    private boolean hasAxeCounter() {
        try {
            com.anormal.client.module.ModuleManager.getModules().stream()
                    .filter(m -> m.isEnabled() && (m.getName().equalsIgnoreCase("ShieldBreaker")
                            || m.getName().equalsIgnoreCase("HitSwap")
                            || m.getName().equalsIgnoreCase("AutoMace")));
        } catch (Throwable ignored) {}
        return isAxe(mc.player.getMainHandStack().getItem());
    }

    private void attack(LivingEntity target) {
        double dist = mc.player.distanceTo(target);
        boolean inRange = dist <= range.getValue() + extraSwing.getValue();
        mc.player.swingHand(Hand.MAIN_HAND);
        if (inRange) {
            mc.interactionManager.attackEntity(mc.player, target);
            lastAttacked = target;
        }
    }

    private boolean roll(double percentChance) {
        if (percentChance <= 0) return false;
        if (percentChance >= 100) return true;
        return Math.random() * 100.0 < percentChance;
    }

    private static String heldItemId(PlayerEntity p) {
        try {
            Identifier id = Registries.ITEM.getId(p.getMainHandStack().getItem());
            return id == null ? "" : id.getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isWeapon(net.minecraft.item.Item item) {
        try {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) return false;
            String path = id.getPath();
            return path.endsWith("_sword") || path.endsWith("_axe")
                    || path.equals("mace") || path.equals("trident");
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

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    public LivingEntity getLastAttacked() {
        return lastAttacked;
    }
}
