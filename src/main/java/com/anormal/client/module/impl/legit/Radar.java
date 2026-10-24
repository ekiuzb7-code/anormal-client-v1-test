package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/**
 * Circular minimap of the entities around you.
 *
 * <p><b>Orientation.</b> Minecraft yaw 0 faces +Z, and yaw increases clockwise seen from above.
 * The world-space basis used here is therefore {@code forward = (-sin yaw, cos yaw)} and
 * {@code right = (-cos yaw, -sin yaw)}; the forward component is subtracted from the screen Y
 * because screen space grows downwards. Getting either sign wrong mirrors the whole map, which is
 * exactly what "forward looks like backwards" means.</p>
 *
 * <p>Height is encoded as brightness (brighter above, dimmer below), which is what matters when
 * someone is on a ledge. Entities are distance-filtered before any drawing, so a busy lobby costs
 * almost nothing.</p>
 */
public class Radar extends Module {

    public final NumberSetting posX = new NumberSetting("Pos X", "X position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y position on screen", 50.0, 0.0, 1080.0, 1.0);
    public final NumberSetting size = new NumberSetting("Size", "Diameter of the radar", 96.0, 60.0, 220.0, 2.0);
    public final NumberSetting range = new NumberSetting("Range", "Blocks from the centre to the edge", 48.0, 16.0, 160.0, 4.0);
    public final NumberSetting dotSize = new NumberSetting("Dot Size", "Base dot radius in pixels", 2.0, 1.0, 5.0, 1.0);

    public final BooleanSetting showPlayers = new BooleanSetting("Show Players", "Draw other players", true);
    public final BooleanSetting showMobs = new BooleanSetting("Show Mobs", "Draw hostile and passive mobs", true);
    public final BooleanSetting showItems = new BooleanSetting("Show Items", "Draw dropped items", false);
    public final BooleanSetting showSelf = new BooleanSetting("Show Self", "Draw the centre marker", true);
    public final BooleanSetting showNames = new BooleanSetting("Show Names", "Write the name next to player dots", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Append the distance to each name", false);
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Turn with the player (up = forward)", true);
    public final BooleanSetting showRings = new BooleanSetting("Range Rings", "Draw a half-range ring", true);
    public final BooleanSetting showNorth = new BooleanSetting("North Marker", "Draw the N marker while rotating", true);
    public final BooleanSetting background = new BooleanSetting("Background", "Dark disc behind the dots", true);

    private static final int DOT_PLAYER = 0xFFFF4D4D;
    private static final int DOT_MOB = 0xFFFFA033;
    private static final int DOT_ITEM = 0xFFFFE066;
    private static final int DOT_OTHER = 0xFF9AA0A6;
    private static final int SELF = 0xFFFFFFFF;

    public Radar() {
        super("Radar", "Circular minimap showing nearby players and mobs", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(size);
        addSetting(range);
        addSetting(dotSize);
        addSetting(showPlayers);
        addSetting(showMobs);
        addSetting(showItems);
        addSetting(showSelf);
        addSetting(showNames);
        addSetting(showDistance);
        addSetting(rotate);
        addSetting(showRings);
        addSetting(showNorth);
        addSetting(background);
    }

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        int radius = size.getValue().intValue() / 2;
        int cx = posX.getValue().intValue() + radius;
        int cy = posY.getValue().intValue() + radius;

        double blocks = range.getValue();
        double scale = radius / blocks;

        if (background.isEnabled()) {
            fillDisc(ctx, cx, cy, radius, 0xB0101014);
        }
        drawRing(ctx, cx, cy, radius, 0xFF3A3A42);

        if (showRings.isEnabled()) {
            drawRing(ctx, cx, cy, radius / 2, 0x33FFFFFF);
        }

        double yawRad = rotate.isEnabled() ? Math.toRadians(mc.player.getYaw()) : 0.0;
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        // World-space basis for this yaw (see the class comment for the derivation).
        double fwdX = -sin, fwdZ = cos;
        double rightX = -cos, rightZ = -sin;

        if (showSelf.isEnabled()) {
            // Forward arrow: always points up because the map is player-relative.
            ctx.fill(cx - 1, cy - 7, cx + 1, cy - 1, SELF);
            ctx.fill(cx - 2, cy - 3, cx + 2, cy - 1, SELF);
            ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, SELF);
        }

        double maxSq = blocks * blocks;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        int drawn = 0;
        int baseDot = dotSize.getValue().intValue();

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !e.isAlive()) continue;

