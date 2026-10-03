package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

public class BlockOverlay extends Module {
    public final ColorSetting outlineColor = new ColorSetting("Outline Color", "Targeted block outline color", ColorUtils.rgba(255, 120, 0, 255));
    public final ColorSetting fillColor = new ColorSetting("Fill Color", "Targeted block face fill color", ColorUtils.rgba(255, 120, 0, 50));

    public BlockOverlay() {
        super("BlockOverlay", "Customizes block highlight colors when targeted", Category.UZNY11);
        addSetting(outlineColor);
        addSetting(fillColor);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
        try {
            Vec3d center = new Vec3d(
                    bhr.getBlockPos().getX() + 0.5,
                    bhr.getBlockPos().getY() + 0.5,
                    bhr.getBlockPos().getZ() + 0.5);
            int[] s = com.anormal.client.util.ProjectionUtil.project(center, tickDelta);
            if (s == null) return;
            double dist = Math.sqrt(mc.player.getEyePos().squaredDistanceTo(center));
            if (dist < 0.5) return;
            int half = Math.max(6, (int) (s[2] / Math.max(0.5, dist) / 2.0));
            int outline = outlineColor.getValue();
            int fill = fillColor.getValue();
            context.fill(s[0] - half, s[1] - half, s[0] + half, s[1] + half, fill);
            context.fill(s[0] - half, s[1] - half, s[0] + half, s[1] - half + 1, outline);
            context.fill(s[0] - half, s[1] + half - 1, s[0] + half, s[1] + half, outline);
            context.fill(s[0] - half, s[1] - half, s[0] - half + 1, s[1] + half, outline);
            context.fill(s[0] + half - 1, s[1] - half, s[0] + half, s[1] + half, outline);
        } catch (Throwable ignored) {}
    }
}
