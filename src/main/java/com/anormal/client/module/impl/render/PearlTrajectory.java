package com.anormal.client.module.impl.render;

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
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PearlTrajectory - Ballistic simulation and trajectory visualizer for both own
 * and enemy ender pearls, including in-flight physics, aim prediction, landing boxes,
 * vertical beacons, hit markers, distance/ETA tags, and incoming enemy warning alerts.
 */
public class PearlTrajectory extends Module {
    public final BooleanSetting ownPearls = new BooleanSetting("Own In-Flight", "Render trajectory for your pearls in flight", true);
    public final BooleanSetting ownHoldingAim = new BooleanSetting("Own Aim Assist", "Predict trajectory while holding an ender pearl", true);
    public final BooleanSetting enemyPearls = new BooleanSetting("Enemy In-Flight", "Render trajectory for enemy pearls in flight", true);
    public final BooleanSetting enemyHoldingAim = new BooleanSetting("Enemy Aim Predict", "Predict trajectory when nearby enemy holds an ender pearl", true);
    public final ModeSetting style = new ModeSetting("Style", "Line rendering style", "Line", "Line", "Dotted", "Thick");
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Maximum detection radius", 96.0, 32.0, 256.0, 8.0);
    public final NumberSetting maxSteps = new NumberSetting("Max Steps", "Physics simulation resolution limit", 100.0, 40.0, 200.0, 5.0);
    public final BooleanSetting landingBox = new BooleanSetting("Landing Box", "Draws 3D wireframe box around landing block", true);
    public final BooleanSetting landingBeacon = new BooleanSetting("Landing Beacon", "Draws vertical light pillar at landing spot", true);
    public final BooleanSetting hitMarker = new BooleanSetting("Hit Marker", "Draws 2D target reticle at impact point", true);
    public final BooleanSetting infoTag = new BooleanSetting("Info Tag", "Displays distance, ETA seconds, and thrower name", true);
    public final BooleanSetting warningAlert = new BooleanSetting("Warning Alert", "Audible chime and alert when enemy pearl is thrown", true);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", "Width of rendered trajectory lines", 1.5, 1.0, 4.0, 0.5);

    public final ColorSetting ownColor = new ColorSetting("Own Color", "Trajectory color for own pearls", ColorUtils.rgba(0, 230, 255, 230));
    public final ColorSetting enemyColor = new ColorSetting("Enemy Color", "Trajectory color for enemy pearls", ColorUtils.rgba(255, 60, 60, 230));
    public final ColorSetting landingColor = new ColorSetting("Landing Color", "Color of landing marker and impact box", ColorUtils.rgba(255, 215, 0, 240));

    private final Set<Integer> alertedPearls = new HashSet<>();

    public PearlTrajectory() {
        super("PearlTrajectory", "Visualizes full ballistic trajectories and landing zones for own and enemy pearls", Category.RENDER);
        addSetting(ownPearls);
        addSetting(ownHoldingAim);
        addSetting(enemyPearls);
        addSetting(enemyHoldingAim);
        addSetting(style);
        addSetting(maxDistance);
        addSetting(maxSteps);
        addSetting(landingBox);
        addSetting(landingBeacon);
        addSetting(hitMarker);
        addSetting(infoTag);
        addSetting(warningAlert);
        addSetting(lineWidth);
        addSetting(ownColor);
        addSetting(enemyColor);
        addSetting(landingColor);
    }

    @Override
    public void onDisable() {
        alertedPearls.clear();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        double maxDistSq = maxDistance.getValue() * maxDistance.getValue();

        // 1. In-flight pearls (Both own and enemies)
        try {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof EnderPearlEntity pearl) || !pearl.isAlive()) continue;
                if (mc.player.squaredDistanceTo(pearl) > maxDistSq) continue;

                Entity owner = pearl.getOwner();
                boolean isOwn = owner == mc.player;
                if (isOwn && !ownPearls.isEnabled()) continue;
                if (!isOwn && !enemyPearls.isEnabled()) continue;

