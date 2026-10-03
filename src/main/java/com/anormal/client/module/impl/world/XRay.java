package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XRay extends Module {
    public final BooleanSetting realXray = new BooleanSetting("Real XRay", "Hide non-selected blocks via mixin", true);
    public final BooleanSetting markers = new BooleanSetting("Markers", "Draw boxes on selected blocks through walls", true);
    public final NumberSetting opacity = new NumberSetting("Opacity", "How transparent unwanted blocks are (0 = see through)", 20.0, 0.0, 100.0, 5.0);
    public final BooleanSetting caveMode = new BooleanSetting("Cave Mode", "Only mark blocks exposed to air", false);
    public final NumberSetting markerRange = new NumberSetting("Marker Range", "Marker scan radius", 24.0, 4.0, 48.0, 1.0);
    private final Set<Block> selectedBlocks = new HashSet<>();

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public XRay() {
        super("XRay", "Real mode hides blocks via mixin, Markers draws boxes through walls", Category.WORLD);
        addSetting(realXray);
        addSetting(markers);
        addSetting(opacity);
        addSetting(caveMode);
        addSetting(markerRange);
        selectAllOres();
    }

    public void selectAllOres() {
        selectedBlocks.clear();
        selectedBlocks.add(Blocks.DIAMOND_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        selectedBlocks.add(Blocks.ANCIENT_DEBRIS);
        selectedBlocks.add(Blocks.GOLD_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_GOLD_ORE);
        selectedBlocks.add(Blocks.NETHER_GOLD_ORE);
        selectedBlocks.add(Blocks.IRON_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_IRON_ORE);
        selectedBlocks.add(Blocks.EMERALD_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_EMERALD_ORE);
        selectedBlocks.add(Blocks.LAPIS_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_LAPIS_ORE);
        selectedBlocks.add(Blocks.REDSTONE_ORE);
        selectedBlocks.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        selectedBlocks.add(Blocks.CHEST);
        selectedBlocks.add(Blocks.TRAPPED_CHEST);
        selectedBlocks.add(Blocks.BARREL);
        selectedBlocks.add(Blocks.SPAWNER);
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public void clearSelection() {
        selectedBlocks.clear();
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public void toggleBlock(Block block) {
        if (selectedBlocks.contains(block)) {
            selectedBlocks.remove(block);
        } else {
            selectedBlocks.add(block);
        }
        if (isEnabled() && mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    public boolean isBlockSelected(Block block) {
        return selectedBlocks.contains(block);
    }

    public Set<Block> getSelectedBlocks() {
        return selectedBlocks;
    }

    public boolean isVisibleBlock(Block block) {
        return selectedBlocks.contains(block);
    }

    public boolean isRealMode() {
        return realXray.isEnabled();
    }

    public boolean markersEnabled() {
        return markers.isEnabled();
    }

    @Override
    public void onTick() {
        if (!markersEnabled()) return;
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
        if (!markersEnabled() || mc.player == null || cache.isEmpty()) return;
        int col = 0xFFFF8800;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        for (BlockPos p : cache) {
            int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (s == null) continue;
            // Cull off-screen and anchor size to depth so markers track blocks
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

    @Override
    public void onEnable() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }
}
