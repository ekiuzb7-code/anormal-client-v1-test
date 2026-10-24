package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.util.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * EntityESP — draws boxes + info tags (name, health, armor, distance) over nearby entities.
 *
 * Rendered as a 2D screen-space overlay. Each tick we scan nearby entities;
 * each render frame we project their position to screen coordinates using a
 * manual camera-relative projection (inverted camera quaternion + the game's
 * actual projection matrix from {@link net.minecraft.client.render.GameRenderer#getBasicProjectionMatrix}
 * which bakes in the player's FOV option — no reflection needed) and draw:
 *   - A 2D box around the entity (8 AABB corners → min/max screen X/Y)
 *   - An info tag showing: name, health bar, armor icons (with enchantment
 *     glint purple overlay), distance
 *
 * Behind-camera guard: a corner with w <= 0.05 after VP transformation is
 * considered behind the camera and is skipped. If ALL 8 corners are behind
 * the camera, the entity is skipped entirely (prevents the
 * "stretched across screen" glitch).
 *
 * Settings:
 *   Range         — scan radius (blocks)
 *   Players Only  — only show other players (skip mobs/animals)
 *   Color         — box + tag color (color picker, click to cycle)
 *   Show Health   — render a health bar
 *   Show Armor    — render armor item icons (with glint highlight)
 *   Show Distance — render distance in blocks
 *   Show Name     — render entity name
 */
public class EntityESP extends Module {

    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Scan radius for entities (blocks).", 8f, 64f, 32f, 0));
    private final BooleanSetting playersOnly = addSetting(
        new BooleanSetting("Players Only", "Only show other players (skip mobs/animals).", true));
    private final ColorSetting color = addSetting(
        new ColorSetting("Color", "Box + tag color. Click to cycle.", Colors.ACCENT));
    private final BooleanSetting showHealth = addSetting(
        new BooleanSetting("Show Health", "Render a health bar under the name.", true));
    private final BooleanSetting showArmor = addSetting(
        new BooleanSetting("Show Armor", "Render armor item icons (with glint highlight).", true));
    private final BooleanSetting showDistance = addSetting(
        new BooleanSetting("Show Distance", "Render the distance in blocks.", true));
    private final BooleanSetting showName = addSetting(
        new BooleanSetting("Show Name", "Render the entity name.", true));

    // Cached snapshot of entities to render (updated each tick, read each frame).
    private final List<LivingEntity> cachedTargets = new ArrayList<>();

    public EntityESP() {
        super("EntityESP", "Draws boxes + health/armor/name tags over nearby entities.",
            Category.RENDER);
    }

    @Override
    protected void onEnable() {
        cachedTargets.clear();
    }

    @Override
    protected void onDisable() {
        cachedTargets.clear();
    }

    // ── Tick: scan + cache targets (20Hz) ────────────────────
    @Override
    public void onTick(MinecraftClient client) {
        cachedTargets.clear();
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        double r = range.get();
        Box searchBox = new Box(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r);

        List<Entity> candidates = world.getOtherEntities(player, searchBox, e -> {
            if (!(e instanceof LivingEntity)) return false;
            if (!e.isAlive()) return false;
            if (e.isRemoved()) return false;
            if (playersOnly.isEnabled() && !(e instanceof PlayerEntity)) return false;
            return e.squaredDistanceTo(player) <= r * r;
        });

        for (Entity e : candidates) {
            cachedTargets.add((LivingEntity) e);
        }
    }

    // ── Render: draw ESP overlay each frame (called from LucidClient HUD render) ──
    public void draw(DrawContext ctx, float tickDelta) {
        if (cachedTargets.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.gameRenderer == null) return;
        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;
        Vec3d camPos = camera.getCameraPos();

        // Use the game's actual projection matrix for accurate FOV/aspect handling.
        // getBasicProjectionMatrix takes FOV DEGREES (not tickDelta). We read the
        // player's FOV option (a SimpleOption<Float>) and pass its value. The old
        // code passed tickDelta (~0-1) as the FOV, a near-zero-FOV projection that
        // collapsed/glitched the ESP boxes.
        float fovDeg = client.options.getFov().getValue();
        Matrix4f projMatrix = client.gameRenderer.getBasicProjectionMatrix(fovDeg);

        // Build the view matrix using the camera's ACTUAL orientation quaternion.
        // This is the exact same rotation the game uses internally — no manual
        // yaw/pitch trig that could have sign/order errors.
        // V = R_inv * T(-camPos)
        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.identity();
        Quaternionf invRot = new Quaternionf(camera.getRotation()).invert();
        viewMatrix.rotate(invRot);
        viewMatrix.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);

        // View-projection matrix = P * V (precompute once per frame)
        Matrix4f vpMatrix = new Matrix4f();
        vpMatrix.set(projMatrix);
        vpMatrix.mul(viewMatrix);

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        TextRenderer tr = client.textRenderer;
        int boxColor = color.getColor();
        ClientPlayerEntity self = client.player;

        for (LivingEntity ent : cachedTargets) {
            if (ent.isRemoved() || !ent.isAlive()) continue;

            // Use the entity's bounding box (AABB) — per spec, this ensures
            // the overlay stays consistent regardless of animations.
            Vec3d pos = ent.getLerpedPos(tickDelta);
            float entHeight = ent.getHeight();
            float entWidth = ent.getWidth() / 2f;

            // 8 corners of the AABB
            Vec3d[] corners = {
                pos.add(-entWidth, 0,         -entWidth),
                pos.add( entWidth, 0,         -entWidth),
                pos.add( entWidth, 0,          entWidth),
                pos.add(-entWidth, 0,          entWidth),
                pos.add(-entWidth, entHeight, -entWidth),
                pos.add( entWidth, entHeight, -entWidth),
                pos.add( entWidth, entHeight,  entWidth),
                pos.add(-entWidth, entHeight,  entWidth),
            };

            // Project all 8 corners. Track min/max screen X/Y from valid (in-front) corners.
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            int validCount = 0;

            for (Vec3d corner : corners) {
                Vector4f v = new Vector4f(
                    (float) corner.x, (float) corner.y, (float) corner.z, 1f);
                vpMatrix.transform(v);

                // Check if the point is in front of the camera (w > 0).
                // If w <= 0, the point is behind the camera — skip it.
                // Use the 0.05 guard per spec to avoid edge-of-perspective
                // singularities.
                if (v.w <= 0.05f) continue;

                // Perspective divide → NDC [-1, 1]
                float ndcX = v.x / v.w;
                float ndcY = v.y / v.w;
                float ndcZ = v.z / v.w;

                // Skip points outside the near/far plane (behind camera or past far plane)
                if (ndcZ < -1f || ndcZ > 1f) continue;

                validCount++;
                float screenX = (ndcX * 0.5f + 0.5f) * screenW;
                float screenY = (0.5f - ndcY * 0.5f) * screenH;
                if (screenX < minX) minX = screenX;
                if (screenX > maxX) maxX = screenX;
                if (screenY < minY) minY = screenY;
                if (screenY > maxY) maxY = screenY;
            }

            // If no corners are in front of the camera, skip this entity entirely.
            // This prevents the "glitching on the side of the screen" bug where
            // partially-behind entities would stretch across the screen.
            if (validCount == 0) continue;

            int bx = (int) minX;
            int by = (int) minY;
            int bw = (int) (maxX - minX);
            int bh = (int) (maxY - minY);
            if (bw < 3) bw = 3;
            if (bh < 3) bh = 3;

            drawBoxOutline(ctx, bx, by, bw, bh, boxColor);
            if (showHealth.isEnabled()) {
                drawVerticalHealthBar(ctx, bx, by, bw, bh, ent);
            }
            drawInfoTag(ctx, tr, self, ent, bx, by, bw);
        }
    }

    private void drawBoxOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        // 1px outline with slightly thicker corners for a clean ESP look
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * Vertical health bar hugging the RIGHT edge of the ESP box.
     * Full entity height, fills bottom-up, green-to-red by ratio, with a
     * soft glow and rounded caps. No black outline anywhere.
     */
    private void drawVerticalHealthBar(DrawContext ctx, int boxX, int boxY, int boxW, int boxH,
                                       LivingEntity ent) {
        float health = ent.getHealth();
        float maxHealth = ent.getMaxHealth();
        if (maxHealth <= 0f) return;
        float ratio = MathHelper.clamp(health / maxHealth, 0f, 1f);

        int barW = 2;                       // crisp 2px core
        int gap = 2;                        // gap between box edge and bar
        int barX = boxX + boxW + gap;
        int barY = boxY;
        int barH = Math.max(6, boxH);       // full entity height

        // Track — translucent dark, softly rounded, NO black border.
        RenderUtils.drawRoundedRect(ctx, barX - 1, barY - 1, barW + 2, barH + 2, 2,
            Colors.withAlpha(0x000000, 90));

        // Fill — grows from the bottom up.
        int fillH = (int) (barH * ratio);
        if (fillH > 0) {
            int fillY = barY + barH - fillH;
            // Health color: green > yellow > red.
            int barColor = ratio > 0.5f ? Colors.SUCCESS : (ratio > 0.25f ? Colors.WARNING : Colors.ERROR);
            // Soft glow behind the fill.
            RenderUtils.drawRoundedRect(ctx, barX - 2, fillY - 1, barW + 4, fillH + 2, 2,
                Colors.withAlpha(barColor, 40));
            // Core fill with rounded caps.
            RenderUtils.drawRoundedRect(ctx, barX, fillY, barW, fillH, 1, barColor);
            // Bright tip at the top edge of the fill (little FX detail).
            RenderUtils.drawRoundedRect(ctx, barX, fillY, barW, 1, 0,
                Colors.withAlpha(0xFFFFFF, 120));
        }
    }

    private void drawInfoTag(DrawContext ctx, TextRenderer tr, ClientPlayerEntity self,
                             LivingEntity ent, int boxX, int boxY, int boxW) {
        // Build tag lines
        List<String> lines = new ArrayList<>();
        if (showName.isEnabled()) {
            Text name = ent.getName();
            lines.add(name != null ? name.getString() : ent.getDisplayName().getString());
        }
        float health = ent.getHealth();
        float maxHealth = ent.getMaxHealth();
        int armor = ent.getArmor();
        double distance = self.distanceTo(ent);

        if (showHealth.isEnabled()) {
            lines.add(String.format("HP %.0f / %.0f", health, maxHealth));
        }
        if (showArmor.isEnabled() && armor > 0) {
            lines.add("Armor " + armor);
        }
        if (showDistance.isEnabled()) {
            lines.add(String.format("%.1fm", distance));
        }
        if (lines.isEmpty()) return;

        // Measure tag
        int padX = 6, padY = 3;
        int lineHeight = tr.fontHeight + 1;
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, tr.getWidth(l));
        int tagW = maxW + padX * 2;
        int tagH = lines.size() * lineHeight + padY * 2 - 1;

        // Reserve room for armor icons row if enabled.
        int armorRowH = 0;
        if (showArmor.isEnabled()) {
            armorRowH = 18; // 16px icon + 2px padding
            tagH += armorRowH;
        }

        // Center tag on the box horizontally
        int tx = boxX + (boxW - tagW) / 2;
        if (tx < 2) tx = 2;
        int ty = boxY - tagH - 2;
        if (ty < 2) ty = boxY + 2; // flip below if no room

        // No border / no background panel — the info floats clean next to the
        // box. Readability comes from the per-glyph drop shadow only.

        // Text lines
        int textY = ty + padY;
        for (String l : lines) {
            RenderUtils.drawText(ctx, tr, l, tx + padX, textY, Colors.TEXT_INVERTED, true);
            textY += lineHeight;
        }

        // Armor icon row — draw HEAD / CHEST / LEGS / FEET stacks left-to-right.
        // If a stack has an enchantment glint, overlay a translucent purple fill
        // on top of the icon to indicate "enchanted".
        if (showArmor.isEnabled()) {
            int iconSize = 16;
            int iconGap = 1;
            EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            int totalIcons = armorSlots.length * iconSize + (armorSlots.length - 1) * iconGap;
            int iconRowX = tx + (tagW - totalIcons) / 2;
            int iconRowY = textY + 1;
            for (EquipmentSlot slot : armorSlots) {
                ItemStack armorStack = ent.getEquippedStack(slot);
                if (!armorStack.isEmpty()) {
                    ctx.drawItem(armorStack, iconRowX, iconRowY);
                    // Enchantment glint overlay — purple tint to flag enchanted gear.
                    if (armorStack.hasGlint()) {
                        ctx.fill(iconRowX, iconRowY, iconRowX + iconSize, iconRowY + iconSize,
                            Colors.withAlpha(0x8B5CF6, 90));
                    }
                }
                iconRowX += iconSize + iconGap;
            }
        }

    }
}
