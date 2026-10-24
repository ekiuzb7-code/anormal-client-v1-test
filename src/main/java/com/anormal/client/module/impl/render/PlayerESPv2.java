package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.PlayerColorizer;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PlayerESP v2 — Advanced player ESP featuring:
 * <ul>
 *   <li><b>Skeleton:</b> mathematically locked 3D projected player skeleton (spine, shoulders, arms, hips, legs, head)
 *       that never leaves the player's bounding box during high-speed sprinting, elytra flying, swimming, or riding.</li>
 *   <li><b>Solid:</b> full solid / translucent player body tint with wall-penetration glow.</li>
 *   <li><b>Box 3D:</b> smooth interpolated 3D bounding box with translucent face fills.</li>
 *   <li><b>Box 2D:</b> corner-bracket 2D bounding box with health bar and labels.</li>
 * </ul>
 */
public class PlayerESPv2 extends Module {

    public final ModeSetting mode = new ModeSetting("Mode", "Body rendering style",
            "Skeleton", "Skeleton", "Solid", "Box 3D", "Box 2D");

    public final NumberSetting maxDistance = new NumberSetting("Max Distance",
            "Maximum render range in blocks", 64.0, 10.0, 160.0, 5.0);

    public final NumberSetting lineWidth = new NumberSetting("Line Width",
            "Skeleton and box line thickness", 1.5, 0.5, 3.5, 0.5);

    // --- Colors ---
    public final ColorSetting mainColor = new ColorSetting("Color",
            "Primary skeleton / box color", ColorUtils.rgba(255, 120, 0, 255));

    public final ColorSetting wallColor = new ColorSetting("Wall Color",
            "Color when player is behind blocks", ColorUtils.rgba(255, 60, 60, 240));

    public final BooleanSetting useWallColor = new BooleanSetting("Behind Wall Color",
            "Use distinct color when obscured", true);

    public final BooleanSetting rainbow = new BooleanSetting("Rainbow",
            "Cycle color hue continuously", false);

    public final NumberSetting rainbowSpeed = new NumberSetting("Rainbow Speed",
            "Rainbow cycle speed", 1.0, 0.2, 5.0, 0.1);

    // --- Solid Mode specific ---
    public final NumberSetting solidAlpha = new NumberSetting("Solid Alpha",
            "Opacity strength for solid body mode", 75.0, 10.0, 100.0, 5.0);

    // --- Detail options ---
    public final BooleanSetting damageFlash = new BooleanSetting("Damage Flash",
            "Flash bright red when player takes damage", true);

    public final BooleanSetting healthBar = new BooleanSetting("Health Bar",
            "Show side health bar", true);

    public final BooleanSetting showName = new BooleanSetting("Show Name",
            "Display player name above", true);

    public final BooleanSetting showDistance = new BooleanSetting("Show Distance",
            "Display distance in blocks", true);

    public final BooleanSetting showSleeping = new BooleanSetting("Show Sleeping",
            "Include sleeping players", false);

    public final BooleanSetting showInvisible = new BooleanSetting("Show Invisible",
            "Include invisible players", true);

    public final BooleanSetting self = new BooleanSetting("Show Self",
            "Render on own body in third person", false);

    private final List<PlayerEntity> cachedPlayers = new ArrayList<>();
    private final Map<UUID, Float> lastHealth = new HashMap<>();
    private final Map<UUID, Long> flashUntil = new HashMap<>();

