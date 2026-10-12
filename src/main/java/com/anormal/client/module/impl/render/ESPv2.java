package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.MultiSelectSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Renders entities through walls.
 *
 * <p>Settings mirror Meteor's ESP module one-to-one: {@code mode}
 * (Box / Wireframe / 2D / Shader / Glow), {@code highlight-target},
 * {@code target-hitbox}, {@code outline-width}, {@code glow-multiplier},
 * {@code ignore-self}, {@code shape-mode} (Lines / Sides / Both),
 * {@code fill-opacity}, {@code fade-distance}, {@code entities},
 * {@code color-mode} (Entity Type / Distance / Health),
 * {@code show-friend-colors} and the full colour set.</p>
 *
 * <p>Rendering note: Meteor draws these shapes with its own 3D renderer and a
 * GLSL glow shader. This client draws a wireframe / filled box in the HUD layer
 * through the shared world&rarr;screen projector, which keeps the identical
 * settings and the same visual behaviour (boxes visible through walls) without
 * a custom render pipeline.</p>
 */
public class ESPv2 extends Module {
    // ---- General ----
    public final ModeSetting mode = new ModeSetting("Mode", "Rendering mode", "Box", "Box", "Wireframe", "2D", "Shader", "Glow");
    public final BooleanSetting highlightTarget = new BooleanSetting("Highlight Target", "Highlights the currently targeted entity differently", false);
    public final BooleanSetting targetHitbox = new BooleanSetting("Target Hitbox", "Draw the hitbox of the target entity", true);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Width of the outline", 2.0, 1.0, 10.0, 1.0);
    public final NumberSetting glowMultiplier = new NumberSetting("Glow Multiplier", "Multiplier for the glow effect", 3.5, 0.0, 10.0, 0.1);
    public final BooleanSetting ignoreSelf = new BooleanSetting("Ignore Self", "Ignores yourself when drawing", true);
    public final ModeSetting shapeMode = new ModeSetting("Shape Mode", "How the shapes are rendered", "Both", "Lines", "Sides", "Both");
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Opacity of the shape fill", 0.3, 0.0, 1.0, 0.05);
    public final NumberSetting fadeDistance = new NumberSetting("Fade Distance", "Distance where the colour begins to fade", 3.0, 0.0, 12.0, 0.5);
    public final MultiSelectSetting entities = new MultiSelectSetting("Entities", "Select which entity groups to render",
            new String[]{"Players"},
            "Players", "Animals", "Water Animals", "Monsters", "Ambient", "Misc", "Items");

    // ---- Colors ----
    public final ModeSetting colorMode = new ModeSetting("Color Mode", "Determines the colours used for entities", "Entity Type", "Entity Type", "Distance", "Health");
    public final BooleanSetting showFriendColors = new BooleanSetting("Show Friend Colors", "Override distance/health colours for friends", true);
    public final ColorSetting nonLivingEntityColor = new ColorSetting("Non-Living Entity Color", "Colour for dropped items", ColorUtils.rgba(25, 25, 25, 255));
    public final ColorSetting playersColor = new ColorSetting("Players Color", "The other player's colour", ColorUtils.rgba(255, 255, 255, 255));
    public final ColorSetting animalsColor = new ColorSetting("Animals Color", "The animal's colour", ColorUtils.rgba(25, 255, 25, 255));
    public final ColorSetting waterAnimalsColor = new ColorSetting("Water Animals Color", "The water animal's colour", ColorUtils.rgba(25, 25, 255, 255));
    public final ColorSetting monstersColor = new ColorSetting("Monsters Color", "The monster's colour", ColorUtils.rgba(255, 25, 25, 255));
    public final ColorSetting ambientColor = new ColorSetting("Ambient Color", "The ambient's colour", ColorUtils.rgba(25, 25, 25, 255));
    public final ColorSetting miscColor = new ColorSetting("Misc Color", "The misc colour", ColorUtils.rgba(175, 175, 175, 255));
    public final ColorSetting targetColor = new ColorSetting("Target Color", "The target colour", ColorUtils.rgba(200, 200, 200, 255));
    public final ColorSetting targetHitboxColor = new ColorSetting("Target Hitbox Color", "The target hitbox colour", ColorUtils.rgba(100, 200, 200, 255));

