package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.TargetFilter;
import com.anormal.client.rotation.Bone;
import com.anormal.client.rotation.Rotation;
import com.anormal.client.rotation.RotationController;
import com.anormal.client.rotation.Smoothing;
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
import net.minecraft.util.math.Vec3d;

/**
 * Keeps your aim glued to one target until it dies, escapes or the timer runs out.
 *
 * <p><b>Why the old version never locked anything.</b> It filtered entities against an
 * {@code ArrayList<String> entityFilter} that was declared but never filled - so in Whitelist mode
 * {@code listed} was always false and every candidate was rejected, and in Blacklist mode it
 * rejected everything as well. The list is gone; the filter now uses the type toggles, which are
 * actually visible in the GUI.</p>
 */
public class TargetLock extends Module {

    public final BooleanSetting combatOnly = new BooleanSetting("Combat",
            "Only lock while you are in combat (recently hurt or attacking)", false);

    public final ModeSetting filterType = new ModeSetting("FilterType",
            "Whitelist = only the checked types, Blacklist = everything except the checked types",
            "Whitelist", "Whitelist", "Blacklist");

    public final NumberSetting maximumTime = new NumberSetting("MaximumTime",
            "Seconds before the lock resets (0 = never)", 30.0, 0.0, 120.0, 1.0);

    public final NumberSetting maximumRange = new NumberSetting("MaximumRange",
            "Release the lock past this range", 20.0, 4.0, 40.0, 1.0);

    public final ModeSetting whenNoLock = new ModeSetting("WhenNoLock",
            "Allow All keeps searching, Pause only locks the entity you attack",
            "Allow All", "Allow All", "Pause");

    // ---- Type filter (drives FilterType) ----
    public final BooleanSetting filterPlayers = new BooleanSetting("Players", "Players", true);
    public final BooleanSetting filterMobs = new BooleanSetting("Mobs", "Hostile mobs", true);
    public final BooleanSetting filterAnimals = new BooleanSetting("Animals", "Passive animals", false);
    public final BooleanSetting filterInvisible = new BooleanSetting("Ignore Invisible", "Skip invisible entities", true);
    public final BooleanSetting filterFriends = new BooleanSetting("Ignore Friends", "Skip friends", true);
    public final BooleanSetting useTargetFilter = new BooleanSetting("Use Target Filter", "Apply TargetFilter rules", true);
    public final BooleanSetting lineOfSight = new BooleanSetting("Line Of Sight", "Skip targets behind blocks", false);

    // ---- Rotation ----
    public final ModeSetting smoothing = new ModeSetting("Smoothing", "How the lock approaches the target",
            "Ease Out Cubic", Smoothing.NAMES);
    public final NumberSetting smoothness = new NumberSetting("Smoothness",
            "0 = snap instantly, 100 = slowest", 35.0, 0.0, 100.0, 1.0);
    public final BooleanSetting silent = new BooleanSetting("Silent",
            "Keep your camera free and only tell the server", false);
    public final BooleanSetting gcd = new BooleanSetting("GCD Fix",
            "Round the rotation to your mouse sensitivity step", true);

