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
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class BannerESP extends Module {
    public final ModeSetting colorFilter = new ModeSetting("Color Filter", "Only banners of color", "All",
            "All", "White", "Red", "Blue", "Green", "Yellow", "Black", "Other");
    public final BooleanSetting outline = new BooleanSetting("Outline", "Box outline", true);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw box marker", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final NumberSetting range = new NumberSetting("Range", "Scan radius", 32.0, 8.0, 64.0, 4.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max banners marked", 20.0, 5.0, 60.0, 5.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 255, 255, 255));

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public BannerESP() {
        super("BannerESP", "Marks banners with color filter", Category.RENDER);
        addSetting(colorFilter);
        addSetting(outline);
        addSetting(box);
        addSetting(tracer);
        addSetting(showDistance);
        addSetting(range);
        addSetting(maxShown);
        addSetting(color);
    }

    private boolean wanted(String path) {
        if (!path.contains("banner")) return false;
        if (colorFilter.is("All")) return true;
        String f = colorFilter.getValue().toLowerCase();
        if (colorFilter.is("Other")) {
            return !path.contains("white") && !path.contains("red") && !path.contains("blue")
                    && !path.contains("green") && !path.contains("yellow") && !path.contains("black");
        }
        return path.contains(f);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 12) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < maxShown.getValue().intValue(); x++)
                for (int y = -r; y <= r && cache.size() < maxShown.getValue().intValue(); y++)
                    for (int z = -r; z <= r && cache.size() < maxShown.getValue().intValue(); z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (wanted(path)) cache.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;
        int col = color.getValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        for (BlockPos p : cache) {
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            if (box.isEnabled()) {
                context.fill(sc[0] - 4, sc[1] - 6, sc[0] + 4, sc[1] + 6, (col & 0x00FFFFFF) | 0x55000000);
                if (outline.isEnabled()) {
                    context.fill(sc[0] - 4, sc[1] - 6, sc[0] + 4, sc[1] - 5, col);
                    context.fill(sc[0] - 4, sc[1] + 5, sc[0] + 4, sc[1] + 6, col);
                }
            } else {
                context.fill(sc[0] - 2, sc[1] - 2, sc[0] + 2, sc[1] + 2, col);
            }
            if (tracer.isEnabled()) {
                int steps = Math.min(200, Math.max(Math.abs(sc[0] - w / 2), Math.abs(sc[1] - h / 2)));
                for (int i = 0; i <= steps; i++) {
                    int x = w / 2 + (sc[0] - w / 2) * i / Math.max(1, steps);
                    int y = h / 2 + (sc[1] - h / 2) * i / Math.max(1, steps);
                    context.fill(x, y, x + 1, y + 1, col);
                }
            }
            if (showDistance.isEnabled() && mc.textRenderer != null) {
                double dx = p.getX() - mc.player.getX(), dy = p.getY() - mc.player.getY(), dz = p.getZ() - mc.player.getZ();
                String d = "" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
                RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + 6, sc[1] - 4, 0xFFAAAAAA, true);
            }
        }
    }
}
