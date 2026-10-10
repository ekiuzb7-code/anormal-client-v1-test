package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ESPv2 extends Module {
    // Mode settings - LiquidBounce style
    public final ModeSetting mode = new ModeSetting("Mode", "ESP visual mode", "Box", "Box", "2D", "Glow", "Outline");
    public final ModeSetting boxMode = new ModeSetting("Box Mode", "Box rendering mode", "Accurate", "Accurate", "Fancy");
    public final ModeSetting colorMode = new ModeSetting("Color Mode", "How to color entities", "Distance", "Distance", "Health", "Static", "Rainbow");

    // Target settings
    public final BooleanSetting players = new BooleanSetting("Players", "Highlight other players", true);
    public final BooleanSetting mobs = new BooleanSetting("Monsters", "Highlight hostile mobs", true);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Highlight passive animals", false);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Show invisible entities", false);
    public final BooleanSetting sleeping = new BooleanSetting("Sleeping", "Show sleeping entities", false);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Maximum render distance", 128.0, 1.0, 512.0, 1.0);

    // Visual settings
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw outline", true);
    public final NumberSetting outlineThickness = new NumberSetting("Outline Thickness", "Outline line thickness", 1.0, 0.5, 5.0, 0.5);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Fill boxes with translucent color", true);
    public final NumberSetting fillAlpha = new NumberSetting("Fill Alpha", "Fill opacity", 50, 0, 255, 5);
    public final BooleanSetting mergeIntersecting = new BooleanSetting("Merge Intersecting", "Merge intersecting boxes", false);

    // Corner style (2D mode)
    public final BooleanSetting corners = new BooleanSetting("Corners", "Draw corners instead of full box", false);
    public final NumberSetting cornerGap = new NumberSetting("Corner Gap", "Gap percentage for corners", 50.0, 1.0, 100.0, 1.0);

    // Border (2D mode)
    public final BooleanSetting border = new BooleanSetting("Border", "Draw black border behind outline", true);
    public final NumberSetting borderThickness = new NumberSetting("Border Thickness", "Border thickness", 1.0, 0.5, 5.0, 0.5);

    // Health bar
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Show health bar on boxes", true);
    public final NumberSetting healthBarSpacing = new NumberSetting("Health Bar Spacing", "Spacing from box", 2.0, 0.0, 32.0, 1.0);

    // Name
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show entity name", true);

    // Colors
    public final ColorSetting color = new ColorSetting("Color", "Default entity color", ColorUtils.rgba(255, 60, 60, 255));
    public final ColorSetting friendColor = new ColorSetting("Friend Color", "Friend entity color", ColorUtils.rgba(0, 255, 0, 255));
    public final ColorSetting invisibleColor = new ColorSetting("Invisible Color", "Invisible entity color", ColorUtils.rgba(255, 165, 0, 255));

    // Distance color mode
    public final ColorSetting distanceColorNear = new ColorSetting("Near Color", "Color for nearby entities", ColorUtils.rgba(0, 255, 0, 255));
    public final ColorSetting distanceColorFar = new ColorSetting("Far Color", "Color for far entities", ColorUtils.rgba(255, 0, 0, 255));

    // Static color
    public final ColorSetting staticColor = new ColorSetting("Static Color", "Static entity color", ColorUtils.rgba(255, 255, 255, 255));

    // Friends list (integrates with Friends module)
    private Set<String> friends = new HashSet<>();

    public ESPv2() {
        super("ESP V2", "Advanced ESP with LiquidBounce-style modes", Category.RENDER);
        addSetting(mode);
        addSetting(boxMode);
        addSetting(colorMode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(invisibles);
        addSetting(sleeping);
        addSetting(maxDistance);
        addSetting(outline);
        addSetting(outlineThickness);
        addSetting(fill);
        addSetting(fillAlpha);
        addSetting(mergeIntersecting);
        addSetting(corners);
        addSetting(cornerGap);
        addSetting(border);
        addSetting(borderThickness);
        addSetting(healthBar);
        addSetting(healthBarSpacing);
        addSetting(showName);
        addSetting(color);
        addSetting(friendColor);
        addSetting(invisibleColor);
        addSetting(distanceColorNear);
        addSetting(distanceColorFar);
        addSetting(staticColor);
    }

    @Override
    public void onEnable() {
        // Subscribe to entity renderer
    }

    @Override
    public void onDisable() {
        // Unsubscribe
    }

    private int getEntityColor(Entity entity) {
        // Check if entity is a friend
        if (entity instanceof PlayerEntity player) {
            if (friends.contains(player.getName().getString())) {
                return friendColor.getValue();
            }
        }

        // Check invisible
        if (entity.isInvisible()) {
            return invisibleColor.getValue();
        }

        // Hurt color
        if (entity instanceof LivingEntity living && living.hurtTime > 0) {
            return ColorUtils.rgba(255, 0, 0, 255);
        }

        switch (colorMode.getValue()) {
            case "Distance" -> {
                float dist = mc.player.distanceTo(entity) / 20.0f;
                float r = Math.min(1.0f, Math.max(0.0f, 2.0f - dist));
                float g = Math.min(1.0f, Math.max(0.0f, dist));
                return ColorUtils.rgba((int)(r * 255), (int)(g * 255), 0, 255);
            }
            case "Health" -> {
                if (entity instanceof LivingEntity living) {
                    float pct = Math.max(0.0f, Math.min(1.0f, living.getHealth() / Math.max(1.0f, living.getMaxHealth())));
                    int r = (int) (255 * (1.0f - pct));
                    int g = (int) (255 * pct);
                    return ColorUtils.rgba(r, g, 0, 255);
                }
                return color.getValue();
            }
            case "Static" -> {
                return staticColor.getValue();
            }
            case "Rainbow" -> {
                return ColorUtils.rainbow((int)(System.currentTimeMillis() / 10), 1.0f, 1.0f);
            }
            default -> {
                return color.getValue();
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;

            // Check if entity should be rendered
            boolean shouldRender = false;
            if (entity instanceof PlayerEntity) {
                if (!players.isEnabled()) continue;
                shouldRender = true;
            } else if (entity instanceof Monster) {
                if (!mobs.isEnabled()) continue;
                shouldRender = true;
            } else if (entity instanceof AnimalEntity) {
                if (!animals.isEnabled()) continue;
                shouldRender = true;
            } else {
                continue;
            }

            if (!invisibles.isEnabled() && entity.isInvisible()) continue;
            if (!sleeping.isEnabled() && entity.isSleeping()) continue;
            if (mc.player.distanceTo(entity) > maxDistance.getValue()) continue;

            renderEntity(context, entity, tickDelta);
        }
    }

    private void renderEntity(DrawContext context, Entity entity, float tickDelta) {
        Box box = entity.getBoundingBox();
        double hgt = box.maxY - box.minY;

        // Get interpolated position
        double[] lc = com.anormal.client.util.ProjectionUtil.lerpEntity(entity.getId(),
                (box.minX + box.maxX) / 2.0, (box.minY + box.maxY) / 2.0,
                (box.minZ + box.maxZ) / 2.0, tickDelta);
        double cx0 = Math.round(lc[0] * 16.0) / 16.0;
        double cz0 = Math.round(lc[2] * 16.0) / 16.0;
        double cyMid = lc[1];

        int[] sTop = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid + hgt / 2.0 + 0.1, cz0), tickDelta);
        int[] sBot = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid - hgt / 2.0, cz0), tickDelta);
        int[] sMid = com.anormal.client.util.ProjectionUtil.project(new Vec3d(cx0, cyMid, cz0), tickDelta);

        if (sTop == null || sBot == null || sMid == null) return;

        int h = Math.max(4, sBot[1] - sTop[1]);
        int w = Math.max(4, h / 3);
        int x = sMid[0];
        int yBot = Math.max(sTop[1], sBot[1]);
        int yTop = yBot - h;

        int color = getEntityColor(entity);
        int baseColor = (color & 0x00FFFFFF) | (fillAlpha.getValue().intValue() << 24);
        int outlineColor = (color & 0x00FFFFFF) | 0xFF000000;
        int black = 0xFF000000;

        boolean v2 = mode.is("V2") || mode.is("Box") || mode.is("2D");
        boolean cornersMode = corners.isEnabled();

        // Draw boxes
        if (mode.is("Box") || mode.is("2D") || mode.is("Outline") || mode.is("V2")) {
            if (cornersMode) {
                drawCorners(context, x, yTop, yBot, w, h, color, outlineColor, black);
            } else {
                drawBox(context, x, yTop, yBot, w, h, color, baseColor, outlineColor, black);
            }
        }

        // Health bar
        if (healthBar.isEnabled() && entity instanceof LivingEntity living) {
            float maxHp = Math.max(1.0f, living.getMaxHealth());
            float pct = Math.max(0.0f, Math.min(1.0f, living.getHealth() / maxHp));
            int barH = (int) (h * pct);
            int barCol = pct > 0.6 ? 0xFF55FF55 : (pct > 0.3 ? 0xFFFFFF55 : 0xFFFF5555);

            int barX = x - w / 2 - 4 - healthBarSpacing.getValue().intValue();
            context.fill(barX, yTop, barX + 2, yBot, 0xAA222222);
            context.fill(barX, yBot - barH, barX + 2, yBot, barCol);
        }

        // Name
        if (showName.isEnabled() && mc.textRenderer != null) {
            String name = entity.getName().getString();
            int nameX = x - mc.textRenderer.getWidth(name) / 2;
            int nameY = yTop - 11;
            RenderUtils.drawText(context, mc.textRenderer, name, nameX, nameY, 0xFFFFFFFF, true);
        }
    }

    private void drawBox(DrawContext context, int x, int yTop, int yBot, int w, int h, int color, int baseColor, int outlineColor, int black) {
        if (fill.isEnabled()) {
            context.fill(x - w / 2, yTop, x + w / 2, yBot, baseColor);
        }
        if (outline.isEnabled()) {
            context.fill(x - w / 2, yTop, x + w / 2, yTop + 1, outlineColor);
            context.fill(x - w / 2, yBot - 1, x + w / 2, yBot, outlineColor);
            context.fill(x - w / 2, yTop, x - w / 2 + 1, yBot, outlineColor);
            context.fill(x + w / 2 - 1, yTop, x + w / 2, yBot, outlineColor);
        }
    }

    private void drawCorners(DrawContext context, int x, int yTop, int yBot, int w, int h, int color, int outlineColor, int black) {
        int cl = Math.min(w / 3, 8);
        float gapPercent = cornerGap.getValue().floatValue() / 100.0f;
        double cw = w * (1.0 - gapPercent) / 2.0;
        double ch = h * (1.0 - gapPercent) / 2.0;

        // Top-left corner
        context.fill(x - w / 2, yTop, x - w / 2 + (int)cw, yTop + 1, outlineColor);
        context.fill(x - w / 2, yTop, x - w / 2 + 1, yTop + (int)ch, outlineColor);

        // Top-right corner
        context.fill(x + w / 2 - (int)cw, yTop, x + w / 2, yTop + 1, outlineColor);
        context.fill(x + w / 2 - 1, yTop, x + w / 2, yTop + (int)ch, outlineColor);

        // Bottom-left corner
        context.fill(x - w / 2, yBot - 1, x - w / 2 + (int)cw, yBot, outlineColor);
        context.fill(x - w / 2, yBot - (int)ch, x - w / 2 + 1, yBot, outlineColor);

        // Bottom-right corner
        context.fill(x + w / 2 - (int)cw, yBot - 1, x + w / 2, yBot, outlineColor);
        context.fill(x + w / 2 - 1, yBot - (int)ch, x + w / 2, yBot, outlineColor);
    }
}