package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * AimAssist — a gentle, human-like rotation helper.
 *
 * IMPORTANT: This is an ASSIST, not an aimbot. It does NOT snap to targets.
 * Each frame it moves only a tiny fraction of the angular gap toward the
 * target (controlled by Smoothness), so the crosshair GLIDES — it takes
 * roughly 1–2 seconds to settle on a target at default settings. Combined
 * with a dead-zone and mouse-yield near the target, the player's own aim
 * always stays in control.
 *
 * ── Timing model ───────────────────────────────────────────────
 *   onTick()   (20Hz)  → Target acquisition:
 *                         distance filter, vector FOV cone, raycast
 *                         line-of-sight, target selection by smallest
 *                         angle to the crosshair, desired yaw/pitch.
 *   onRender() (60fps) → Tiny frame-rate-independent lerp toward the
 *                         desired rotation, with dead-zone, mouse-yield,
 *                         per-axis caps, and humanization noise.
 *
 * Rotation is applied directly to player.setYaw / player.setPitch on the
 * render frame (Method A: direct memory modification). We deliberately
 * do NOT touch lastYaw/lastPitch — the renderer interpolates from the
 * previous tick automatically, so the motion stays fluid.
 *
 * Target filter: LivingEntity only (no dropped items, no vehicles, no XP
 * orbs). This matches the intent — you want to aim at combat-relevant
 * entities, not item frames.
 */
public class AimAssist extends Module {

    // ── Settings ──────────────────────────────────────────────
    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Maximum distance to consider targets (blocks).", 3.0f, 8.0f, 4.5f, 1));
    private final FloatSetting fov = addSetting(
        new FloatSetting("FOV", "Only assist when the target is within this cone around the crosshair (°).", 10f, 180f, 90f, 0));
    private final FloatSetting horizontalSpeed = addSetting(
        new FloatSetting("Horizontal Speed", "Hard cap on yaw movement per frame (°). Safety net, not primary speed.", 0.5f, 15f, 3.5f, 1));
    private final FloatSetting verticalSpeed = addSetting(
        new FloatSetting("Vertical Speed", "Hard cap on pitch movement per frame (°). Safety net, not primary speed.", 0.5f, 10f, 2f, 1));
    private final FloatSetting smoothness = addSetting(
        new FloatSetting("Smoothness", "How gradual the correction is. Higher = slower & smoother. 0.8 = gentle drift.", 0f, 1f, 0.8f, 2));
    private final FloatSetting noise = addSetting(
        new FloatSetting("Noise", "Subtle random jitter to mimic human micro-movements (°).", 0f, 2f, 0.25f, 2));
    private final FloatSetting mouseYield = addSetting(
        new FloatSetting("Mouse Yield", "Reduces assist strength when already close to the target (°).", 0f, 10f, 3f, 1));
    private final EnumSetting aimPoint = addSetting(
        new EnumSetting("Aim Point", "Where on the target body to aim.",
            new String[]{"Head", "Chest", "Centre"}, "Chest"));
    private final BooleanSetting visibilityCheck = addSetting(
        new BooleanSetting("Visibility Check", "Ignore targets behind blocks (line-of-sight raycast).", true));
    private final BooleanSetting weaponOnly = addSetting(
        new BooleanSetting("Weapon Only", "Only assist while holding a sword or axe.", true));
    private final BooleanSetting clickOnly = addSetting(
        new BooleanSetting("Click Only", "Only assist while the left mouse button (attack) is held.", true));
    private final BooleanSetting playersOnly = addSetting(
        new BooleanSetting("Players Only", "Only target other players (ignore mobs).", true));
    private final BooleanSetting ignoreInvisible = addSetting(
        new BooleanSetting("Ignore Invisible", "Skip invisible targets.", true));

    private final Random rng = new Random();

    // ── Per-tick target state (written by onTick, read by onRender) ──
    private volatile Entity target = null;
    private volatile float desiredYaw = 0f;
    private volatile float desiredPitch = 0f;
    private volatile boolean hasTarget = false;

    // Frame-timing for frame-rate-independent smoothing.
    private long lastRenderMs = 0L;

