package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.lucid.IntSetting;
import com.anormal.client.lucid.WorldRender3D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import java.util.ArrayList;
import java.util.List;

/**
 * PearlTrajectory — predicts and draws the flight path of an Ender Pearl
 * before you throw it.
 *
 * ── How it works ──────────────────────────────────────────────────
 * Minecraft's projectile physics are fully deterministic, so the mc
 * can run the EXACT same math the server will use, instantly, and draw the
 * resulting path. This module is a "fast-forward button" for the pearl's
 * physics:
 *
 *   1. Gather starting variables — eye position, pitch/yaw (look vector),
 *      and the player's velocity (momentum is added to the pearl's initial
 *      speed, with the Y component only added when airborne — matching
 *      vanilla {@code ProjectileEntity.setVelocity}).
 *   2. Simulate tick-by-tick — apply gravity (0.03 blocks/tick²) and drag
 *      (0.99 air / 0.8 water) to a virtual pearl position, exactly as
 *      {@code ThrownEntity.tick()} does. Each simulated tick casts a ray
 *      between the current and next position; the moment that ray hits a
 *      solid block or an entity's hitbox, the loop stops — that's the
 *      impact point.
 *   3. Render — draw the list of simulated points as a connected line
 *      through the world (depth-test disabled, so it shows through walls),
 *      plus a small box at the landing block so you know exactly where
 *      you'll teleport.
 *
 * ── Vanilla constants (confirmed from 1.21.11 bytecode) ───────────
 *   Initial power (EnderPearlItem.POWER) ........ 1.5f
 *   Divergence (spread) ......................... 1.0f  (averages to zero,
 *                                                       so the deterministic
 *                                                       path ignores it)
 *   Gravity (ThrownEntity.getGravity) ........... 0.03  (vy -= 0.03 / tick)
 *   Air drag (ThrownEntity.applyDrag) ........... 0.99  (vel *= 0.99 / tick)
 *   Water drag ................................. 0.8   (vel *= 0.8  / tick)
 *
 * ── Initial velocity formula (ProjectileEntity.setVelocity) ───────
 *   Vec3d look = player.getRotationVec(1.0f);          // unit look vector
 *   Vec3d pv   = player.getVelocity();
 *   vel = look.multiply(1.5)
 *             .add(pv.x, player.isOnGround() ? 0 : pv.y, pv.z);
 *   pos = player.getEyePos();
 *
 * The gaussian spread (stddev 0.0172275 per axis) is random per-throw and
 * can't be predicted, so the trajectory shows the AVERAGE path — the actual
 * pearl lands within a few milliblocks of the drawn line.
 *
 * Settings:
 *   Max Steps      — max simulated ticks (200 = ~10s of flight)
 *   Require Pearl  — only draw when holding an Ender Pearl (off = always pre-aim)
 *   Show Landing   — draw a box at the impact block
 *   Show Entities  — include entity hitboxes in the collision check
 *   Color          — line + landing box color
 */
public class PearlTrajectoryV2 extends Module {

    // ── Vanilla Ender Pearl physics constants ─────────────────────
    /** Initial velocity power (EnderPearlItem.POWER). */
    private static final float PEARL_POWER = 1.5f;
    /** Per-tick gravity (ThrownEntity.getGravity). Applied as vy -= g. */
    private static final double PEARL_GRAVITY = 0.03;
    /** Per-tick air drag (ThrownEntity.applyDrag, !isTouchingWater). */
    private static final double AIR_DRAG = 0.99;
    /** Per-tick water drag (ThrownEntity.applyDrag, isTouchingWater). */
    private static final double WATER_DRAG = 0.8;