    // ---- Render ----
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Draw a marker on the locked target", true);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Locked target marker colour",
            ColorUtils.rgba(255, 200, 0, 255));

    // ------------------------------------------------------------------
    //  State
    // ------------------------------------------------------------------
    private final RotationController rotation = new RotationController();

    private LivingEntity locked;
    private int lockTicks = 0;
    private int attackLockTicks = 0;

    public TargetLock() {
        super("TargetLock", "Locks your aim onto one target until it is gone", Category.COMBAT);

        addSetting(combatOnly);
        addSetting(filterType);
        addSetting(maximumTime);
        addSetting(maximumRange);
        addSetting(whenNoLock);

        addSetting(filterPlayers);
        addSetting(filterMobs);
        addSetting(filterAnimals);
        addSetting(filterInvisible);
        addSetting(filterFriends);
        addSetting(useTargetFilter);
        addSetting(lineOfSight);

        addSetting(smoothing);
        addSetting(smoothness);
        addSetting(silent);
        addSetting(gcd);

        addSetting(showTarget);
        addSetting(targetColor);
    }

    @Override
    public void onDisable() {
        locked = null;
        lockTicks = 0;
        attackLockTicks = 0;
        rotation.reset();
    }

    public LivingEntity getLocked() {
        return locked;
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (attackLockTicks > 0) attackLockTicks--;

        // Drop an invalid lock.
        if (locked != null) {
            lockTicks++;
            boolean invalid = !locked.isAlive()
                    || locked.isRemoved()
                    || mc.world.getEntityById(locked.getId()) == null
                    || mc.player.distanceTo(locked) > maximumRange.getValue()
                    || (maximumTime.getValue() > 0 && lockTicks > maximumTime.getValue() * 20);
            if (invalid) {
                locked = null;
                lockTicks = 0;
                rotation.reset();
            }
        }

        // Acquire a new lock.
        if (locked == null) {
            if (whenNoLock.is("Pause") && attackLockTicks <= 0) return;
            LivingEntity best = findBest();
            if (best == null) return;
            locked = best;
            lockTicks = 0;
            rotation.reset();
        }

        aimAt(locked);
    }

    private void aimAt(LivingEntity target) {
        try {
            Vec3d point = bestBone(target);
            Rotation aim = rotation.step(
                    point,
                    smoothness.getValue().floatValue() / 100.0f,
                    Smoothing.byName(smoothing.getValue()),
                    true,
                    silent.isEnabled());
            rotation.apply(aim, silent.isEnabled(), gcd.isEnabled());
        } catch (Throwable ignored) {
        }
    }

    /** Closest point of the hitbox to the crosshair - keeps the lock feeling natural. */
    private Vec3d bestBone(LivingEntity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        Vec3d best = null;
        double bestAngle = 180.0;
        for (Bone bone : Bone.ALL) {
            Vec3d point = bone.point(mc.player, target);
            double angle = Rotation.angle(look, point.subtract(eye));
            if (angle >= bestAngle) continue;
            best = point;
            bestAngle = angle;
        }
        if (best != null) return best;
        return new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
    }

    private LivingEntity findBest() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
            if (living.isSpectator()) continue;
            if (!typeAllowed(living)) continue;
            if (filterInvisible.isEnabled() && living.isInvisible()) continue;
            if (filterFriends.isEnabled() && living instanceof PlayerEntity player
                    && Friends.isFriend(player.getGameProfile().name())) continue;
            if (useTargetFilter.isEnabled() && !TargetFilter.isValid(living)) continue;
            if (combatOnly.isEnabled() && !inCombat()) continue;

            double distance = mc.player.distanceTo(living);
            if (distance > maximumRange.getValue()) continue;
            if (lineOfSight.isEnabled() && !mc.player.canSee(living)) continue;

            // Prefer what the crosshair is closest to, not simply the nearest entity.
            Vec3d point = new Vec3d(living.getX(), living.getY() + living.getHeight() * 0.5, living.getZ());
            double angle = Rotation.angle(look, point.subtract(eye));
            double score = angle + distance * 2.0;
            if (score >= bestScore) continue;

            best = living;
            bestScore = score;
        }
        return best;
    }

    /** Whitelist keeps only the checked types, Blacklist keeps everything else. */
    private boolean typeAllowed(LivingEntity living) {
        boolean checked;
        if (living instanceof PlayerEntity) checked = filterPlayers.isEnabled();
        else if (living instanceof HostileEntity) checked = filterMobs.isEnabled();
        else if (living instanceof PassiveEntity) checked = filterAnimals.isEnabled();
        else checked = filterMobs.isEnabled();

        return filterType.is("Whitelist") ? checked : !checked;
    }

    private boolean inCombat() {
        try {
            return mc.player.hurtTime > 0 || attackLockTicks > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called by the attack hook so a manual hit immediately locks its target. */
    public void lockOn(Entity entity) {
        if (!isEnabled() || !(entity instanceof LivingEntity living) || !living.isAlive()) return;
        if (locked != null && locked != living) rotation.reset();
        locked = living;
        lockTicks = 0;
        attackLockTicks = 60;
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || locked == null || !showTarget.isEnabled()) return;
        int[] s = ProjectionUtil.project(
                ProjectionUtil.lerpPos(locked, tickDelta).add(0.0, locked.getHeight() + 0.4, 0.0), tickDelta);
        if (s == null) return;

        int col = targetColor.getValue();
        RenderUtils.drawBorder(context, s[0] - 12, s[1] - 4, s[0] + 12, s[1] + 4, 1, col);
        if (mc.textRenderer != null) {
            String label = "LOCKED";
            RenderUtils.drawText(context, mc.textRenderer, label,
                    s[0] - mc.textRenderer.getWidth(label) / 2, s[1] - 14, col, true);
        }
    }
}