    public ESPv2() {
        super("ESPv2", "Renders entities through walls (Meteor-style)", Category.RENDER);

        addSetting(mode);
        addSetting(highlightTarget);
        addSetting(targetHitbox);
        addSetting(outlineWidth);
        addSetting(glowMultiplier);
        addSetting(ignoreSelf);
        addSetting(shapeMode);
        addSetting(fillOpacity);
        addSetting(fadeDistance);
        addSetting(entities);

        addSetting(colorMode);
        addSetting(showFriendColors);
        addSetting(nonLivingEntityColor);
        addSetting(playersColor);
        addSetting(animalsColor);
        addSetting(waterAnimalsColor);
        addSetting(monstersColor);
        addSetting(ambientColor);
        addSetting(miscColor);
        addSetting(targetColor);
        addSetting(targetHitboxColor);

        // Visibility mirrors Meteor exactly.
        targetHitbox.visibleIf(() -> highlightTarget.isEnabled());
        outlineWidth.visibleIf(() -> mode.is("Shader"));
        glowMultiplier.visibleIf(() -> mode.is("Shader"));
        shapeMode.visibleIf(() -> !mode.is("Glow"));
        fillOpacity.visibleIf(() -> !shapeMode.is("Lines") && !mode.is("Glow"));
        showFriendColors.visibleIf(() -> colorMode.is("Distance") || colorMode.is("Health"));
        nonLivingEntityColor.visibleIf(() -> colorMode.is("Health"));
        playersColor.visibleIf(() -> colorMode.is("Entity Type"));
        animalsColor.visibleIf(() -> colorMode.is("Entity Type"));
        waterAnimalsColor.visibleIf(() -> colorMode.is("Entity Type"));
        monstersColor.visibleIf(() -> colorMode.is("Entity Type"));
        ambientColor.visibleIf(() -> colorMode.is("Entity Type"));
        miscColor.visibleIf(() -> colorMode.is("Entity Type"));
        targetColor.visibleIf(() -> highlightTarget.isEnabled());
        targetHitboxColor.visibleIf(() -> highlightTarget.isEnabled() && targetHitbox.isEnabled());
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        boolean twoD = mode.is("2D");
        boolean lines = shapeMode.is("Lines") || shapeMode.is("Both");
        boolean sides = shapeMode.is("Sides") || shapeMode.is("Both");

        Entity target = null;
        if (highlightTarget.isEnabled() && targetHitbox.isEnabled()
                && mc.crosshairTarget instanceof EntityHitResult ehr) {
            target = ehr.getEntity();
        }

        try {
            for (Entity entity : mc.world.getEntities()) {
                if (!shouldRender(entity)) continue;
                int col = colorFor(entity, target);
                renderEntity(context, entity, col, tickDelta, twoD, lines, sides);
            }
        } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------------------------
    //  Selection
    // ---------------------------------------------------------------------
    private boolean shouldRender(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (ignoreSelf.isEnabled() && entity == mc.player) return false;

        if (entity instanceof ItemEntity) return entities.isSelected("Items");
        if (entity instanceof PlayerEntity) return entities.isSelected("Players");
        if (entity instanceof FishEntity) return entities.isSelected("Water Animals");
        if (entity instanceof AnimalEntity) return entities.isSelected("Animals");
        if (entity instanceof Monster) return entities.isSelected("Monsters");
        if (entity instanceof BatEntity) return entities.isSelected("Ambient");
        return entities.isSelected("Misc");
    }

    // ---------------------------------------------------------------------
    //  Colour (mirrors Meteor's ESP.getColor)
    // ---------------------------------------------------------------------
    private int colorFor(Entity entity, Entity target) {
        int base;
        if (highlightTarget.isEnabled() && target != null && entity == target) {
            base = targetColor.getValue();
        } else if (colorMode.is("Health")) {
            base = entity instanceof LivingEntity living ? healthColor(living) : nonLivingEntityColor.getValue();
        } else if (colorMode.is("Distance")) {
            base = distanceColor(entity);
        } else {
            base = entityTypeColor(entity);
        }

        // Friend override
        if (showFriendColors.isEnabled() && (colorMode.is("Distance") || colorMode.is("Health"))
                && entity instanceof PlayerEntity p && mc.player.isTeammate(p)) {
            base = playersColor.getValue();
        }

        // Fade with proximity (Meteor reduces alpha inside fade-distance).
        double fade = fadeDistance.getValue();
        if (fade > 0) {
            double distSq = mc.player.squaredDistanceTo(entity);
            if (distSq < fade * fade) {
                double f = Math.sqrt(distSq) / fade;
                int a = (int) (((base >> 24) & 0xFF) * Math.max(0.0, Math.min(1.0, f)));
                base = (a << 24) | (base & 0x00FFFFFF);
            }
        }
        return base;
    }

    private int entityTypeColor(Entity entity) {
        if (entity instanceof PlayerEntity) return playersColor.getValue();
        if (entity instanceof FishEntity) return waterAnimalsColor.getValue();
        if (entity instanceof AnimalEntity) return animalsColor.getValue();
        if (entity instanceof Monster) return monstersColor.getValue();
        if (entity instanceof BatEntity) return ambientColor.getValue();
        return miscColor.getValue();
    }

    private int distanceColor(Entity entity) {
        float dist = mc.player.distanceTo(entity);
        float f = Math.max(0f, Math.min(1f, dist / 64f));
        int r = (int) (255 * (1 - f));
        int g = (int) (60 + 195 * f);
        return ColorUtils.rgba(r, g, 60, 255);
    }

    private int healthColor(LivingEntity living) {
        float max = Math.max(1f, living.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, living.getHealth() / max));
        int r = (int) (255 * (1 - ratio));
        int g = (int) (255 * ratio);
        return ColorUtils.rgba(r, g, 40, 255);
    }

