package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Draws a real 3D outline (and optional fill) around the block you are looking
 * at.
 *
 * <p><b>What was wrong:</b> the old version projected only the block <i>centre</i>
 * and then drew a square whose size was guessed from the distance
 * ({@code 70 / dist}). That is not the block at all - it was a flat grey
 * rectangle floating in mid-air, which is exactly what it looked like in
 * water. It is now a proper world-space wireframe built from the block's eight
 * corners, so it hugs the real block at any angle.</p>
 */
public class BlockOverlay extends Module {
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw the block outline", true);
    public final ColorSetting outlineColor = new ColorSetting("Outline Color", "Targeted block outline color", ColorUtils.rgba(255, 120, 0, 255));
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);

    public final BooleanSetting fill = new BooleanSetting("Fill", "Translucent fill covering the targeted block", true);
    public final ColorSetting fillColor = new ColorSetting("Fill Color", "Targeted block fill color", ColorUtils.rgba(255, 120, 0, 45));
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Opacity of the fill", 45.0, 0.0, 255.0, 5.0);

    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Hide the overlay past this distance", 24.0, 4.0, 64.0, 2.0);

    public BlockOverlay() {
        super("BlockOverlay", "Customizes the highlight of the block you are looking at", Category.RENDER);
        addSetting(outline);
        addSetting(outlineColor);
        addSetting(outlineWidth);
        addSetting(fill);
        addSetting(fillColor);
        addSetting(fillOpacity);
        addSetting(maxDistance);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;

        try {
            var pos = bhr.getBlockPos();
            if (mc.world.getBlockState(pos).isAir()) return;

            Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3d eye = ProjectionUtil.eye(tickDelta);
            if (Math.sqrt(eye.squaredDistanceTo(center)) > maxDistance.getValue()) return;

            if (fill.isEnabled()) {
                int fc = fillColor.getValue();
                int alpha = fillOpacity.getValue().intValue();
                if (alpha <= 0) alpha = (fc >>> 24) & 0xFF;
                RenderUtils.drawBlockFill(context, pos, fc, tickDelta, alpha);
            }
            if (outline.isEnabled()) {
                RenderUtils.drawBlockOutline(context, pos, outlineColor.getValue(), tickDelta,
                        outlineWidth.getValue().intValue());
            }
        } catch (Throwable ignored) {
        }
    }
}