            double dx = e.getX() - px;
            double dz = e.getZ() - pz;
            double distSq = dx * dx + dz * dz;
            if (distSq > maxSq) continue;

            int color;
            int dotRadius;
            String label = null;

            if (e instanceof PlayerEntity player) {
                if (!showPlayers.isEnabled()) continue;
                color = DOT_PLAYER;
                dotRadius = baseDot;
                if (showNames.isEnabled()) {
                    label = player.getName().getString();
                    if (showDistance.isEnabled()) {
                        label = label + " " + (int) Math.sqrt(distSq) + "m";
                    }
                }
            } else if (e instanceof Monster) {
                if (!showMobs.isEnabled()) continue;
                color = DOT_MOB;
                dotRadius = Math.max(1, baseDot - 1);
            } else if (e instanceof ItemEntity) {
                if (!showItems.isEnabled()) continue;
                color = DOT_ITEM;
                dotRadius = 1;
            } else if (e instanceof LivingEntity) {
                if (!showMobs.isEnabled()) continue;
                color = DOT_OTHER;
                dotRadius = Math.max(1, baseDot - 1);
            } else {
                continue;
            }

            // Project onto the player-relative basis. Forward maps to screen UP, right to
            // screen RIGHT - both signs matter, a single flip mirrors the whole radar.
            double relRight = dx * rightX + dz * rightZ;
            double relForward = dx * fwdX + dz * fwdZ;

            int dotX = (int) Math.round(cx + relRight * scale);
            int dotY = (int) Math.round(cy - relForward * scale);

            // Clip to the disc so dots never paint outside the border.
            int ox = dotX - cx;
            int oy = dotY - cy;
            if (ox * ox + oy * oy > radius * radius - 1) continue;

            int shaded = shade(color, e.getY() - py);
            ctx.fill(dotX - dotRadius, dotY - dotRadius,
                    dotX + dotRadius + 1, dotY + dotRadius + 1, shaded);

            if (label != null && !label.isEmpty()) {
                int textWidth = mc.textRenderer.getWidth(label);
                int textX = dotX + dotRadius + 2;
                // Keep the text inside the disc instead of letting it run off the edge.
                if (textX + textWidth > cx + radius - 2) {
                    textX = dotX - dotRadius - 2 - textWidth;
                }
                ctx.drawText(mc.textRenderer, Text.literal(label), textX, dotY - 4, shaded, true);
            }

            // Cap the work per frame: a packed lobby should not turn the HUD into a particle sim.
            if (++drawn >= 128) break;
        }

        if (showNorth.isEnabled() && rotate.isEnabled()) {
            // North is -Z in world space; project it onto the rotated basis.
            double nRight = -rightZ;
            double nForward = -fwdZ;
            int nx = (int) Math.round(cx + nRight * (radius - 9));
            int ny = (int) Math.round(cy - nForward * (radius - 9));
            ctx.drawText(mc.textRenderer, "N", nx - 3, ny - 4, 0xFF8A8A96, false);
        }
    }

    /** Height shading: brighter above, dimmer below. */
    private static int shade(int argb, double deltaY) {
        int alpha = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        double factor = deltaY > 2.0 ? 1.0 : (deltaY < -2.0 ? 0.45 : 0.75);

        r = (int) Math.min(255, r * factor + 40);
        g = (int) Math.min(255, g * factor + 40);
        b = (int) Math.min(255, b * factor + 40);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    /** Filled disc built from horizontal spans - cheap at radar sizes. */
    private static void fillDisc(DrawContext ctx, int cx, int cy, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int span = (int) Math.sqrt(Math.max(0, radius * radius - y * y));
            ctx.fill(cx - span, cy + y, cx + span, cy + y + 1, color);
        }
    }

    /** 1px ring outline. */
    private static void drawRing(DrawContext ctx, int cx, int cy, int radius, int color) {
        int steps = Math.max(48, radius * 4);
        for (int i = 0; i < steps; i++) {
            double angle = (Math.PI * 2 * i) / steps;
            int x = (int) Math.round(cx + Math.cos(angle) * radius);
            int y = (int) Math.round(cy + Math.sin(angle) * radius);
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }
}