                // Incoming warning alert sound for enemy pearls
                if (!isOwn && warningAlert.isEnabled() && !alertedPearls.contains(pearl.getId())) {
                    alertedPearls.add(pearl.getId());
                    try {
                        mc.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.9f);
                    } catch (Throwable ignored) {}
                }

                Vec3d currentPos = ProjectionUtil.lerpPos(pearl, tickDelta);
                Vec3d currentVel = pearl.getVelocity();
                String throwerName = isOwn ? "You" : (owner instanceof PlayerEntity p ? p.getName().getString() : "Enemy");

                simulateAndRender(context, currentPos, currentVel, pearl, owner, isOwn, throwerName, tickDelta);
            }
        } catch (Throwable ignored) {}

        // 2. Own Aim Prediction (when holding ender pearl in main or off-hand)
        if (ownHoldingAim.isEnabled()) {
            boolean holding = mc.player.getMainHandStack().isOf(Items.ENDER_PEARL)
                    || mc.player.getOffHandStack().isOf(Items.ENDER_PEARL);
            if (holding) {
                Vec3d eyePos = mc.player.getEyePos().subtract(0, 0.1, 0);
                Vec3d rot = mc.player.getRotationVec(tickDelta);
                Vec3d initialVel = rot.multiply(1.5).add(mc.player.getVelocity());
                simulateAndRender(context, eyePos, initialVel, null, mc.player, true, "You (Aim)", tickDelta);
            }
        }

        // 3. Enemy Aim Prediction (when nearby enemy player is holding an ender pearl)
        if (enemyHoldingAim.isEnabled()) {
            try {
                for (PlayerEntity player : mc.world.getPlayers()) {
                    if (player == mc.player || !player.isAlive()) continue;
                    if (mc.player.squaredDistanceTo(player) > maxDistSq) continue;

                    boolean enemyHolding = player.getMainHandStack().isOf(Items.ENDER_PEARL)
                            || player.getOffHandStack().isOf(Items.ENDER_PEARL);
                    if (enemyHolding) {
                        Vec3d eyePos = player.getEyePos().subtract(0, 0.1, 0);
                        Vec3d rot = player.getRotationVec(tickDelta);
                        Vec3d initialVel = rot.multiply(1.5).add(player.getVelocity());
                        String name = player.getName().getString() + " (Aim)";
                        simulateAndRender(context, eyePos, initialVel, null, player, false, name, tickDelta);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void simulateAndRender(DrawContext context, Vec3d startPos, Vec3d startVel,
                                   Entity projectile, Entity thrower, boolean isOwn,
                                   String throwerName, float tickDelta) {
        int steps = maxSteps.getValue().intValue();
        Vec3d pos = startPos;
        Vec3d vel = startVel;

        List<Vec3d> points = new ArrayList<>(steps + 1);
        points.add(pos);

        Vec3d impactPos = null;
        int ticksToImpact = 0;

        for (int step = 1; step <= steps; step++) {
            Vec3d nextPos = pos.add(vel);

            // Raycast block collision
            RaycastContext rCtx = new RaycastContext(
                    pos, nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    projectile != null ? projectile : (thrower != null ? thrower : mc.player)
            );
            BlockHitResult bHit = mc.world.raycast(rCtx);
            if (bHit.getType() != HitResult.Type.MISS) {
                impactPos = bHit.getPos();
                points.add(impactPos);
                ticksToImpact = step;
                break;
            }

            // Entity collision check
            Box stepBox = new Box(pos, nextPos).expand(0.3);
            Entity hitEnt = null;
            for (Entity e : mc.world.getEntities()) {
                if (e == projectile || e == thrower || !e.isAlive()) continue;
                if (e.getBoundingBox().expand(0.3).intersects(stepBox)) {
                    hitEnt = e;
                    break;
                }
            }
            if (hitEnt != null) {
                impactPos = nextPos;
                points.add(impactPos);
                ticksToImpact = step;
                break;
            }

            points.add(nextPos);
            pos = nextPos;

            // Minecraft pearl physics update: drag = 0.99, gravity = 0.03
            vel = vel.multiply(0.99);
            vel = vel.add(0, -0.03, 0);
        }

        if (impactPos == null && !points.isEmpty()) {
            impactPos = points.get(points.size() - 1);
            ticksToImpact = points.size();
        }

        int col = isOwn ? ownColor.getValue() : enemyColor.getValue();
        int landCol = landingColor.getValue();
        int width = Math.max(1, lineWidth.getValue().intValue());

        // Project and draw trajectory path
        List<int[]> screenPoints = new ArrayList<>(points.size());
        for (Vec3d p : points) {
            int[] s = ProjectionUtil.project(p, tickDelta);
            if (s != null) {
                if (!screenPoints.isEmpty()) {
                    int[] last = screenPoints.get(screenPoints.size() - 1);
                    if (Math.abs(s[0] - last[0]) + Math.abs(s[1] - last[1]) > 500) {
                        // Off-screen discontinuity, reset chain
                        screenPoints.clear();
                    }
                }
                screenPoints.add(s);
            }
        }

        if (style.is("Dotted")) {
            for (int[] p : screenPoints) {
                context.fill(p[0] - 1, p[1] - 1, p[0] + 2, p[1] + 2, col);
            }
        } else {
            int w = style.is("Thick") ? Math.max(2, width + 1) : width;
            for (int i = 1; i < screenPoints.size(); i++) {
                int[] a = screenPoints.get(i - 1), b = screenPoints.get(i);
                RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], col, w);
            }
        }

        if (impactPos == null) return;

        BlockPos impactBlock = BlockPos.ofFloored(impactPos);

        // 3D Landing Box
        if (landingBox.isEnabled()) {
            RenderUtils.drawBlockOutline(context, impactBlock, landCol, tickDelta, width);
            RenderUtils.drawBlockFill(context, impactBlock, landCol, tickDelta, 45);
        }

        int[] hitScreen = ProjectionUtil.project(impactPos, tickDelta);

        // Vertical Landing Beacon Light Beam
        if (landingBeacon.isEnabled() && hitScreen != null) {
            int[] topScreen = ProjectionUtil.project(impactPos.add(0, 3.5, 0), tickDelta);
            if (topScreen != null) {
                int beaconCol = (landCol & 0x00FFFFFF) | 0x88000000;
                RenderUtils.drawLine(context, hitScreen[0], hitScreen[1], topScreen[0], topScreen[1], beaconCol, 2);
            }
        }

        // Hit Marker (Target crosshair)
        if (hitMarker.isEnabled() && hitScreen != null) {
            int cx = hitScreen[0], cy = hitScreen[1];
            int sz = 4;
            // Crosshair [+]
            context.fill(cx - sz, cy, cx + sz + 1, cy + 1, landCol);
            context.fill(cx, cy - sz, cx + 1, cy + sz + 1, landCol);
            // Outer brackets
            context.fill(cx - sz - 1, cy - sz - 1, cx - sz + 1, cy - sz + 1, landCol);
            context.fill(cx + sz - 1, cy - sz - 1, cx + sz + 1, cy - sz + 1, landCol);
            context.fill(cx - sz - 1, cy + sz - 1, cx - sz + 1, cy + sz + 1, landCol);
            context.fill(cx + sz - 1, cy + sz - 1, cx + sz + 1, cy + sz + 1, landCol);
        }

        // Floating Info Tag (Thrower name, distance, ETA)
        if (infoTag.isEnabled() && mc.textRenderer != null) {
            int[] tagScreen = ProjectionUtil.project(impactPos.add(0, 0.8, 0), tickDelta);
            if (tagScreen != null) {
                double dist = mc.player.getEyePos().distanceTo(impactPos);
                double etaSeconds = ticksToImpact / 20.0;
                String tag = String.format("%s §f%.1fm §e%.1fs",
                        (isOwn ? "§b" : "§c") + throwerName, dist, etaSeconds);

                int textW = mc.textRenderer.getWidth(tag);
                int tx = tagScreen[0] - textW / 2;
                int ty = tagScreen[1] - 12;

                // Pill background
                context.fill(tx - 3, ty - 2, tx + textW + 3, ty + 10, 0xAA000000);
                context.fill(tx - 3, ty + 9, tx + textW + 3, ty + 10, isOwn ? 0xFF00D2FF : 0xFFFF4444);
                context.drawText(mc.textRenderer, tag, tx, ty, 0xFFFFFFFF, true);
            }
        }
    }
}
