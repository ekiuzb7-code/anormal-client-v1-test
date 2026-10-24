package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.Friends;
import com.anormal.client.module.impl.world.TargetFilter;
import com.anormal.client.rotation.Bone;
import com.anormal.client.rotation.Rotation;
import com.anormal.client.rotation.RotationConfig;
import com.anormal.client.rotation.Rotator;
import com.anormal.client.rotation.Smoothing;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.MultiSelectSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Aimbot - rotation-based target tracking with humanised smoothing.
 *
 * <p>This is a separate module from {@link AimAssist} on purpose: the rotation pipeline here is
 * ported from Cold Client (via trident/zaza.hook) and works on {@link Rotation} values with a
 * pluggable {@link Rotator}, which is what makes WindMouse and silent rotations possible.</p>
 *
 * <p><b>Regular</b> mode turns the camera. <b>Silent</b> mode leaves the camera alone and only
 * sends the look packet, so the aim is invisible on your own screen while the server still sees
 * the rotation.</p>
 */
public class Aimbot extends Module {

    // ------------------------------------------------------------------
    //  Aim
    // ------------------------------------------------------------------
    public final ModeSetting mode = new ModeSetting("Mode",
            "Regular turns the camera, Silent only sends the rotation to the server",
            "Regular", "Regular", "Silent");

    public final ModeSetting smoothing = new ModeSetting("Smoothing",
            "How the rotation approaches the target", "WindMouse",
            Smoothing.NAMES);

    public final NumberSetting smoothness = new NumberSetting("Smoothness",
            "0 = snap instantly, 100 = slowest human-like movement (30 is a good combat value)", 30.0, 0.0, 100.0, 1.0);

    public final NumberSetting fov = new NumberSetting("FOV",
            "Only targets inside this cone around your crosshair", 90.0, 10.0, 360.0, 5.0);

    public final NumberSetting range = new NumberSetting("Range",
            "Maximum target distance in blocks", 4.5, 2.0, 8.0, 0.5);

    public final ModeSetting targetMode = new ModeSetting("Target Mode",
            "How the best target is picked", "Closest to FOV",
            "Closest to FOV", "Lowest Health", "Distance");

    public final MultiSelectSetting bones = new MultiSelectSetting("Bones",
            "Aim points used on the target", new String[]{"Head", "Body"},
            Bone.NAMES);

    public final NumberSetting prediction = new NumberSetting("Prediction",
            "Ticks of target movement to lead, for fast targets", 0.0, 0.0, 10.0, 1.0);

    public final BooleanSetting verticalAim = new BooleanSetting("Vertical Aim",
            "Allow the pitch to move (off = horizontal only)", true);

    // ------------------------------------------------------------------
    //  Conditions
    // ------------------------------------------------------------------
    public final BooleanSetting onHold = new BooleanSetting("On Hold",
            "Only aim while the attack key is held", false);

    public final BooleanSetting weaponsOnly = new BooleanSetting("Weapons Only",
            "Only aim while holding a weapon", false);

    public final BooleanSetting lineOfSight = new BooleanSetting("Line Of Sight",
            "Skip targets behind blocks", true);

    public final BooleanSetting targetPlayers = new BooleanSetting("Target Players",
            "Aim at other players", true);

    public final BooleanSetting targetMobs = new BooleanSetting("Target Mobs",
            "Aim at hostile mobs", false);

    public final BooleanSetting targetAnimals = new BooleanSetting("Target Animals",
            "Aim at passive animals", false);

    public final BooleanSetting ignoreInvisible = new BooleanSetting("Ignore Invisible",
            "Skip invisible targets", true);

    public final BooleanSetting ignoreFriends = new BooleanSetting("Ignore Friends",
            "Never aim at players on your friend list", true);

    public final BooleanSetting useTargetFilter = new BooleanSetting("Use Target Filter",
            "Apply the TargetFilter module's team / bot rules", true);

    // ------------------------------------------------------------------
    //  Output
    // ------------------------------------------------------------------
    public final BooleanSetting gcdFix = new BooleanSetting("GCD Fix",
            "Round the rotation to your mouse sensitivity step so it looks like real input", true);

    public final BooleanSetting autoAttack = new BooleanSetting("Auto Attack",
            "Attack the target automatically once the aim is on it", false);

    public final NumberSetting attackCps = new NumberSetting("Attack CPS",
            "Clicks per second used by Auto Attack", 10.0, 2.0, 20.0, 1.0);

    public final BooleanSetting showTarget = new BooleanSetting("Show Target",
            "Draw the current target on the HUD", false);

