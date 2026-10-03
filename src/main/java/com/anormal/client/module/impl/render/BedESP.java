package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class BedESP extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 48.0, 16.0, 96.0, 8.0);
    public final ColorSetting color = new ColorSetting("Color", "Bed marker color", ColorUtils.rgba(255, 60, 60, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max bed markers", 32.0, 8.0, 64.0, 1.0);

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public BedESP() {
        super("BedESP", "Highlights bed blocks through walls", Category.RENDER);
        addSetting(range);
        addSetting(color);
        addSetting(maxShown);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 20) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int cap = maxShown.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < cap; x++)
                for (int y = -r; y <= r && cache.size() < cap; y++)
                    for (int z = -r; z <= r && cache.size() < cap; z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (path.endsWith("_bed")) cache.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;
        try {
            int c = color.getValue();
            for (BlockPos p : cache) {
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                if (sc == null) continue;
                context.fill(sc[0] - 5, sc[1] - 3, sc[0] + 5, sc[1] + 3, (c & 0x00FFFFFF) | 0x66000000);
                context.fill(sc[0] - 5, sc[1] - 3, sc[0] + 5, sc[1] - 2, c);
                context.fill(sc[0] - 5, sc[1] + 2, sc[0] + 5, sc[1] + 3, c);
                context.fill(sc[0] - 5, sc[1] - 2, sc[0] - 4, sc[1] + 2, c);
                context.fill(sc[0] + 4, sc[1] - 2, sc[0] + 5, sc[1] + 2, c);
            }
        } catch (Throwable ignored) {}
    }


}