    // ---------------------------------------------------------------------
    //  Rendering
    // ---------------------------------------------------------------------
    private void renderEntity(DrawContext context, Entity entity, int col, float tickDelta,
                              boolean twoD, boolean lines, boolean sides) {
        Box box = entity.getBoundingBox();
        Vec3d lerp = ProjectionUtil.lerpPos(entity, tickDelta);
        double ox = lerp.x - entity.getX(), oy = lerp.y - entity.getY(), oz = lerp.z - entity.getZ();

        double x0 = box.minX + ox, y0 = box.minY + oy, z0 = box.minZ + oz;
        double x1 = box.maxX + ox, y1 = box.maxY + oy, z1 = box.maxZ + oz;

        int[][] c = {
                ProjectionUtil.project(new Vec3d(x0, y0, z0), tickDelta),
                ProjectionUtil.project(new Vec3d(x1, y0, z0), tickDelta),
                ProjectionUtil.project(new Vec3d(x1, y0, z1), tickDelta),
                ProjectionUtil.project(new Vec3d(x0, y0, z1), tickDelta),
                ProjectionUtil.project(new Vec3d(x0, y1, z0), tickDelta),
                ProjectionUtil.project(new Vec3d(x1, y1, z0), tickDelta),
                ProjectionUtil.project(new Vec3d(x1, y1, z1), tickDelta),
                ProjectionUtil.project(new Vec3d(x0, y1, z1), tickDelta)
        };

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (int[] q : c) {
            if (q == null) continue;
            any = true;
            minX = Math.min(minX, q[0]);
            minY = Math.min(minY, q[1]);
            maxX = Math.max(maxX, q[0]);
            maxY = Math.max(maxY, q[1]);
        }
        if (!any) return;

        int fill = withAlpha(col, (int) (fillOpacity.getValue() * 255));

        // 2D mode: a single screen-space rectangle.
        if (twoD) {
            if (sides) context.fill(minX, minY, maxX, maxY, fill);
            if (lines) RenderUtils.drawBorder(context, minX, minY, maxX, maxY, lineWidth(), col);
            return;
        }

        if (sides) {
            context.fill(minX, minY, maxX, maxY, fill);
        }
        if (lines) {
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            int w = lineWidth();
            for (int[] e : edges) {
                int[] a = c[e[0]], b = c[e[1]];
                if (a == null || b == null) continue;
                RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], col, w);
            }
        }
    }

    private int lineWidth() {
        // Meteor's outline-width only exists for the Shader mode.
        if (mode.is("Shader")) return Math.max(1, outlineWidth.getValue().intValue());
        return 1;
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        if (a == 0) a = 1;
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