    public PlayerESPv2() {
        super("PlayerESPv2", "Next-gen Player ESP with 3D Skeleton, Solid body, and 3D/2D boxes", Category.RENDER);
        addSetting(mode);
        addSetting(maxDistance);
        addSetting(lineWidth);
        addSetting(mainColor);
        addSetting(wallColor);
        addSetting(useWallColor);
        addSetting(rainbow);
        addSetting(rainbowSpeed);
        addSetting(solidAlpha);
        addSetting(damageFlash);
        addSetting(healthBar);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showSleeping);
        addSetting(showInvisible);
        addSetting(self);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        cachedPlayers.clear();
        double maxDistSq = (double) maxDistance.getValue() * (double) maxDistance.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player && !self.isEnabled()) continue;
            if (!player.isAlive()) continue;
            if (!showSleeping.isEnabled() && player.isSleeping()) continue;
            if (!showInvisible.isEnabled() && player.isInvisible()) continue;
            if (player.squaredDistanceTo(mc.player) > maxDistSq) continue;

            cachedPlayers.add(player);

            // Track damage flash
            if (damageFlash.isEnabled()) {
                float hp = player.getHealth();
                Float prev = lastHealth.put(player.getUuid(), hp);
                if (prev != null && hp < prev) {
                    flashUntil.put(player.getUuid(), System.currentTimeMillis() + 350L);
                }
            }

            // Solid mode colorizer hook
            if (mode.is("Solid")) {
                int col = resolveColor(player, false);
                PlayerColorizer.setMode(PlayerColorizer.FLAT);
                PlayerColorizer.setTintStrength((float) ((double) solidAlpha.getValue() / 100.0));
                PlayerColorizer.track(player, col);
            }
        }
    }

    @Override
    public void onDisable() {
        cachedPlayers.clear();
        lastHealth.clear();
        flashUntil.clear();
        PlayerColorizer.reset();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || cachedPlayers.isEmpty()) return;

        for (PlayerEntity player : cachedPlayers) {
            if (player == null || !player.isAlive()) continue;

            boolean isFlashing = damageFlash.isEnabled()
                    && flashUntil.getOrDefault(player.getUuid(), 0L) > System.currentTimeMillis();
            boolean isBehindWall = useWallColor.isEnabled() && !mc.player.canSee(player);
            int color = isFlashing ? ColorUtils.rgba(255, 30, 30, 255) : resolveColor(player, isBehindWall);

            String selectedMode = mode.getValue();
            switch (selectedMode) {
                case "Skeleton" -> renderSkeleton(context, player, tickDelta, color);
                case "Solid" -> renderSolid(context, player, tickDelta, color);
                case "Box 3D" -> renderBox3D(context, player, tickDelta, color);
                case "Box 2D" -> renderBox2D(context, player, tickDelta, color);
            }

            if (showName.isEnabled() || showDistance.isEnabled() || healthBar.isEnabled()) {
                renderLabelsAndHealth(context, player, tickDelta, color);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  SKELETON ESP — Mathematically locked to the interpolated player BoundingBox
    // -------------------------------------------------------------------------
    private void renderSkeleton(DrawContext context, PlayerEntity player, float tickDelta, int color) {
        // Interpolated world bounding box
        Vec3d lerp = ProjectionUtil.lerpPos(player, tickDelta);
        Box box = player.getBoundingBox().offset(-player.getX(), -player.getY(), -player.getZ()).offset(lerp.x, lerp.y, lerp.z);

        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double minY = box.minY;
        double maxY = box.maxY;
        double h = maxY - minY;

        // Check if flying horizontally (elytra / swimming / crawling)
        boolean isHorizontal = player.isGliding() || player.isSwimming() || h < 1.0;

        float bodyYaw = player.bodyYaw;
        float headYaw = player.headYaw;
        float pitch = player.getPitch();

        float bodyYawRad = (float) Math.toRadians(bodyYaw);
        float headYawRad = (float) Math.toRadians(headYaw);
        float pitchRad = (float) Math.toRadians(pitch);

        // Basis vectors for body orientation
        double cosYaw = Math.cos(bodyYawRad);
        double sinYaw = Math.sin(bodyYawRad);

        // Forward and right vectors in Minecraft coordinate space
        // Forward: x = -sinYaw, z = cosYaw
        // Right:   x = -cosYaw, z = -sinYaw
        double fwdX = -sinYaw;
        double fwdZ = cosYaw;
        double rgtX = -cosYaw;
        double rgtZ = -sinYaw;

        Vec3d headPos;
        Vec3d neckPos;
        Vec3d pelvisPos;
        Vec3d leftShoulder;
        Vec3d rightShoulder;
        Vec3d leftHip;
        Vec3d rightHip;

        double maxArmSwing = Math.min(0.20, (box.maxX - box.minX) * 0.35);
        double maxLegSwing = Math.min(0.25, (box.maxX - box.minX) * 0.40);
        float limbSpeed = Math.min(1.0f, player.limbAnimator.getSpeed());
        float swingAngle = MathHelper.cos((player.age + tickDelta) * 0.6662f) * limbSpeed;
        double armOffset = swingAngle * maxArmSwing;
        double legOffset = swingAngle * maxLegSwing;

        float w = (float) (double) lineWidth.getValue();

        if (isHorizontal) {
            // Horizontal flight / swim pose
            double lookPitchRad = Math.toRadians(MathHelper.clamp(pitch, -80f, 80f));
            double cosP = Math.cos(lookPitchRad);
            double sinP = Math.sin(lookPitchRad);

            // Forward flight axis
            double flightFwdX = -Math.sin(headYawRad) * cosP;
            double flightFwdY = -sinP;
            double flightFwdZ = Math.cos(headYawRad) * cosP;

            double bodyLen = Math.max(1.4, h * 1.5);
            Vec3d center = new Vec3d(cx, (minY + maxY) / 2.0, cz);

            headPos = center.add(flightFwdX * (bodyLen * 0.45), flightFwdY * (bodyLen * 0.45), flightFwdZ * (bodyLen * 0.45));
            neckPos = center.add(flightFwdX * (bodyLen * 0.28), flightFwdY * (bodyLen * 0.28), flightFwdZ * (bodyLen * 0.28));
            pelvisPos = center.add(-flightFwdX * (bodyLen * 0.25), -flightFwdY * (bodyLen * 0.25), -flightFwdZ * (bodyLen * 0.25));

            double sWidth = 0.22;
            rightShoulder = neckPos.add(rgtX * sWidth, 0, rgtZ * sWidth);
            leftShoulder = neckPos.add(-rgtX * sWidth, 0, -rgtZ * sWidth);

            double hWidth = 0.14;
            rightHip = pelvisPos.add(rgtX * hWidth, 0, rgtZ * hWidth);
            leftHip = pelvisPos.add(-rgtX * hWidth, 0, -rgtZ * hWidth);

            Vec3d rightFoot = rightHip.add(-flightFwdX * 0.5, -flightFwdY * 0.5, -flightFwdZ * 0.5);
            Vec3d leftFoot = leftHip.add(-flightFwdX * 0.5, -flightFwdY * 0.5, -flightFwdZ * 0.5);

            Vec3d rightHand = rightShoulder.add(flightFwdX * 0.4, flightFwdY * 0.4, flightFwdZ * 0.4);
            Vec3d leftHand = leftShoulder.add(flightFwdX * 0.4, flightFwdY * 0.4, flightFwdZ * 0.4);

            int[] sHead = ProjectionUtil.project(headPos, tickDelta);
            int[] sNeck = ProjectionUtil.project(neckPos, tickDelta);
            int[] sPelvis = ProjectionUtil.project(pelvisPos, tickDelta);
            int[] sRShoulder = ProjectionUtil.project(rightShoulder, tickDelta);
            int[] sLShoulder = ProjectionUtil.project(leftShoulder, tickDelta);
            int[] sRHip = ProjectionUtil.project(rightHip, tickDelta);
            int[] sLHip = ProjectionUtil.project(leftHip, tickDelta);
            int[] sRFoot = ProjectionUtil.project(rightFoot, tickDelta);
            int[] sLFoot = ProjectionUtil.project(leftFoot, tickDelta);
            int[] sRHand = ProjectionUtil.project(rightHand, tickDelta);
            int[] sLHand = ProjectionUtil.project(leftHand, tickDelta);

            drawBone(context, sHead, sNeck, color, w);
            drawBone(context, sNeck, sPelvis, color, w);
            drawBone(context, sNeck, sRShoulder, color, w);
            drawBone(context, sNeck, sLShoulder, color, w);
            drawBone(context, sRShoulder, sRHand, color, w);
            drawBone(context, sLShoulder, sLHand, color, w);
            drawBone(context, sPelvis, sRHip, color, w);
            drawBone(context, sPelvis, sLHip, color, w);
            drawBone(context, sRHip, sRFoot, color, w);
            drawBone(context, sLHip, sLFoot, color, w);

            if (sHead != null) {
                int r = Math.max(2, (int) (6.0 * (w / 1.5)));
                RenderUtils.drawBorder(context, sHead[0] - r, sHead[1] - r, sHead[0] + r, sHead[1] + r, 1, color);
            }
            return;
        }

        // Standard vertical standing / running / crouching pose
        double headY = maxY - (h * 0.13);
        double neckY = maxY - (h * 0.25);
        double spineY = minY + (h * 0.55);
        double pelvisY = minY + (h * 0.40);
        double kneeY = minY + (h * 0.20);
        double footY = minY + 0.02;

        headPos = new Vec3d(cx, headY, cz);
        neckPos = new Vec3d(cx, neckY, cz);
        Vec3d spinePos = new Vec3d(cx, spineY, cz);
        pelvisPos = new Vec3d(cx, pelvisY, cz);

        double shoulderSpan = Math.min(0.28, (box.maxX - box.minX) * 0.38);
        rightShoulder = neckPos.add(rgtX * shoulderSpan, 0, rgtZ * shoulderSpan);
        leftShoulder = neckPos.add(-rgtX * shoulderSpan, 0, -rgtZ * shoulderSpan);

        double hipSpan = Math.min(0.14, (box.maxX - box.minX) * 0.22);
        rightHip = pelvisPos.add(rgtX * hipSpan, 0, rgtZ * hipSpan);
        leftHip = pelvisPos.add(-rgtX * hipSpan, 0, -rgtZ * hipSpan);

        // Arms (opposite swings)
        double armDrop = (neckY - pelvisY) * 0.55;
        Vec3d rightElbow = rightShoulder.add(fwdX * armOffset, -armDrop, fwdZ * armOffset);
        Vec3d rightHand = rightElbow.add(fwdX * (armOffset * 1.2), -armDrop * 0.9, fwdZ * (armOffset * 1.2));

        Vec3d leftElbow = leftShoulder.add(-fwdX * armOffset, -armDrop, -fwdZ * armOffset);
        Vec3d leftHand = leftElbow.add(-fwdX * (armOffset * 1.2), -armDrop * 0.9, -fwdZ * (armOffset * 1.2));

        // Legs (opposite swings)
        double legDrop = pelvisY - kneeY;
        Vec3d rightKnee = rightHip.add(-fwdX * legOffset, -legDrop, -fwdZ * legOffset);
        Vec3d rightFoot = new Vec3d(rightKnee.x - fwdX * (legOffset * 0.5), footY, rightKnee.z - fwdZ * (legOffset * 0.5));

        Vec3d leftKnee = leftHip.add(fwdX * legOffset, -legDrop, fwdZ * legOffset);
        Vec3d leftFoot = new Vec3d(leftKnee.x + fwdX * (legOffset * 0.5), footY, leftKnee.z + fwdZ * (legOffset * 0.5));

        // Head look direction vector
        double cosP = Math.cos(pitchRad);
        Vec3d headLook = headPos.add(-Math.sin(headYawRad) * cosP * 0.22, -Math.sin(pitchRad) * 0.22, Math.cos(headYawRad) * cosP * 0.22);

        // Project to screen space
        int[] sHead = ProjectionUtil.project(headPos, tickDelta);
        int[] sHeadLook = ProjectionUtil.project(headLook, tickDelta);
        int[] sNeck = ProjectionUtil.project(neckPos, tickDelta);
        int[] sSpine = ProjectionUtil.project(spinePos, tickDelta);
        int[] sPelvis = ProjectionUtil.project(pelvisPos, tickDelta);

        int[] sRShoulder = ProjectionUtil.project(rightShoulder, tickDelta);
        int[] sRElbow = ProjectionUtil.project(rightElbow, tickDelta);
        int[] sRHand = ProjectionUtil.project(rightHand, tickDelta);

        int[] sLShoulder = ProjectionUtil.project(leftShoulder, tickDelta);
        int[] sLElbow = ProjectionUtil.project(leftElbow, tickDelta);
        int[] sLHand = ProjectionUtil.project(leftHand, tickDelta);

        int[] sRHip = ProjectionUtil.project(rightHip, tickDelta);
        int[] sRKnee = ProjectionUtil.project(rightKnee, tickDelta);
        int[] sRFoot = ProjectionUtil.project(rightFoot, tickDelta);

        int[] sLHip = ProjectionUtil.project(leftHip, tickDelta);
        int[] sLKnee = ProjectionUtil.project(leftKnee, tickDelta);
        int[] sLFoot = ProjectionUtil.project(leftFoot, tickDelta);

        // Draw skeleton lines
        drawBone(context, sHead, sNeck, color, w);
        drawBone(context, sHead, sHeadLook, ColorUtils.rgba(255, 255, 255, 220), Math.max(1.0f, w - 0.5f));
        drawBone(context, sNeck, sSpine, color, w);
        drawBone(context, sSpine, sPelvis, color, w);

        // Right arm
        drawBone(context, sNeck, sRShoulder, color, w);
        drawBone(context, sRShoulder, sRElbow, color, w);
        drawBone(context, sRElbow, sRHand, color, w);

        // Left arm
        drawBone(context, sNeck, sLShoulder, color, w);
        drawBone(context, sLShoulder, sLElbow, color, w);
        drawBone(context, sLElbow, sLHand, color, w);

        // Right leg
        drawBone(context, sPelvis, sRHip, color, w);
        drawBone(context, sRHip, sRKnee, color, w);
        drawBone(context, sRKnee, sRFoot, color, w);

        // Left leg
        drawBone(context, sPelvis, sLHip, color, w);
        drawBone(context, sLHip, sLKnee, color, w);
        drawBone(context, sLKnee, sLFoot, color, w);

        // Head joint ring
        if (sHead != null) {
            int radius = Math.max(2, (int) (7.0 * (w / 1.5)));
            RenderUtils.drawBorder(context, sHead[0] - radius, sHead[1] - radius, sHead[0] + radius, sHead[1] + radius, 1, color);
        }
    }

    private void drawBone(DrawContext context, int[] a, int[] b, int color, float width) {
        if (a == null || b == null) return;
        RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], color, width);
    }

    // -------------------------------------------------------------------------
    //  SOLID BODY & 3D BOX MODES
    // -------------------------------------------------------------------------
    private void renderSolid(DrawContext context, PlayerEntity player, float tickDelta, int color) {
        renderBox3D(context, player, tickDelta, color);
    }

    private void renderBox3D(DrawContext context, PlayerEntity player, float tickDelta, int color) {
        Vec3d pos = ProjectionUtil.lerpPos(player, tickDelta);
        Box box = player.getBoundingBox().offset(-player.getX(), -player.getY(), -player.getZ()).offset(pos.x, pos.y, pos.z);

        Vec3d[] corners = new Vec3d[]{
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ)
        };

        int[][] sc = new int[8][];
        for (int i = 0; i < 8; i++) {
            sc[i] = ProjectionUtil.project(corners[i], tickDelta);
        }

        float w = (float) (double) lineWidth.getValue();

        // 12 box edges
        drawBone(context, sc[0], sc[1], color, w);
        drawBone(context, sc[1], sc[2], color, w);
        drawBone(context, sc[2], sc[3], color, w);
        drawBone(context, sc[3], sc[0], color, w);

        drawBone(context, sc[4], sc[5], color, w);
        drawBone(context, sc[5], sc[6], color, w);
        drawBone(context, sc[6], sc[7], color, w);
        drawBone(context, sc[7], sc[4], color, w);

        drawBone(context, sc[0], sc[4], color, w);
        drawBone(context, sc[1], sc[5], color, w);
        drawBone(context, sc[2], sc[6], color, w);
        drawBone(context, sc[3], sc[7], color, w);
    }

    private void renderBox2D(DrawContext context, PlayerEntity player, float tickDelta, int color) {
        int[] bounds = computeScreenBounds(player, tickDelta);
        if (bounds == null) return;

        int x1 = bounds[0];
        int y1 = bounds[1];
        int x2 = bounds[2];
        int y2 = bounds[3];

        int cornerW = Math.max(3, (x2 - x1) / 4);
        int cornerH = Math.max(3, (y2 - y1) / 4);
        float w = (float) (double) lineWidth.getValue();

        // Top-left corner
        RenderUtils.drawLine(context, x1, y1, x1 + cornerW, y1, color, w);
        RenderUtils.drawLine(context, x1, y1, x1, y1 + cornerH, color, w);

        // Top-right corner
        RenderUtils.drawLine(context, x2, y1, x2 - cornerW, y1, color, w);
        RenderUtils.drawLine(context, x2, y1, x2, y1 + cornerH, color, w);

        // Bottom-left corner
        RenderUtils.drawLine(context, x1, y2, x1 + cornerW, y2, color, w);
        RenderUtils.drawLine(context, x1, y2, x1, y2 - cornerH, color, w);

        // Bottom-right corner
        RenderUtils.drawLine(context, x2, y2, x2 - cornerW, y2, color, w);
        RenderUtils.drawLine(context, x2, y2, x2, y2 - cornerH, color, w);
    }

    // -------------------------------------------------------------------------
    //  LABELS & HEALTH BAR
    // -------------------------------------------------------------------------
    private void renderLabelsAndHealth(DrawContext context, PlayerEntity player, float tickDelta, int color) {
        int[] bounds = computeScreenBounds(player, tickDelta);
        if (bounds == null) return;

        int x1 = bounds[0];
        int y1 = bounds[1];
        int x2 = bounds[2];
        int y2 = bounds[3];

        // Health bar on left
        if (healthBar.isEnabled()) {
            float hp = MathHelper.clamp(player.getHealth(), 0f, player.getMaxHealth());
            float hpPct = hp / player.getMaxHealth();

            int barX = x1 - 5;
            int barHeight = y2 - y1;
            int filledHeight = (int) (barHeight * hpPct);

            context.fill(barX - 1, y1 - 1, barX + 2, y2 + 1, ColorUtils.rgba(15, 15, 15, 200));

            int hpColor = hpPct > 0.6f ? ColorUtils.rgba(0, 230, 118, 255)
                    : hpPct > 0.3f ? ColorUtils.rgba(255, 180, 0, 255)
                    : ColorUtils.rgba(255, 60, 60, 255);

            context.fill(barX, y2 - filledHeight, barX + 1, y2, hpColor);
        }

        // Name and distance above
        StringBuilder label = new StringBuilder();
        if (showName.isEnabled()) label.append(player.getName().getString());
        if (showDistance.isEnabled()) {
            double dist = mc.player.distanceTo(player);
            if (label.length() > 0) label.append(" ");
            label.append(String.format("[%.1fm]", dist));
        }

        if (label.length() > 0) {
            String text = label.toString();
            int tw = mc.textRenderer.getWidth(text);
            int cx = (x1 + x2) / 2;
            int textX = cx - tw / 2;
            int textY = y1 - 11;

            context.fill(textX - 2, textY - 1, textX + tw + 2, textY + 9, ColorUtils.rgba(15, 15, 15, 180));
            context.drawText(mc.textRenderer, text, textX, textY, color, false);
        }
    }

    private int[] computeScreenBounds(PlayerEntity player, float tickDelta) {
        Vec3d pos = ProjectionUtil.lerpPos(player, tickDelta);
        Box box = player.getBoundingBox().offset(-player.getX(), -player.getY(), -player.getZ()).offset(pos.x, pos.y, pos.z);

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        Vec3d[] corners = new Vec3d[]{
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ)
        };

        boolean anyVisible = false;
        for (Vec3d c : corners) {
            int[] sc = ProjectionUtil.project(c, tickDelta);
            if (sc == null) continue;
            anyVisible = true;
            if (sc[0] < minX) minX = sc[0];
            if (sc[1] < minY) minY = sc[1];
            if (sc[0] > maxX) maxX = sc[0];
            if (sc[1] > maxY) maxY = sc[1];
        }

        if (!anyVisible || minX >= maxX || minY >= maxY) return null;
        return new int[]{minX, minY, maxX, maxY};
    }

    private int resolveColor(PlayerEntity player, boolean behindWall) {
        if (rainbow.isEnabled()) {
            double spd = (double) rainbowSpeed.getValue();
            float hue = (float) ((System.currentTimeMillis() % (long) (3000 / spd)) / (3000.0 / spd));
            int rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f);
            return (0xFF << 24) | (rgb & 0x00FFFFFF);
        }
        return behindWall ? wallColor.getColor() : mainColor.getColor();
    }
}