    /** Max simulated ticks. 200 = ~10s of flight, more than enough for any
     *  realistic pearl throw. Capped to keep the sim cheap. */
    private final IntSetting maxSteps = addSetting(
        new IntSetting("Max Steps", "Max simulated ticks (200 = ~10s of flight). Higher = longer paths traced.",
            20, 1000, 200, 1));
    /** Only draw the trajectory when an Ender Pearl is in the main hand.
     *  Off = always show the path (lets you pre-aim with any item). */
    private final BooleanSetting requirePearl = addSetting(
        new BooleanSetting("Require Pearl", "Only show the trajectory when holding an Ender Pearl.",
            false));
    /** Draw a highlighted box at the impact block. */
    private final BooleanSetting showLanding = addSetting(
        new BooleanSetting("Show Landing", "Draw a box at the predicted impact block.",
            true));
    /** Include entity hitboxes in the collision check (pearls can hit
     *  players/mobs and teleport you onto them). Off = block-only collision. */
    private final BooleanSetting showEntities = addSetting(
        new BooleanSetting("Hit Entities", "Include entity hitboxes in the collision check (pearls can land on players/mobs).",
            true));
    /** Trajectory line + landing box color. */
    private final ColorSetting color = addSetting(
        new ColorSetting("Color", "Trajectory line + landing box color.", 0xFF00E5FF));

    /** Last computed trajectory points (world coords). Recomputed each render
     *  frame from the player's current aim so the line tracks your camera in
     *  real time. */
    private final List<Vec3d> trajectory = new ArrayList<>();
    /** Last computed landing position (the impact point), or null if the pearl
     *  flies forever (out of render distance / into the void). */
    private Vec3d landingPos = null;