    public AimAssist() {
        super("AimAssist", "Gently nudges your crosshair toward nearby targets while you aim.", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        LucidClient.LOGGER.info("AimAssist enabled");
        clearTarget();
    }

    @Override
    protected void onDisable() {
        LucidClient.LOGGER.info("AimAssist disabled");
        clearTarget();
    }

    private void clearTarget() {
        target = null;
        hasTarget = false;
    }

    // ════════════════════════════════════════════════════════════════
    //  STAGE 1 — TARGET ACQUISITION  (runs on tick, 20Hz)
    //  Distance filter · vector FOV cone · raycast · selection
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        // Clear stale target each tick; will be re-acquired or left null.
        clearTarget();

        if (client.player == null || client.world == null) return;
        if (!passesActivation(client, client.player)) return;

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        // (1) Distance filter — gather candidates within range box.
        //     Filter to LivingEntity only (no dropped items, no XP orbs).
        double maxRange = range.get();
        Box searchBox = new Box(
            player.getX() - maxRange, player.getY() - maxRange, player.getZ() - maxRange,
            player.getX() + maxRange, player.getY() + maxRange, player.getZ() + maxRange);

        List<Entity> candidates = world.getOtherEntities(player, searchBox, e -> {
            if (e == player) return false;
            if (!(e instanceof LivingEntity)) return false;
            if (!e.isAlive()) return false;
            if (e.isRemoved()) return false;
            if (playersOnly.isEnabled() && !(e instanceof PlayerEntity)) return false;
            if (ignoreInvisible.isEnabled() && e.isInvisible()) return false;
            return e.squaredDistanceTo(player) <= maxRange * maxRange;
        });
        if (candidates.isEmpty()) return;

        // Player eye + look vector (unit) for FOV cone check.
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f); // current-tick look direction (unit)

        // (1b + 2) FOV cone + selection — pick the candidate requiring the
        // least rotation (smallest angle to the crosshair). This is the most
        // natural-feeling priority for aim assist.
        Entity best = null;
        double bestAngle = Double.MAX_VALUE;
        Vec3d bestAim = null;
        double fovRad = Math.toRadians(fov.get());

        for (Entity e : candidates) {
            Vec3d aim = computeAimPoint(e);
            Vec3d dir = aim.subtract(eye);
            double dist = dir.length();
            if (dist < 1e-4) continue;
            Vec3d dirN = dir.multiply(1.0 / dist);

            // Angle between the look vector and the direction to target.
            // Clamp dot to [-1, 1] to avoid NaN from float error.
            double dot = MathHelper.clamp(look.dotProduct(dirN), -1.0, 1.0);
            double angle = Math.acos(dot);
            if (angle > fovRad) continue; // outside FOV cone

            if (angle < bestAngle) {
                bestAngle = angle;
                best = e;
                bestAim = aim;
            }
        }
        if (best == null || bestAim == null) return;

        // (1c) Line-of-sight raycast — ignore targets behind walls.
        if (visibilityCheck.isEnabled() && !hasLineOfSight(world, eye, bestAim, best)) {
            return;
        }

