package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

/**
 * Draws wireframe outlines around specified target blocks through walls.
 *
 * <p>Migrated to budgeted {@link BlockScanner}: no full-sphere freeze,
 * persistent cache with re-validation (no blink/ghosts), 3-axis recentre
 * and independent vertical range for deep coverage.</p>
 */
public class Search extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 24.0, 4.0, 48.0, 1.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 24.0, 4.0, 80.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 4000.0, 1000.0, 15000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final BooleanSetting onlyCaves = new BooleanSetting("Only Caves", "Only highlight blocks exposed to air", true);
    public final ModeSetting searchBlocks = new ModeSetting("Search Blocks", "Which blocks to highlight", "Ores", "Ores", "Valuables", "Storage", "Spawners");
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines from camera to highlighted blocks", false);
    public final ColorSetting color = new ColorSetting("Color", "Highlight color", ColorUtils.rgba(255, 140, 0, 255));

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockScanner scanner = new BlockScanner();
    private final Set<BlockPos> cache = new HashSet<>();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;

    public Search() {
        super("Search", "Draws wireframe outlines around specified target blocks through walls", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(onlyCaves);
        addSetting(searchBlocks);
        addSetting(tracers);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        cache.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
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

    private boolean isWanted(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return matches(path);
    }

    private boolean exposed(BlockPos p) {
        try {
            for (Direction d : Direction.values()) {
                try {
                    if (mc.world.getBlockState(p.offset(d)).isAir()) return true;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        if (centreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - centreX) > recentreDistance.getValue()
                || Math.abs(origin.getY() - centreY) > recentreDistance.getValue()
                || Math.abs(origin.getZ() - centreZ) > recentreDistance.getValue()) {
            centreX = origin.getX();
            centreY = origin.getY();
            centreZ = origin.getZ();
            scanner.resetCylinder();
            prune(origin, r, vr);
        }

        cache.removeIf(pos -> {
            if (!mc.world.isChunkLoaded(pos)) return false;
            return !isWanted(mc.world.getBlockState(pos));
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr,
                    scanSpeed.getValue().intValue(), scratch,
                    state -> !state.isAir() && isWanted(state),
                    (pos, state) -> {
                        if (cache.size() >= 128) return;
                        BlockPos imm = pos.toImmutable();
                        if (onlyCaves.isEnabled() && !exposed(imm)) return;
                        cache.add(imm);
                    });
        } catch (Throwable ignored) {
        }
    }

    private void prune(BlockPos origin, int range, int verticalRange) {
        int rr = range * range;
        cache.removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > verticalRange + 2;
        });
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || cache.isEmpty()) return;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int c = color.getValue();
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        for (BlockPos p : cache) {
            if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

            int[] s = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (s == null) continue;
            drawn++;

            context.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] + 3, (c & 0x00FFFFFF) | 0x55000000);
            context.fill(s[0] - 3, s[1] - 3, s[0] + 3, s[1] - 2, c);
            context.fill(s[0] - 3, s[1] + 2, s[0] + 3, s[1] + 3, c);
            context.fill(s[0] - 3, s[1] - 2, s[0] - 2, s[1] + 2, c);
            context.fill(s[0] + 2, s[1] - 2, s[0] + 3, s[1] + 2, c);
            if (tracers.isEnabled()) line(context, w / 2, h / 2, s[0], s[1], c);
        }
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