    // ------------------------------------------------------------------
    //  State
    // ------------------------------------------------------------------
    private Rotator rotator;
    private Smoothing rotatorSmoothing;
    private LivingEntity target;
    private Bone bone;
    private Rotation lastSent;
    private long lastAttack;
    private String status = "";

    public Aimbot() {
        super("Aimbot", "Rotation-based aimbot with humanised smoothing and silent rotations", Category.COMBAT);

        addSetting(mode);
        addSetting(smoothing);
        addSetting(smoothness);
        addSetting(fov);
        addSetting(range);
        addSetting(targetMode);
        addSetting(bones);
        addSetting(prediction);
        addSetting(verticalAim);

        addSetting(onHold);
        addSetting(weaponsOnly);
        addSetting(lineOfSight);
        addSetting(targetPlayers);
        addSetting(targetMobs);
        addSetting(targetAnimals);
        addSetting(ignoreInvisible);
        addSetting(ignoreFriends);
        addSetting(useTargetFilter);

        addSetting(gcdFix);
        addSetting(autoAttack);
        addSetting(attackCps);
        addSetting(showTarget);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        rotator = null;
        rotatorSmoothing = null;
        target = null;
        bone = null;
        lastSent = null;
        lastAttack = 0L;
        status = "";
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }
        if (mc.currentScreen != null) {
            status = "Paused: GUI";
            return;
        }
        if (onHold.isEnabled() && !mc.options.attackKey.isPressed()) {
            target = null;
            status = "Waiting: hold attack";
            return;
        }
        if (weaponsOnly.isEnabled() && !holdingWeapon()) {
            target = null;
            status = "Waiting: weapon";
            return;
        }

        LivingEntity best = findTarget();
        if (best == null) {
            target = null;
            status = "No target";
            return;
        }
        target = best;

        Vec3d aimPoint = aimPoint(best);
        if (aimPoint == null) {
            target = null;
            status = "No aim point";
            return;
        }

        Rotation wanted = Rotation.toward(mc.player.getEyePos(), aimPoint);
        if (!verticalAim.isEnabled()) {
            wanted = new Rotation(wanted.yaw(), mc.player.getPitch());
        }

        Rotation current = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        Rotation next = stepRotator(current, wanted);

        boolean silent = mode.is("Silent");
        apply(next, silent);

        status = "Tracking " + best.getName().getString() + " (" + (bone == null ? "?" : bone.label()) + ")";