        // (3) Compute desired yaw/pitch to point at the chosen aim point.
        float[] desired = computeDesiredRotation(eye, bestAim);
        desiredYaw = desired[0];
        desiredPitch = desired[1];
        target = best;
        hasTarget = true;
    }

    // ════════════════════════════════════════════════════════════════
    //  STAGE 2 — SMOOTH ROTATION APPLICATION  (runs on render, 60fps)
    //  Lerp toward desired with per-axis speed caps + humanization.
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onRender(MinecraftClient client, float tickDelta) {
        if (!hasTarget || target == null) return;
        if (client.player == null) return;
        // Target may have despawned/died between tick and render.
        if (!target.isAlive() || target.isRemoved()) { clearTarget(); return; }
        // Re-check activation (player may have opened inventory / dropped weapon).
        if (!passesActivation(client, client.player)) return;

        ClientPlayerEntity player = client.player;

        // Read the LIVE yaw/pitch (which already includes the player's own
        // mouse movement for this frame) so aim assist layers on top of
        // manual aiming rather than fighting it.
        float curYaw = player.getYaw();
        float curPitch = player.getPitch();

        // (4) Rotation difference (wrapped to [-180, 180] for yaw).
        float yawDiff = MathHelper.wrapDegrees(desiredYaw - curYaw);
        float pitchDiff = MathHelper.wrapDegrees(desiredPitch - curPitch);
        double totalDiff = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        // Re-verify FOV on render (the player may have rotated away).
        if (totalDiff > fov.get()) return;

        // ── DEAD ZONE ────────────────────────────────────────────
        // Once we're basically on target, stop micro-adjusting.
        // This prevents the jittery "stuck on target" feel and lets
        // the player's own mouse movement dominate when close.
        if (totalDiff < 0.35) return;

        // ── FRAME-RATE-INDEPENDENT SMOOTHING ─────────────────────
        // Map smoothness [0..1] → base alpha per 60fps-frame:
        //   smoothness 0   (fast)  → 0.12  (closes 30°→1° in ~0.3s @60fps)
        //   smoothness 0.8 (default)→ 0.024 (closes 30°→1° in ~1.6s @60fps)
        //   smoothness 1   (slow)  → 0.006 (closes 30°→1° in ~6s   @60fps)
        // This keeps the assist GENTLE by design — it nudges, never locks.
        float baseAlpha = 0.12f - smoothness.get() * 0.114f;
        baseAlpha = Math.max(0.006f, baseAlpha);

        // Convert per-60fps-frame alpha to a frame-rate-independent alpha
        // using the real elapsed time, so 144Hz players don't lock faster
        // than 60Hz players.   alpha_dt = 1 − (1 − baseAlpha)^(dt / 16.67)
        long now = System.currentTimeMillis();
        long dtMs = lastRenderMs == 0 ? 16 : Math.min(50, now - lastRenderMs);
        lastRenderMs = now;
        float dtScale = dtMs / 16.667f;
        float alpha = 1f - (float) Math.pow(1f - baseAlpha, dtScale);

        // (7) Humanization — mouse yield: reduce strength near the target.
        // As we approach the aim point, the assist progressively lets go,
        // letting the player's own aim take over. This is the key trait
        // that separates an assist from a hard lock.
        float yieldThreshold = mouseYield.get();
        if (yieldThreshold > 0f && totalDiff < yieldThreshold) {
            alpha *= (float) (totalDiff / yieldThreshold);
        }

        // (5) Apply the lerp — this is a SMALL fraction of the gap each
        //     frame, so the crosshair GLIDES toward the target, never snaps.
        float stepYaw = yawDiff * alpha;
        float stepPitch = pitchDiff * alpha;

        // Per-axis hard caps — safety net only, not the primary speed
        // control. Keeps a single frame from ever jumping more than a
        // few degrees, even if alpha somehow spiked.
        float maxYawStep = horizontalSpeed.get();
        float maxPitchStep = verticalSpeed.get();
        stepYaw = MathHelper.clamp(stepYaw, -maxYawStep, maxYawStep);
        stepPitch = MathHelper.clamp(stepPitch, -maxPitchStep, maxPitchStep);

        // (7b) Humanization — subtle noise jitter (less on pitch than yaw).
        float n = noise.get();
        if (n > 0f) {
            stepYaw += (rng.nextFloat() * 2f - 1f) * n * alpha;       // scale noise by alpha so it's tiny when gentle
            stepPitch += (rng.nextFloat() * 2f - 1f) * n * 0.5f * alpha;
        }

        // (6) Apply rotation directly to the player's view variables
        //     (Method A: direct memory modification). We do NOT touch
        //     lastYaw/lastPitch — the renderer interpolates from the
        //     previous tick automatically, keeping motion fluid.
        float newYaw = curYaw + stepYaw;
        float newPitch = MathHelper.clamp(curPitch + stepPitch, -90f, 90f);
        player.setYaw(newYaw);
        player.setPitch(newPitch);
        // Note: deliberately NOT setting lastYaw/lastPitch here.
    }

    // ════════════════════════════════════════════════════════════════
    //  Activation gating
    // ════════════════════════════════════════════════════════════════
    private boolean passesActivation(MinecraftClient client, ClientPlayerEntity player) {
        // Must be in-game (no screen open).
        if (client.currentScreen != null) return false;

        // Weapon only — sword or axe in main hand.
        if (weaponOnly.isEnabled()) {
            ItemStack main = player.getMainHandStack();
            boolean isWeapon = main.isIn(ItemTags.SWORDS) || main.isIn(ItemTags.AXES);
            if (!isWeapon) return false;
        }

        // Click only — left mouse (attack) held.
        if (clickOnly.isEnabled()) {
            if (client.options != null && client.options.attackKey != null) {
                if (!client.options.attackKey.isPressed()) return false;
            }
        }
        return true;
    }

    // ════════════════════════════════════════════════════════════════
    //  Aim point — head / chest / centre of the target's body box
    // ════════════════════════════════════════════════════════════════
    private Vec3d computeAimPoint(Entity target) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        float h = target.getHeight();
        double aimY;
        switch (aimPoint.get()) {
            case "Head": aimY = y + h - 0.18; break;     // near top of head
            case "Centre": aimY = y + h * 0.5; break;    // bounding-box centre
            case "Chest":
            default: aimY = y + h * 0.62; break;         // upper chest mass
        }
        return new Vec3d(x, aimY, z);
    }

    // ════════════════════════════════════════════════════════════════
    //  Desired yaw/pitch from eye → point (atan2 math)
    //    Δx, Δy, Δz = T − P
    //    Yaw   = atan2(Δz, Δx) × 180/π − 90°
    //    d     = sqrt(Δx² + Δz²)
    //    Pitch = −atan2(Δy, d) × 180/π
    // ════════════════════════════════════════════════════════════════
    private float[] computeDesiredRotation(Vec3d eye, Vec3d point) {
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));
        return new float[]{ yaw, pitch };
    }

    // ════════════════════════════════════════════════════════════════
    //  Line-of-sight raycast — true if no solid block is in the way
    // ════════════════════════════════════════════════════════════════
    private boolean hasLineOfSight(ClientWorld world, Vec3d from, Vec3d to, Entity ignored) {
        RaycastContext ctx = new RaycastContext(
            from, to,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            ignored);
        BlockHitResult hit = world.raycast(ctx);
        if (hit == null) return true;
        if (hit.getType() == HitResult.Type.MISS) return true; // no block hit
        double distToBlock = from.squaredDistanceTo(hit.getPos());
        double distToTarget = from.squaredDistanceTo(to);
        // Allow a small epsilon so targets right against glass/walls still count.
        return distToBlock >= distToTarget - 0.5;
    }
}
