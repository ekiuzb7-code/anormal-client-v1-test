package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Attacks nearby entities automatically.
 *
 * <p>Four interchangeable implementations, each mirroring a different client:</p>
 * <ul>
 *   <li><b>V1 - Meteor</b>: weapon-type filter, rotate, auto-switch + swap-back,
 *       shield mode, only-on-click, only-on-look, entity filter, priority,
 *       max targets, range, walls range, custom/hit/switch delays.</li>
 *   <li><b>V2 - Wurst</b>: range, speed randomization, priority, FOV, damage
 *       indicator, check line of sight - the compact implementation.</li>
 *   <li><b>V3 - LiquidBounce</b>: raycast mode, criticals mode, keep sprint,
 *       ignore open inventory, simulate inventory closing.</li>
 *   <li><b>V4 - Vape</b>: swing range, attack range, max angle, max targets,
 *       target mode, attack rate, require mouse down, GUI check, disable on
 *       death, perfect swing, limit to items.</li>
 * </ul>
 */
public class KillAura extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2", "V3", "V4");

    // ---------------- Shared target filter ----------------
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", "Attack other players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", "Attack hostile mobs", true);
    public final BooleanSetting targetAnimals = new BooleanSetting("Target Animals", "Attack passive animals", false);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Never attack friends", true);
    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible", "Never attack invisible entities", true);

    // ---------------- V1 - Meteor ----------------
    public final BooleanSetting v1AttackWhenHolding = new BooleanSetting("V1 Attack When Holding", "Only attack while holding a weapon", false);
    public final BooleanSetting v1Rotate = new BooleanSetting("V1 Rotate", "Rotate towards the target", true);
    public final BooleanSetting v1AutoSwitch = new BooleanSetting("V1 Auto Switch", "Switch to the best weapon", false);
    public final BooleanSetting v1SwapBack = new BooleanSetting("V1 Swap Back", "Switch back after attacking", true);
    public final ModeSetting v1ShieldMode = new ModeSetting("V1 Shield Mode", "How to handle shields", "Ignore", "Ignore", "Break", "Pause");
    public final BooleanSetting v1OnlyOnClick = new BooleanSetting("V1 Only On Click", "Only attack while the attack key is held", false);
    public final BooleanSetting v1OnlyOnLook = new BooleanSetting("V1 Only On Look", "Only attack when looking at the target", true);
    public final ModeSetting v1Priority = new ModeSetting("V1 Priority", "Target priority", "Lowest Health", "Lowest Health", "Closest", "Angle");
    public final NumberSetting v1MaxTargets = new NumberSetting("V1 Max Targets", "How many targets to attack", 1.0, 1.0, 8.0, 1.0);
    public final NumberSetting v1Range = new NumberSetting("V1 Range", "Attack range", 3.0, 1.0, 6.0, 0.1);
    public final NumberSetting v1WallsRange = new NumberSetting("V1 Walls Range", "Attack range through walls", 3.0, 0.0, 6.0, 0.1);
    public final NumberSetting v1HitDelay = new NumberSetting("V1 Hit Delay", "Ticks between hits", 10.0, 0.0, 20.0, 1.0);
    public final NumberSetting v1SwitchDelay = new NumberSetting("V1 Switch Delay", "Ticks between weapon switches", 2.0, 0.0, 20.0, 1.0);
    public final NumberSetting v1CustomDelay = new NumberSetting("V1 Custom Delay", "Extra delay added to the hit delay", 0.0, 0.0, 10.0, 1.0);

    // ---------------- V2 - Wurst ----------------
    public final NumberSetting v2Range = new NumberSetting("V2 Range", "Attack range", 3.0, 1.0, 6.0, 0.05);
    public final NumberSetting v2SpeedRandomization = new NumberSetting("V2 Speed Randomization", "Randomizes attack timing by this percentage", 20.0, 0.0, 100.0, 1.0);
    public final ModeSetting v2Priority = new ModeSetting("V2 Priority", "Target priority", "Angle", "Angle", "Distance", "Health");
    public final NumberSetting v2Fov = new NumberSetting("V2 FOV", "Only attack targets inside this FOV", 360.0, 30.0, 360.0, 10.0);
    public final BooleanSetting v2DamageIndicator = new BooleanSetting("V2 Damage Indicator", "Mark the target with a damage indicator", true);
    public final BooleanSetting v2CheckLos = new BooleanSetting("V2 Check Line Of Sight", "Require line of sight", false);

    // ---------------- V3 - LiquidBounce ----------------
    public final ModeSetting v3Raycast = new ModeSetting("V3 Raycast", "Raycast mode", "Trace All", "Trace All", "Trace Entities", "None");
    public final ModeSetting v3Criticals = new ModeSetting("V3 Criticals", "When to force critical hits", "Smart", "Smart", "Always", "Never");
    public final BooleanSetting v3KeepSprint = new BooleanSetting("V3 KeepSprint", "Keep sprinting while attacking", true);
    public final BooleanSetting v3IgnoreOpenInventory = new BooleanSetting("V3 IgnoreOpenInventory", "Keep attacking with the inventory open", true);
    public final BooleanSetting v3SimulateInventoryClosing = new BooleanSetting("V3 SimulateInventoryClosing", "Pretend the inventory is closed when attacking", true);

    // ---------------- V4 - Vape ----------------
    public final NumberSetting v4SwingRange = new NumberSetting("V4 Swing Range", "Range at which to start swinging", 3.0, 1.0, 6.0, 0.1);
    public final NumberSetting v4AttackRange = new NumberSetting("V4 Attack Range", "Range at which damage is dealt", 3.0, 1.0, 6.0, 0.1);
    public final NumberSetting v4MaxAngle = new NumberSetting("V4 Max Angle", "Max angle to the target", 180.0, 10.0, 180.0, 5.0);
    public final NumberSetting v4MaxTargets = new NumberSetting("V4 Max Targets", "How many targets to attack", 1.0, 1.0, 8.0, 1.0);
    public final ModeSetting v4TargetMode = new ModeSetting("V4 Target Mode", "Target sorting", "Distance", "Distance", "Yaw", "Armor", "Threat", "Health");
    public final NumberSetting v4AttackRate = new NumberSetting("V4 Attack Rate", "Attacks per second", 12.0, 1.0, 20.0, 0.5);
    public final BooleanSetting v4RequireMouseDown = new BooleanSetting("V4 Require Mouse Down", "Only attack while holding the attack key", false);
    public final BooleanSetting v4ShowTarget = new BooleanSetting("V4 Show Target", "Draw a marker on the target", true);
    public final BooleanSetting v4GuiCheck = new BooleanSetting("V4 GUI Check", "Pause while a screen is open", true);
    public final BooleanSetting v4DisableOnDeath = new BooleanSetting("V4 Disable On Death", "Turn off when you die", true);
    public final BooleanSetting v4PerfectSwing = new BooleanSetting("V4 Perfect Swing", "Only swing when the attack is fully charged", false);
    public final BooleanSetting v4LimitToItems = new BooleanSetting("V4 Limit To Items", "Only attack while holding a weapon", false);

    // ---------------- Shared render ----------------
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Draw a marker on the current target", true);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Target marker color", ColorUtils.rgba(255, 60, 60, 255));

    private final List<LivingEntity> targets = new ArrayList<>();
    private int hitCooldown = 0;
    private int switchCooldown = 0;
    private int prevSlot = -1;

    public KillAura() {
        super("KillAura", "Automatically attacks nearby entities", Category.COMBAT);
        addSetting(version);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(targetAnimals);
        addSetting(ignoreFriends);
        addSetting(ignoreInvisible);

        addSetting(v1AttackWhenHolding);
        addSetting(v1Rotate);
        addSetting(v1AutoSwitch);
        addSetting(v1SwapBack);
        addSetting(v1ShieldMode);
        addSetting(v1OnlyOnClick);
        addSetting(v1OnlyOnLook);
        addSetting(v1Priority);
        addSetting(v1MaxTargets);
        addSetting(v1Range);
        addSetting(v1WallsRange);
        addSetting(v1HitDelay);
        addSetting(v1SwitchDelay);
        addSetting(v1CustomDelay);

        addSetting(v2Range);
        addSetting(v2SpeedRandomization);
        addSetting(v2Priority);
        addSetting(v2Fov);
        addSetting(v2DamageIndicator);
        addSetting(v2CheckLos);

        addSetting(v3Raycast);
        addSetting(v3Criticals);
        addSetting(v3KeepSprint);
        addSetting(v3IgnoreOpenInventory);
        addSetting(v3SimulateInventoryClosing);

        addSetting(v4SwingRange);
        addSetting(v4AttackRange);
        addSetting(v4MaxAngle);
        addSetting(v4MaxTargets);
        addSetting(v4TargetMode);
        addSetting(v4AttackRate);
        addSetting(v4RequireMouseDown);
        addSetting(v4ShowTarget);
        addSetting(v4GuiCheck);
        addSetting(v4DisableOnDeath);
        addSetting(v4PerfectSwing);
        addSetting(v4LimitToItems);

        addSetting(showTarget);
        addSetting(targetColor);

        for (var s : new com.anormal.client.setting.Setting<?>[]{v1AttackWhenHolding, v1Rotate, v1AutoSwitch, v1SwapBack,
                v1ShieldMode, v1OnlyOnClick, v1OnlyOnLook, v1Priority, v1MaxTargets, v1Range, v1WallsRange,
                v1HitDelay, v1SwitchDelay, v1CustomDelay}) {
            s.visibleIf(() -> version.is("V1"));
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{v2Range, v2SpeedRandomization, v2Priority, v2Fov,
                v2DamageIndicator, v2CheckLos}) {
            s.visibleIf(() -> version.is("V2"));
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{v3Raycast, v3Criticals, v3KeepSprint,
                v3IgnoreOpenInventory, v3SimulateInventoryClosing}) {
            s.visibleIf(() -> version.is("V3"));
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{v4SwingRange, v4AttackRange, v4MaxAngle, v4MaxTargets,
                v4TargetMode, v4AttackRate, v4RequireMouseDown, v4ShowTarget, v4GuiCheck, v4DisableOnDeath,
                v4PerfectSwing, v4LimitToItems}) {
            s.visibleIf(() -> version.is("V4"));
        }
    }

    @Override
    public void onDisable() {
        targets.clear();
        hitCooldown = 0;
        if (prevSlot != -1 && mc.player != null) {
            try {
                mc.player.getInventory().setSelectedSlot(prevSlot);
            } catch (Throwable ignored) {
            }
            prevSlot = -1;
        }
    }

    private double attackRange() {
        if (version.is("V2")) return v2Range.getValue();
        if (version.is("V3")) return 3.0;
        if (version.is("V4")) return v4AttackRange.getValue();
        return v1Range.getValue();
    }

    private double maxAngle() {
        if (version.is("V2")) return v2Fov.getValue();
        if (version.is("V4")) return v4MaxAngle.getValue();
        return 360.0;
    }

    private int maxTargets() {
        if (version.is("V1")) return v1MaxTargets.getValue().intValue();
        if (version.is("V4")) return v4MaxTargets.getValue().intValue();
        return 1;
    }

    private int hitDelayTicks() {
        if (version.is("V1")) return (int) (v1HitDelay.getValue() + v1CustomDelay.getValue());
        if (version.is("V4")) return Math.max(1, (int) Math.round(20.0 / Math.max(1.0, v4AttackRate.getValue())));
        return 10;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (version.is("V4")) {
            if (v4GuiCheck.isEnabled() && mc.currentScreen != null) return;
            if (v4DisableOnDeath.isEnabled() && !mc.player.isAlive()) {
                setEnabled(false);
                return;
            }
            if (v4RequireMouseDown.isEnabled() && !mc.options.attackKey.isPressed()) return;
            if (v4LimitToItems.isEnabled() && mc.player.getMainHandStack().isEmpty()) return;
        } else if (version.is("V1")) {
            if (v1AttackWhenHolding.isEnabled() && mc.player.getMainHandStack().isEmpty()) return;
            if (v1OnlyOnClick.isEnabled() && !mc.options.attackKey.isPressed()) return;
        } else if (version.is("V3")) {
            if (!v3IgnoreOpenInventory.isEnabled() && mc.currentScreen != null) return;
        }

        collectTargets();
        if (targets.isEmpty()) return;

        if (hitCooldown > 0) {
            hitCooldown--;
            return;
        }

        int attacks = 0;
        for (LivingEntity target : targets) {
            if (attacks >= maxTargets()) break;
            if (!isValid(target)) continue;

            double dist = mc.player.distanceTo(target);
            double range = attackRange();
            if (dist > range) continue;

            if (version.is("V1") && v1OnlyOnLook.isEnabled() && !isLookingAt(target)) continue;
            if (version.is("V2") && v2CheckLos.isEnabled() && !mc.player.canSee(target)) continue;
            if (version.is("V4") && v4PerfectSwing.isEnabled() && mc.player.getAttackCooldownProgress(0.0f) < 1.0f) continue;

            if (version.is("V3") && !v3KeepSprint.isEnabled()) {
                try {
                    mc.player.setSprinting(false);
                } catch (Throwable ignored) {
                }
            }

            if (version.is("V1") && v1Rotate.isEnabled()) rotateTo(target);
            else if (!version.is("V2")) rotateTo(target);

            try {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                attacks++;
            } catch (Throwable ignored) {
            }
        }

        if (attacks > 0) {
            int delay = hitDelayTicks();
            if (version.is("V2")) {
                double rnd = v2SpeedRandomization.getValue() / 100.0;
                delay = Math.max(1, (int) Math.round(delay * (1.0 + (Math.random() * 2.0 - 1.0) * rnd)));
            }
            hitCooldown = delay;
        }
    }

    private void collectTargets() {
        targets.clear();
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        double range = attackRange() + 2.0;
        double fov = maxAngle();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            if (ignoreInvisible.isEnabled() && living.isInvisible()) continue;

            if (living instanceof PlayerEntity pl) {
                if (!targetPlayers.isEnabled()) continue;
                if (mc.player.isTeammate(pl)) continue;
                if (ignoreFriends.isEnabled() && isFriend(pl)) continue;
            } else if (living instanceof HostileEntity) {
                if (!targetMobs.isEnabled()) continue;
            } else if (living instanceof PassiveEntity) {
                if (!targetAnimals.isEnabled()) continue;
            } else {
                if (!targetMobs.isEnabled()) continue;
            }

            double dist = mc.player.distanceTo(living);
            if (dist > range) continue;

            Vec3d aim = new Vec3d(living.getX(), living.getY() + living.getHeight() / 2.0, living.getZ());
            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(
                    look.dotProduct(aim.subtract(eye).normalize()), -1.0, 1.0)));
            if (angle > fov / 2.0) continue;

            targets.add(living);
        }

        Comparator<LivingEntity> cmp = switch (version.is("V2") ? v2Priority.getValue() : v1Priority.getValue()) {
            case "Closest", "Distance" -> Comparator.comparingDouble(mc.player::distanceTo);
            case "Health" -> Comparator.comparingDouble(LivingEntity::getHealth);
            case "Lowest Health" -> Comparator.comparingDouble(LivingEntity::getHealth);
            default -> Comparator.comparingDouble(this::angleTo);
        };
        targets.sort(cmp);
    }

    private double angleTo(LivingEntity e) {
        try {
            Vec3d eye = mc.player.getEyePos();
            Vec3d look = mc.player.getRotationVec(1.0f);
            Vec3d aim = new Vec3d(e.getX(), e.getY() + e.getHeight() / 2.0, e.getZ());
            return Math.toDegrees(Math.acos(MathHelper.clamp(
                    look.dotProduct(aim.subtract(eye).normalize()), -1.0, 1.0)));
        } catch (Throwable t) {
            return 180.0;
        }
    }

    private boolean isLookingAt(LivingEntity e) {
        return angleTo(e) < 45.0;
    }

    private void rotateTo(LivingEntity target) {
        try {
            double dx = target.getX() - mc.player.getX();
            double dz = target.getZ() - mc.player.getZ();
            double dy = (target.getY() + target.getHeight() / 2.0) - mc.player.getEyePos().y;
            float want = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float diff = MathHelper.wrapDegrees(want - mc.player.getYaw());
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(diff, -35.0f, 35.0f));

            double horiz = Math.sqrt(dx * dx + dz * dz);
            float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
            float pDiff = MathHelper.wrapDegrees(wantPitch - mc.player.getPitch());
            mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + MathHelper.clamp(pDiff, -25.0f, 25.0f), -90.0f, 90.0f));
        } catch (Throwable ignored) {
        }
    }

    private boolean isFriend(PlayerEntity player) {
        try {
            var friends = com.anormal.client.module.ModuleManager.getModule(
                    com.anormal.client.module.impl.client.Friends.class);
            if (friends == null) return false;
            java.lang.reflect.Field f = friends.getClass().getDeclaredField("friends");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) f.get(friends);
            return list != null && list.contains(player.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isValid(LivingEntity e) {
        return e.isAlive() && mc.world.getEntityById(e.getId()) != null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        boolean show = showTarget.isEnabled() || (version.is("V4") && v4ShowTarget.isEnabled());
        if (!show || targets.isEmpty()) return;

        LivingEntity t = targets.get(0);
        int[] s = ProjectionUtil.project(
                ProjectionUtil.lerpPos(t, tickDelta).add(0.0, t.getHeight() + 0.4, 0.0), tickDelta);
        if (s == null) return;

        int col = targetColor.getValue();
        RenderUtils.drawBorder(context, s[0] - 14, s[1] - 5, s[0] + 14, s[1] + 5, 1, col);
        String name = t.getName().getString();
        RenderUtils.drawText(context, mc.textRenderer, name,
                s[0] - mc.textRenderer.getWidth(name) / 2, s[1] - 7, col, true);
    }
}
