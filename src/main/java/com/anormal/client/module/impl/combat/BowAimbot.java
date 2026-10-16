package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.AntiBot;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aims (and optionally fires) your bow at the best target.
 *
 * <p>Instead of pointing straight at the target it solves the real ballistic
 * equation for the arrow arc:</p>
 * <pre>
 *   h = d·tanθ − g·d² / (2·v²·cos²θ)
 * </pre>
 * <p>which reduces to a quadratic in {@code tanθ}. The flatter root is used by
 * default; "High Arc" picks the lobbed one. Movement prediction leads the
 * target by the estimated flight time.</p>
 */
public class BowAimbot extends Module {

    // ---- Targeting ----
    public final NumberSetting range = new NumberSetting("Range", "Maximum target distance", 40.0, 4.0, 128.0, 2.0);
    public final NumberSetting fov = new NumberSetting("FOV", "Only aim at targets within this cone (degrees)", 90.0, 5.0, 360.0, 5.0);
    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players", "Aim at other players", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", "Aim at hostile mobs", true);
    public final BooleanSetting targetAnimals = new BooleanSetting("Target Animals", "Aim at passive animals", false);
    public final BooleanSetting targetInvisible = new BooleanSetting("Target Invisible", "Aim at invisible entities", false);
    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends", "Never aim at friends from the Friends list", true);
    public final BooleanSetting ignoreBots = new BooleanSetting("Ignore Bots", "Never aim at entities AntiBot flagged", true);
    public final BooleanSetting ignoreNaked = new BooleanSetting("Ignore Naked", "Skip players with no armour", false);
    public final BooleanSetting ignoreDead = new BooleanSetting("Ignore Dead", "Skip dead or dying entities", true);
    public final ModeSetting priority = new ModeSetting("Priority", "How the best target is chosen", "Distance",
            "Distance", "Health", "Angle", "Armour");
    public final ModeSetting sortMode = new ModeSetting("Sort", "Order of the candidate list", "Nearest",
            "Nearest", "Furthest", "Lowest Health");

    // ---- Ballistics ----
    public final BooleanSetting predict = new BooleanSetting("Predict Movement", "Lead the target while it moves", true);
    public final NumberSetting predictionTicks = new NumberSetting("Prediction Ticks", "Extra ticks of lead", 0.0, 0.0, 20.0, 1.0);
    public final NumberSetting arrowSpeed = new NumberSetting("Arrow Speed", "Arrow speed in blocks per tick", 3.0, 1.0, 8.0, 0.1);
    public final NumberSetting gravity = new NumberSetting("Gravity", "Arrow gravity per tick squared", 0.05, 0.0, 0.2, 0.005);
    public final BooleanSetting highArc = new BooleanSetting("High Arc", "Use the lobbed trajectory instead of the flat one", false);
    public final BooleanSetting wallCheck = new BooleanSetting("Wall Check", "Require a clear line of sight", false);

    // ---- Firing ----
    public final BooleanSetting autoShoot = new BooleanSetting("Auto Shoot", "Release the bow automatically", true);
    public final BooleanSetting onlyWhenCharged = new BooleanSetting("Only When Charged", "Wait until the bow is fully drawn", true);
    public final NumberSetting chargeThreshold = new NumberSetting("Charge Threshold", "Minimum draw progress before firing", 0.95, 0.1, 1.0, 0.05);
    public final NumberSetting shootDelay = new NumberSetting("Shoot Delay", "Ticks to wait between shots", 4.0, 0.0, 40.0, 1.0);
    public final BooleanSetting onlyWithBow = new BooleanSetting("Only With Bow", "Only run while holding a bow or crossbow", true);

    // ---- Rotation ----
    public final NumberSetting rotationSpeed = new NumberSetting("Rotation Speed", "How fast the aim turns (1 = instant)", 0.65, 0.05, 1.0, 0.05);
    public final BooleanSetting instant = new BooleanSetting("Instant Rotate", "Snap to the target instead of easing", false);
    public final BooleanSetting restore = new BooleanSetting("Restore Rotation", "Return to your old aim when no target is found", false);

    // ---- Render ----
    public final BooleanSetting renderTarget = new BooleanSetting("Render Target", "Draw a box around the current target", true);

    private Entity target;
    private int shootCooldown = 0;
    private float lastYaw, lastPitch;

    public BowAimbot() {
        super("BowAimbot", "Aims and fires your bow with real ballistic trajectory solving", Category.COMBAT);
        addSetting(range);
        addSetting(fov);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(targetAnimals);
        addSetting(targetInvisible);
        addSetting(ignoreFriends);
        addSetting(ignoreBots);
        addSetting(ignoreNaked);
        addSetting(ignoreDead);
        addSetting(priority);
        addSetting(sortMode);
        addSetting(predict);
        addSetting(predictionTicks);
        addSetting(arrowSpeed);
        addSetting(gravity);
        addSetting(highArc);
        addSetting(wallCheck);
        addSetting(autoShoot);
        addSetting(onlyWhenCharged);
        addSetting(chargeThreshold);
        addSetting(shootDelay);
        addSetting(onlyWithBow);
        addSetting(rotationSpeed);
        addSetting(instant);
        addSetting(restore);
        addSetting(renderTarget);
    }

    @Override
    public void onDisable() {
        target = null;
        shootCooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (shootCooldown > 0) shootCooldown--;

        if (onlyWithBow.isEnabled() && !holdingRanged()) {
            target = null;
            return;
        }

        if (mc.player.getMainHandStack().getItem() instanceof CrossbowItem) {
            // Crossbows fire instantly; nothing to charge.
        } else if (!(mc.player.getMainHandStack().getItem() instanceof BowItem)
                && !(mc.player.getOffHandStack().getItem() instanceof BowItem)) {
            if (onlyWithBow.isEnabled()) {
                target = null;
                return;
            }
        }

        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();

        target = findTarget();
        if (target == null) return;

        Vec3d eye = mc.player.getEyePos();
        Vec3d aimPoint = predict(predictedPosition(target), eye);
        if (aimPoint == null) return;

        float[] rot = solveAngles(eye, aimPoint);
        if (rot == null) return;

        applyRotation(rot[0], rot[1]);

        if (autoShoot.isEnabled() && shootCooldown <= 0 && isCharged()) {
            fire();
            shootCooldown = shootDelay.getValue().intValue();
        }
    }

    private boolean holdingRanged() {
        return mc.player.getMainHandStack().getItem() instanceof BowItem
                || mc.player.getMainHandStack().getItem() instanceof CrossbowItem
                || mc.player.getOffHandStack().getItem() instanceof BowItem
                || mc.player.getOffHandStack().getItem() instanceof CrossbowItem;
    }

    private boolean isCharged() {
        ItemStack main = mc.player.getMainHandStack();
        if (main.getItem() instanceof CrossbowItem) return CrossbowItem.isCharged(main);
        if (!(main.getItem() instanceof BowItem) && !(mc.player.getOffHandStack().getItem() instanceof BowItem)) return false;
        float progress = mc.player.getItemUseTime() / 20.0f;
        if (!onlyWhenCharged.isEnabled()) return mc.player.isUsingItem();
        return progress >= chargeThreshold.getValue();
    }

    private void fire() {
        try {
            if (mc.player.isUsingItem()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = {
            net.minecraft.entity.EquipmentSlot.HEAD,
            net.minecraft.entity.EquipmentSlot.CHEST,
            net.minecraft.entity.EquipmentSlot.LEGS,
            net.minecraft.entity.EquipmentSlot.FEET
    };

    /** Position of an entity without relying on version-specific accessors. */
    private static Vec3d posOf(Entity e) {
        return new Vec3d(e.getX(), e.getY(), e.getZ());
    }

    private static boolean hasArmor(LivingEntity e) {
        for (net.minecraft.entity.EquipmentSlot slot : ARMOR_SLOTS) {
            if (!e.getEquippedStack(slot).isEmpty()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Target selection
    // ------------------------------------------------------------------

    private Entity findTarget() {
        Vec3d eye = mc.player.getEyePos();
        double maxDist = range.getValue();
        double maxFov = fov.getValue() / 2.0;

        List<Entity> candidates = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e == mc.player || e.isRemoved()) continue;
            if (ignoreDead.isEnabled() && !living.isAlive()) continue;
            if (e.isInvisible() && !targetInvisible.isEnabled()) continue;

            if (e instanceof PlayerEntity p) {
                if (!targetPlayers.isEnabled()) continue;
                String name = p.getGameProfile().name();
                if (ignoreFriends.isEnabled() && Friends.isFriend(name)) continue;
                if (ignoreBots.isEnabled() && AntiBot.isBot(e)) continue;
                if (ignoreNaked.isEnabled() && !hasArmor(p)) continue;
            } else if (e instanceof HostileEntity) {
                if (!targetMobs.isEnabled()) continue;
            } else if (e instanceof PassiveEntity) {
                if (!targetAnimals.isEnabled()) continue;
            } else {
                continue;
            }

            double dist = eye.distanceTo(posOf(e));
            if (dist > maxDist) continue;

            // FOV cone around the current view direction.
            if (maxFov < 180.0) {
                double angle = angleTo(eye, e.getBoundingBox().getCenter());
                if (angle > maxFov) continue;
            }

            if (wallCheck.isEnabled() && !mc.player.canSee(e)) continue;

            candidates.add(e);
        }

        if (candidates.isEmpty()) return null;

        Comparator<Entity> cmp = switch (priority.getValue()) {
            case "Health" -> Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth());
            case "Angle" -> Comparator.comparingDouble(e -> angleTo(eye, e.getBoundingBox().getCenter()));
            case "Armour" -> Comparator.comparingDouble(e -> -armourValue((LivingEntity) e));
            default -> Comparator.comparingDouble(e -> eye.distanceTo(posOf(e)));
        };

        // A secondary sort lets the user prefer near/far/low-health targets.
        switch (sortMode.getValue()) {
            case "Furthest" -> cmp = Comparator.comparingDouble((Entity e) -> -eye.distanceTo(posOf(e)));
            case "Lowest Health" -> cmp = Comparator.comparingDouble(e -> ((LivingEntity) e).getHealth());
            default -> {
            }
        }

        candidates.sort(cmp);
        return candidates.get(0);
    }

    private static double armourValue(LivingEntity e) {
        double total = 0;
        for (net.minecraft.entity.EquipmentSlot slot : ARMOR_SLOTS) {
            if (!e.getEquippedStack(slot).isEmpty()) total += 1;
        }
        return total;
    }

    private double angleTo(Vec3d eye, Vec3d point) {
        Vec3d dir = point.subtract(eye).normalize();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();
        double dot = MathHelper.clamp(dir.dotProduct(look), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    // ------------------------------------------------------------------
    //  Ballistics
    // ------------------------------------------------------------------

    private Vec3d predictedPosition(Entity e) {
        Vec3d pos = e.getBoundingBox().getCenter();
        if (!predict.isEnabled()) return pos;

        Vec3d vel = e.getVelocity();
        double dist = mc.player.getEyePos().distanceTo(pos);
        double speed = Math.max(0.5, arrowSpeed.getValue());
        double flightTicks = dist / speed + predictionTicks.getValue();
        return pos.add(vel.multiply(flightTicks));
    }

    /** One refinement pass of the flight-time estimate (target keeps moving). */
    private Vec3d predict(Vec3d aimPoint, Vec3d eye) {
        if (!predict.isEnabled()) return aimPoint;
        try {
            Vec3d delta = aimPoint.subtract(eye);
            double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double speed = Math.max(0.5, arrowSpeed.getValue());
            double t = flat / speed;
            return aimPoint.add(target.getVelocity().multiply(Math.max(0, t)));
        } catch (Throwable t) {
            return aimPoint;
        }
    }

    /**
     * Solves for the yaw/pitch that lands the arrow on {@code point}.
     *
     * @return {yaw, pitch} or null when the target is out of reach
     */
    private float[] solveAngles(Vec3d eye, Vec3d point) {
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double d = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        if (d < 0.05) {
            return new float[]{yaw, (float) -Math.toDegrees(Math.atan2(dy, d))};
        }

        double v = Math.max(0.5, arrowSpeed.getValue());
        double g = gravity.getValue();

        if (g <= 0.0001) {
            return new float[]{yaw, (float) -Math.toDegrees(Math.atan2(dy, d))};
        }

        double a = g * d * d / (2.0 * v * v);
        double disc = d * d - 4.0 * a * (dy + a);
        if (disc < 0.0) return null; // out of range for this speed

        double sqrt = Math.sqrt(disc);
        double tanFlat = (d - sqrt) / (2.0 * a);
        double tanHigh = (d + sqrt) / (2.0 * a);
        double tan = highArc.isEnabled() ? tanHigh : tanFlat;

        float pitch = (float) -Math.toDegrees(Math.atan(tan));
        return new float[]{yaw, pitch};
    }

    private void applyRotation(float yaw, float pitch) {
        if (instant.isEnabled()) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
            return;
        }
        float speed = rotationSpeed.getValue().floatValue();
        float newYaw = mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw()) * speed;
        float newPitch = mc.player.getPitch() + (MathHelper.clamp(pitch, -90f, 90f) - mc.player.getPitch()) * speed;
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || !renderTarget.isEnabled() || target == null || mc.player == null) return;
        try {
            var box = target.getBoundingBox();
            var center = box.getCenter();
            int[] sc = com.anormal.client.util.ProjectionUtil.project(center, tickDelta);
            if (sc == null) return;
            int w = (int) Math.max(8, box.getLengthX() * 30);
            int h = (int) Math.max(8, box.getLengthY() * 30);
            RenderUtils.drawBorder(context, sc[0] - w / 2, sc[1] - h / 2, sc[0] + w / 2, sc[1] + h / 2, 1, 0xFFFF5555);
        } catch (Throwable ignored) {
        }
    }
}
