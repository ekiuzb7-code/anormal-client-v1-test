package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XRay extends Module {
    public final NumberSetting opacity = new NumberSetting("Opacity", "How transparent unwanted blocks are", 20.0, 0.0, 100.0, 5.0);
    public final BooleanSetting caveMode = new BooleanSetting("Cave Mode", "Only mark blocks exposed to air", false);
    public final ModeSetting preset = new ModeSetting("Preset", "Block list preset", "Ores", "Ores", "Valuables", "Storage", "Clear");
    public final NumberSetting markerRange = new NumberSetting("Marker Range", "Marker scan radius", 24.0, 4.0, 48.0, 1.0);
    public final BooleanSetting markers = new BooleanSetting("Markers", "Draw boxes on listed blocks", true);

    private final Set<Block> selectedBlocks = new HashSet<>();
    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;
    private String appliedPreset = "";

    public XRay() {
        super("XRay", "Highlights listed blocks through walls", Category.UZNY11);
        addSetting(opacity);
        addSetting(caveMode);
        addSetting(preset);
        addSetting(markerRange);
        addSetting(markers);
        applyPreset();
    }

    private void applyPreset() {
        if (appliedPreset.equals(preset.getValue())) return;
        appliedPreset = preset.getValue();
        selectedBlocks.clear();
        if (preset.is("Ores")) {
            Block[] ores = {Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS,
                    Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
                    Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.EMERALD_ORE,
                    Blocks.DEEPSLATE_EMERALD_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                    Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.COAL_ORE,
                    Blocks.DEEPSLATE_COAL_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.NETHER_QUARTZ_ORE};
            for (Block b : ores) selectedBlocks.add(b);
        } else if (preset.is("Valuables")) {
            Block[] vals = {Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS,
                    Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.CHEST, Blocks.ENDER_CHEST,
                    Blocks.SPAWNER, Blocks.BEACON};
            for (Block b : vals) selectedBlocks.add(b);
        } else if (preset.is("Storage")) {
            Block[] st = {Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL,
                    Blocks.HOPPER, Blocks.FURNACE, Blocks.DISPENSER, Blocks.DROPPER};
            for (Block b : st) selectedBlocks.add(b);
        }
        if (isEnabled() && mc.worldRenderer != null) {
            try {
                mc.worldRenderer.reload();
            } catch (Throwable ignored) {}
        }
    }

    public boolean isVisibleBlock(Block block) {
        return selectedBlocks.contains(block);
    }

    @Override
    public void onTick() {
        applyPreset();
        if (!markers.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 12) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = markerRange.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < 128; x++)
                for (int y = -r; y <= r && cache.size() < 128; y++)
                    for (int z = -r; z <= r && cache.size() < 128; z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            if (!selectedBlocks.contains(mc.world.getBlockState(p).getBlock())) continue;
                            if (caveMode.isEnabled() && !exposed(p)) continue;
                        } catch (Throwable t) {
                            continue;
                        }
                        cache.add(p.toImmutable());
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!markers.isEnabled() || mc.player == null || cache.isEmpty()) return;
        int col = 0xFFFF8800;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        for (BlockPos p : cache) {
            int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (s == null) continue;
            if (s[0] < -20 || s[0] > sw + 20 || s[1] < -20 || s[1] > sh + 20) continue;
            double dist = Math.sqrt(mc.player.getEyePos().squaredDistanceTo(
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)));
            int half = Math.max(2, Math.min(6, (int) (60.0 / Math.max(1.0, dist))));
            context.fill(s[0] - half, s[1] - half, s[0] + half, s[1] + half, (col & 0x00FFFFFF) | 0x55000000);
            context.fill(s[0] - half, s[1] - half, s[0] + half, s[1] - half + 1, col);
            context.fill(s[0] - half, s[1] + half - 1, s[0] + half, s[1] + half, col);
            context.fill(s[0] - half, s[1] - half, s[0] - half + 1, s[1] + half, col);
            context.fill(s[0] + half - 1, s[1] - half, s[0] + half, s[1] + half, col);
        }
    }

    private boolean exposed(BlockPos p) {
        try {
            for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
                try {
                    if (mc.world.getBlockState(p.offset(d)).isAir()) return true;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
