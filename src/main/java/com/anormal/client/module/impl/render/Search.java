package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Search extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Highlight blocks within this range", 24.0, 4.0, 48.0, 1.0);
    public final BooleanSetting onlyCaves = new BooleanSetting("Only Caves", "Only highlight blocks exposed to air", true);
    public final ModeSetting searchBlocks = new ModeSetting("Search Blocks", "Which blocks to highlight", "Ores", "Ores", "Valuables", "Storage", "Spawners");
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines from camera to highlighted blocks", false);
    public final ColorSetting color = new ColorSetting("Color", "Highlight color", ColorUtils.rgba(255, 140, 0, 255));

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public Search() {
        super("Search", "Draws wireframe outlines around specified target blocks through walls", Category.RENDER);
        addSetting(range);
        addSetting(onlyCaves);
        addSetting(searchBlocks);
        addSetting(tracers);
        addSetting(color);
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
            for (int x = -r; x <= r && cache.size() < 128; x++)
                for (int y = -r; y <= r && cache.size() < 128; y++)
                    for (int z = -r; z <= r && cache.size() < 128; z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        String path;
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                        } catch (Throwable t) {
                            continue;
                        }
                        if (!matches(path)) continue;
                        if (onlyCaves.isEnabled() && !exposed(p)) continue;
                        cache.add(p.toImmutable());
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || cache.isEmpty()) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int c = color.getValue();
        for (BlockPos p : cache) {
            int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (s == null) continue;
            context.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, (c & 0x00FFFFFF) | 0x55000000);
            context.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] - 2, c);
            context.fill(s[0] - 3, s[1] + 2, s[0] + 3, s[1] + 3, c);
            context.fill(s[0] - 3, s[1] - 2, s[0] - 2, s[1] + 2, c);
            context.fill(s[0] + 2, s[1] - 2, s[0] + 3, s[1] + 2, c);
            if (tracers.isEnabled()) line(context, w / 2, h / 2, s[0], s[1], c);
        }
    }

    private boolean matches(String path) {
        if (searchBlocks.is("Ores")) return path.contains("_ore") || path.contains("ancient_debris");
        if (searchBlocks.is("Valuables"))
            return path.contains("diamond") || path.contains("emerald") || path.contains("gold")
                    || path.contains("netherite") || path.contains("ancient_debris") || path.contains("beacon");
        if (searchBlocks.is("Storage"))
            return path.contains("chest") || path.contains("barrel") || path.contains("shulker")
                    || path.contains("hopper") || path.contains("furnace") || path.contains("dispenser") || path.contains("dropper");
        return path.contains("spawner");
    }

    private boolean exposed(BlockPos p) {
        try {
            for (Direction d : Direction.values()) {
                try {
                    if (mc.world.getBlockState(p.offset(d)).isAir()) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }



    private void line(DrawContext context, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.min(200, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        if (steps == 0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, col);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, col);
        }
    }
}