        if (autoAttack.isEnabled()) attack(best, next, silent);
    }

    private Rotation stepRotator(Rotation current, Rotation wanted) {
        Smoothing chosen = Smoothing.byName(smoothing.getValue());
        if (rotator == null || rotatorSmoothing != chosen) {
            rotator = chosen.create();
            rotatorSmoothing = chosen;
        }
        RotationConfig config = mode.is("Silent")
                ? RotationConfig.silent(smoothness.getValue().floatValue() / 100.0f, chosen)
                : RotationConfig.visible(smoothness.getValue().floatValue() / 100.0f, chosen);
        return rotator.step(current, wanted, config, 1.0f);
    }

    /** Pushes the rotation out - either to the camera or only to the server. */
    private void apply(Rotation rotation, boolean silent) {
        float yaw = rotation.yaw();
        float pitch = rotation.pitch();

        if (gcdFix.isEnabled()) {
            float gcd = gcd();
            if (gcd > 1.0E-5f) {
                float deltaYaw = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
                float deltaPitch = pitch - mc.player.getPitch();
                yaw = mc.player.getYaw() + Math.round(deltaYaw / gcd) * gcd;
                pitch = MathHelper.clamp(mc.player.getPitch() + Math.round(deltaPitch / gcd) * gcd, -90.0f, 90.0f);
            }
        }

        if (!silent) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            return;
        }

        if (lastSent != null
                && Math.abs(lastSent.yaw() - yaw) < 0.01f
                && Math.abs(lastSent.pitch() - pitch) < 0.01f) {
            return;
        }
        try {
            var handler = mc.getNetworkHandler();
            if (handler == null) return;
            handler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            lastSent = new Rotation(yaw, pitch);
        } catch (Throwable ignored) {
        }
    }

    /**
     * The smallest rotation step the player's mouse sensitivity can produce. Rounding to it makes
     * the movement indistinguishable from real mouse input.
     */
    private float gcd() {
        try {
            double sensitivity = mc.options.getMouseSensitivity().getValue();
            double f = sensitivity * 0.6 + 0.2;
            return (float) (f * f * f * 8.0 * 0.15);
        } catch (Throwable t) {
            return 0.0f;
        }
    }

    // ------------------------------------------------------------------
    //  Target selection
    // ------------------------------------------------------------------

    private LivingEntity findTarget() {
        List<LivingEntity> candidates = new ArrayList<>();
        try {
            for (var entity : mc.world.getEntities()) {
                if (entity instanceof LivingEntity living && living != mc.player) candidates.add(living);
            }
        } catch (Throwable t) {
            return null;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        double maxRange = range.getValue();
        double halfFov = fov.getValue() * 0.5;

        LivingEntity best = null;
        Bone bestBone = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity living : candidates) {
            if (!acceptable(living)) continue;
            if (mc.player.squaredDistanceTo(living) > maxRange * maxRange) continue;
            if (lineOfSight.isEnabled() && !mc.player.canSee(living)) continue;

            Bone bone = bestBone(living, eye, look, halfFov);
            if (bone == null) continue;

            Vec3d point = pointFor(living, bone);
            double score;
            if (targetMode.is("Lowest Health")) {
                score = living.getHealth();
            } else if (targetMode.is("Distance")) {
                score = mc.player.distanceTo(living);
            } else {
                score = Rotation.angle(look, point.subtract(eye));
            }
            if (score >= bestScore) continue;

            best = living;
            bestBone = bone;
            bestScore = score;
        }

        bone = bestBone;
        return best;
    }

    /** The selected bone closest to the crosshair, or {@code null} when outside the cone. */
    private Bone bestBone(LivingEntity living, Vec3d eye, Vec3d look, double halfFov) {
        Bone closest = null;
        double closestAngle = halfFov;

        for (Bone candidate : Bone.ALL) {
            if (!bones.isSelected(candidate.label())) continue;
            Vec3d point = pointFor(living, candidate);
            double angle = Rotation.angle(look, point.subtract(eye));
            if (angle >= closestAngle) continue;
            closest = candidate;
            closestAngle = angle;
        }
        return closest;
    }

    private Vec3d pointFor(LivingEntity living, Bone bone) {
        Vec3d point = bone.point(mc.player, living);
        double ticks = prediction.getValue();
        if (ticks > 0.0) {
            point = point.add(living.getVelocity().multiply(ticks));
        }
        return point;
    }

    private Vec3d aimPoint(LivingEntity living) {
        if (bone == null) return null;
        return pointFor(living, bone);
    }

    private boolean acceptable(LivingEntity living) {
        if (!living.isAlive() || living.isRemoved()) return false;
        if (living.isSpectator()) return false;
        if (ignoreInvisible.isEnabled() && living.isInvisible()) return false;

        if (living instanceof PlayerEntity player) {
            if (!targetPlayers.isEnabled()) return false;
            if (ignoreFriends.isEnabled() && Friends.isFriend(player.getGameProfile().name())) return false;
        } else if (living instanceof HostileEntity) {
            if (!targetMobs.isEnabled()) return false;
        } else if (living instanceof PassiveEntity) {
            if (!targetAnimals.isEnabled()) return false;
        } else {
            return false;
        }

        return !useTargetFilter.isEnabled() || TargetFilter.isValid(living);
    }

    /**
     * Weapon test by registry id rather than by item class: 1.21.11 dropped the dedicated
     * SwordItem class, and the id check also covers modded weapons that follow the vanilla naming.
     */
    private boolean holdingWeapon() {
        try {
            var stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) return false;
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            return path.endsWith("_sword") || path.endsWith("_axe")
                    || path.equals("mace") || path.equals("trident");
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Attack
    // ------------------------------------------------------------------

    private void attack(LivingEntity living, Rotation rotation, boolean silent) {
        long now = System.currentTimeMillis();
        long interval = (long) (1000.0 / Math.max(1.0, attackCps.getValue()));
        if (now - lastAttack < interval) return;

        try {
            if (mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

            // The server validates the hit against the rotation it knows, so in silent mode the
            // look packet has to go out before the attack packet.
            if (silent) {
                var handler = mc.getNetworkHandler();
                if (handler != null) {
                    handler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                            rotation.yaw(), rotation.pitch(), mc.player.isOnGround(),
                            mc.player.horizontalCollision));
                    lastSent = new Rotation(rotation.yaw(), rotation.pitch());
                }
            }

            mc.interactionManager.attackEntity(mc.player, living);
            mc.player.swingHand(Hand.MAIN_HAND);
            lastAttack = now;
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    //  HUD
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showTarget.isEnabled() || mc.textRenderer == null || mc.getWindow() == null) return;
        if (status.isEmpty()) return;
        try {
            String line = "§6Aimbot §7| §f" + status;
            context.drawText(mc.textRenderer, line, 6, mc.getWindow().getScaledHeight() - 64, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {
        }
    }
}
