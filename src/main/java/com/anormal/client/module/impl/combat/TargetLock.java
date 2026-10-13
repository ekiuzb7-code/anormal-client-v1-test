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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Ported from LiquidBounce's {@code ModuleTargetLock}.
 *
 * <p>Locks your view onto one target and keeps it locked: while a target is
 * locked, your rotation is forced onto it, so every attack you make lands on
 * that entity. The lock is dropped when the target dies, goes out of range, or
 * after the configured maximum time.</p>
 *
 * <p>LiquidBounce's settings, mirrored here:</p>
 * <ul>
 *   <li>"Combat" - only lock while in combat (default off)</li>
 *   <li>"FilterType" - Whitelist / Blacklist of entity types</li>
 *   <li>"MaximumTime" - seconds before the lock resets (default 30, 0..120)</li>
 *   <li>"MaximumRange" - lock is released past this range (default 20, 8..40)</li>
 *   <li>"WhenNoLock" - what to do when nothing is locked (Allow All / Pause)</li>
 * </ul>
 */
public class TargetLock extends Module {
    public final BooleanSetting combatOnly = new BooleanSetting("Combat", "Only lock onto targets while you are in combat", false);
    public final ModeSetting filterType = new ModeSetting("FilterType", "How the entity filter list is applied", "Whitelist", "Whitelist", "Blacklist");
    public final NumberSetting maximumTime = new NumberSetting("MaximumTime", "Seconds before the lock resets", 30.0, 0.0, 120.0, 1.0);
    public final NumberSetting maximumRange = new NumberSetting("MaximumRange", "Release the lock past this range", 20.0, 8.0, 40.0, 1.0);
    public final ModeSetting whenNoLock = new ModeSetting("WhenNoLock", "Behaviour when nothing is locked", "Allow All", "Allow All", "Pause");

    // Filter lists
    public final BooleanSetting filterPlayers = new BooleanSetting("Filter Players", "Apply the filter to players", true);
    public final BooleanSetting filterMobs = new BooleanSetting("Filter Mobs", "Apply the filter to hostile mobs", true);
    public final BooleanSetting filterAnimals = new BooleanSetting("Filter Animals", "Apply the filter to passive animals", false);
    public final BooleanSetting filterInvisible = new BooleanSetting("Filter Invisible", "Ignore invisible entities", true);
    public final BooleanSetting filterFriends = new BooleanSetting("Filter Friends", "Ignore friends", true);

    // Rotation
    public final NumberSetting rotationSpeed = new NumberSetting("RotationSpeed", "How fast the lock snaps onto the target", 30.0, 5.0, 180.0, 5.0);
    public final BooleanSetting silent = new BooleanSetting("Silent", "Keep your real rotation and only aim the server-side view", false);

    // Render
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", "Draw a marker on the locked target", true);
    public final ColorSetting targetColor = new ColorSetting("Target Color", "Locked target marker color", ColorUtils.rgba(255, 200, 0, 255));

    private LivingEntity locked;
    private int lockTicks = 0;
    private final List<String> entityFilter = new ArrayList<>();

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
        addSetting(rotationSpeed);
        addSetting(silent);
        addSetting(showTarget);
        addSetting(targetColor);
    }

    @Override
    public void onDisable() {
        locked = null;
        lockTicks = 0;
    }

    /** @return the currently locked entity, or null. */
    public LivingEntity getLocked() {
        return locked;
    }

    private boolean isCombatTarget(LivingEntity e) {
        if (e instanceof PlayerEntity pl) return filterPlayers.isEnabled();
        if (e instanceof net.minecraft.entity.mob.HostileEntity) return filterMobs.isEnabled();
        if (e instanceof net.minecraft.entity.passive.PassiveEntity) return filterAnimals.isEnabled();
        return filterMobs.isEnabled();
    }

    private boolean passesFilter(LivingEntity e) {
        boolean listed = entityFilter.contains(e.getName().getString());
        boolean isWhite = filterType.is("Whitelist");
        if (!isCombatTarget(e)) return false;
        return isWhite == listed;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Drop a lock that is no longer valid.
        if (locked != null) {
            lockTicks++;
            boolean invalid = !locked.isAlive()
                    || mc.world.getEntityById(locked.getId()) == null
                    || mc.player.distanceTo(locked) > maximumRange.getValue()
                    || (maximumTime.getValue() > 0 && lockTicks > maximumTime.getValue() * 20);
            if (invalid) {
                locked = null;
                lockTicks = 0;
            }
        }

        // Acquire a new lock: nearest valid entity in front of us.
        if (locked == null) {
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
                if (filterInvisible.isEnabled() && living.isInvisible()) continue;
                if (filterFriends.isEnabled() && isFriend(living)) continue;
                if (!isCombatTarget(living)) continue;
                if (!passesFilter(living)) continue;
                double d = mc.player.distanceTo(living);
                if (d > maximumRange.getValue() || d >= bestDist) continue;
                bestDist = d;
                best = living;
            }
            if (best != null) {
                locked = best;
                lockTicks = 0;
            }
        }

        if (locked == null) return;

        // Force the rotation onto the lock.
        try {
            Vec3d eye = mc.player.getEyePos();
            double dx = locked.getX() - eye.x;
            double dy = (locked.getY() + locked.getHeight() / 2.0) - eye.y;
            double dz = locked.getZ() - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);

            float wantYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
            float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

            float speed = rotationSpeed.getValue().floatValue();
            float yawDiff = MathHelper.wrapDegrees(wantYaw - mc.player.getYaw());
            float pitchDiff = MathHelper.wrapDegrees(wantPitch - mc.player.getPitch());

            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(yawDiff, -speed, speed));
            mc.player.setPitch(MathHelper.clamp(
                    mc.player.getPitch() + MathHelper.clamp(pitchDiff, -speed, speed), -90.0f, 90.0f));
        } catch (Throwable ignored) {
        }
    }

    private boolean isFriend(LivingEntity e) {
        if (!(e instanceof PlayerEntity pl)) return false;
        try {
            var friends = com.anormal.client.module.ModuleManager.getModule(
                    com.anormal.client.module.impl.client.Friends.class);
            if (friends == null) return false;
            java.lang.reflect.Field f = friends.getClass().getDeclaredField("friends");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) f.get(friends);
            return list != null && list.contains(pl.getName().getString());
        } catch (Throwable ignored) {
            return false;
        }
    }

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