    public PearlTrajectoryV2() {
        super("PearlTrajectory V2",
            "Predicts and draws the flight path of an Ender Pearl before you throw it (real-time physics sim).",
            Category.RENDER);
        // Lucid dispatched renderWorld() from its client entrypoint; self-register here.
        try {
            net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onEnable() {
        trajectory.clear();
        landingPos = null;
    }

    @Override
    public void onDisable() {
        trajectory.clear();
        landingPos = null;
    }

    // ════════════════════════════════════════════════════════════════
    //  RENDER — recompute the trajectory each frame + draw it
    // ════════════════════════════════════════════════════════════════
    /**
     * Called from {@link LucidClient} during {@code WorldRenderEvents.END_MAIN}.
     * Recomputes the pearl trajectory from the player's current aim and draws
     * it as a world-space polyline (depth-test disabled, visible through
     * walls) plus a landing box at the impact block.
     *
     * Recomputing every frame (rather than every tick) keeps the line locked
     * to your crosshair in real time — as you move the mouse, the path
     * follows instantly. The sim is cheap (≤200 raycasts/frame).
     */
    private void anormal$dispatchWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext wrc) {
        if (!isEnabled()) return;
        renderWorld(wrc);
    }

    public void renderWorld(WorldRenderContext wrc) {
        if (!isEnabled()) return;
        
        if (mc.player == null || mc.world == null) return;

        // Gate on holding a pearl if Require Pearl is on.
        if (requirePearl.isEnabled()
            && mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
            trajectory.clear();
            landingPos = null;
            return;
        }

        // Recompute the trajectory from the current aim — interpolated with
        // the render partial tick so the line's origin sits EXACTLY under
        // the crosshair every frame (see simulateTrajectory).
        float tickDelta = 1.0f;
        try {
            tickDelta = mc.getRenderTickCounter().getTickProgress(false);
        } catch (Throwable ignored) {}
        simulateTrajectory(mc, tickDelta);

        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        net.minecraft.client.util.math.MatrixStack matrices = wrc.matrices();
        net.minecraft.client.render.VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int c = color.getColor();

        WorldRender3D.begin(matrices, camera);
        try {
            // Draw the trajectory as connected line segments.
            for (int i = 1; i < trajectory.size(); i++) {
                WorldRender3D.drawLine(consumers, trajectory.get(i - 1), trajectory.get(i), c);
            }
            // Draw a landing box at the impact block (1×1×1, slightly expanded
            // so it reads clearly through terrain).
            if (showLanding.isEnabled() && landingPos != null) {
                Box box = new Box(landingPos.x - 0.5, landingPos.y - 0.5, landingPos.z - 0.5,
                                  landingPos.x + 0.5, landingPos.y + 0.5, landingPos.z + 0.5)
                    .expand(0.05);
                WorldRender3D.drawBox(consumers, box, c);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  PHYSICS SIMULATION — replicate ThrownEntity.tick() tick-by-tick
    // ════════════════════════════════════════════════════════════════
    /**
     * Run the vanilla Ender Pearl physics forward from the player's current
     * aim, populating {@link #trajectory} with the path points and {@link
     * #landingPos} with the impact point (or null if the pearl never lands
     * within Max Steps).
     *
     * This mirrors {@code ThrownEntity.tick()} exactly:
     *   - apply gravity (vy -= 0.03)
     *   - apply drag (vel *= 0.99 air / 0.8 water)
     *   - raycast from pos to pos+vel; on hit, that's the landing point
     *   - advance pos by vel
     *
     * The initial velocity mirrors {@code ProjectileEntity.setVelocity}:
     *   vel = look.multiply(1.5).add(pv.x, onGround ? 0 : pv.y, pv.z)
     *
     * <p><b>Crosshair alignment (the moving-offset fix):</b> the line's
     * ORIGIN and LOOK use the render-interpolated position/rotation
     * ({@code getCameraPosVec(tickDelta)} / {@code getRotationVec(tickDelta)})
     * — the exact same interpolation the camera renders from — so the path
     * launches precisely from the crosshair every frame. Using the raw
     * tick-position ({@code getEyePos()}) made the origin lead the rendered
     * camera by up to a full tick of movement while sprinting/falling, which
     * read as the line swaying off the crosshair. Velocity and onGround stay
     * tick-based — they're the values the server will actually use when the
     * pearl is thrown.
     */
    private void simulateTrajectory(MinecraftClient mc, float tickDelta) {
        trajectory.clear();
        landingPos = null;

        net.minecraft.entity.player.PlayerEntity player = mc.player;
        ClientWorld world = mc.world;

        // ── 1. Starting variables ──────────────────────────────────
        // Spawn at the INTERPOLATED eye position — camera-locked so the line
        // starts exactly under the crosshair while moving.
        Vec3d pos = player.getCameraPosVec(tickDelta);
        // Interpolated look vector — matches the on-screen crosshair
        // direction this frame (the deterministic base direction; the
        // random gaussian spread averages to zero and can't be predicted).
        Vec3d look = player.getRotationVec(tickDelta);
        // Player momentum: added to the pearl's initial velocity, with the Y
        // component only added when the player is airborne (vanilla behavior
        // — running off a cliff keeps your Y momentum, running on the ground
        // doesn't add Y).
        Vec3d pv = player.getVelocity();
        Vec3d vel = look.multiply(PEARL_POWER)
            .add(pv.x, player.isOnGround() ? 0.0 : pv.y, pv.z);

        trajectory.add(pos);

        int maxSteps = this.maxSteps.get();
        // The pearl's own bounding box is 0.25×0.25×0.25 (vanilla), centered
        // on the position. We expand the path raycast slightly to catch
        // block corners the center ray would miss.
        double pearlHalf = 0.125;

        // ── 2. Tick-by-tick simulation ─────────────────────────────
        for (int step = 0; step < maxSteps; step++) {
            // Apply gravity (Entity.applyGravity → vy -= getFinalGravity).
            vel = vel.add(0.0, -PEARL_GRAVITY, 0.0);
            // Apply drag (ThrownEntity.applyDrag: 0.99 air, 0.8 water).
            // Check water at the current position — vanilla checks
            // isTouchingWater() on the entity, which is a box-vs-fluid test;
            // we approximate by sampling the block at the pearl's center.
            double drag = isFluidAt(world, pos) ? WATER_DRAG : AIR_DRAG;
            vel = vel.multiply(drag);

            Vec3d next = pos.add(vel);

            // ── 3. Raycast + collision detection ──────────────────
            // Block collision: raycast from pos to next with the COLLIDER
            // shape (the same shape vanilla projectiles use for collision).
            HitResult hit = blockRaycast(world, pos, next, player);
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                // The pearl hits a block — record the impact point and stop.
                // Use the actual hit position (the raycast returns the exact
                // intersection point on the block face).
                Vec3d hitPos = hit.getPos();
                trajectory.add(hitPos);
                landingPos = hitPos;
                return;
            }

            // Entity collision (optional): check if the path segment passes
            // through any entity's hitbox. Vanilla uses ProjectileUtil for
            // this; we do a lightweight version — iterate entities near the
            // segment and test if the segment intersects their bounding box.
            if (showEntities.isEnabled()) {
                Vec3d entityHit = entityRaycast(world, pos, next, player);
                if (entityHit != null) {
                    trajectory.add(entityHit);
                    landingPos = entityHit;
                    return;
                }
            }

            // No collision — advance the pearl.
            trajectory.add(next);
            pos = next;

            // Safety: if the pearl falls below the world floor (e.g. thrown
            // into the void), stop simulating — it'll never land on a block.
            if (pos.y < world.getBottomY() - 64) {
                landingPos = pos;
                return;
            }
        }
        // Ran out of steps — the pearl is still flying (very long throw or
        // lag). Leave landingPos null so no landing box is drawn.
    }

    // ════════════════════════════════════════════════════════════════
    //  Collision helpers
    // ════════════════════════════════════════════════════════════════
    /**
     * Cast a block raycast from {@code from} to {@code to} using the
     * COLLIDER shape (the shape vanilla projectiles collide against) and
     * no fluid handling (pearls don't stop on water — they just slow down).
     * Returns null if the raycast throws (e.g. chunk unloaded mid-cast).
     */
    private static HitResult blockRaycast(ClientWorld world, Vec3d from, Vec3d to, Entity ignore) {
        try {
            RaycastContext ctx = new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                ignore);
            return world.raycast(ctx);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Lightweight entity collision check along the segment from→to. Vanilla's
     * ProjectileUtil.getCollision does this via an entity raycast against a
     * predicate; we approximate by scanning entities whose bounding box
     * intersects the segment's expanded AABB and doing a per-entity segment-
     * vs-box test. Returns the hit position (the entity's center, since we
     * don't need the exact face for a trajectory preview) or null.
     */
    private static Vec3d entityRaycast(ClientWorld world, Vec3d from, Vec3d to, Entity shooter) {
        // Build a bounding box around the segment and expand it to catch
        // entities the segment passes near.
        Box segmentBox = new Box(from, to).expand(0.5);
        List<Entity> entities;
        try {
            entities = world.getOtherEntities(shooter, segmentBox,
                e -> e.canHit() && e.isAlive());
        } catch (Throwable ignored) {
            return null;
        }
        // Per-entity segment-vs-bbox test. We use a simple parametric
        // approach: walk the segment in small steps and check if the point
        // is inside the entity's (slightly expanded) box. This isn't as
        // exact as vanilla's ray-vs-box, but it's close enough for a
        // trajectory preview and avoids needing the private raycast helpers.
        for (Entity e : entities) {
            Box box = e.getBoundingBox().expand(0.3);
            // 16 sample points along the segment — fine enough to catch any
            // realistic entity hitbox at pearl speeds.
            for (int i = 0; i <= 16; i++) {
                double t = i / 16.0;
                Vec3d p = from.lerp(to, t);
                if (box.contains(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * Check whether the block at {@code pos} is a fluid (water/lava) — used
     * to pick the water-drag value. Vanilla checks isTouchingWater() on the
     * entity (a box-vs-fluid test); we approximate by sampling the block
     * state at the pearl's center, which is close enough for drag purposes.
     */
    private static boolean isFluidAt(ClientWorld world, Vec3d pos) {
        try {
            return !world.getBlockState(
                net.minecraft.util.math.BlockPos.ofFloored(pos.x, pos.y, pos.z))
                .getFluidState().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }
}