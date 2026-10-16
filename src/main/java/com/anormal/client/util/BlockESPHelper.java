package com.anormal.client.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared scanning engine for the simple "find block X" ESP modules.
 *
 * <p>Every one of them used to sweep a full sphere in a single tick and then
 * clear its result set on the next sweep. At a radius of 48 that is roughly
 * <b>463 000</b> world lookups landing in one frame, and the clear meant every
 * marker blinked out and back on a timer. Both are fixed here:</p>
 *
 * <ul>
 *   <li>The sweep runs through {@link BlockScanner#scanCylinder}, so the cost
 *       per tick is a fixed budget no matter how big the radius is.</li>
 *   <li>Results are kept between sweeps and re-validated every tick, so a
 *       marker only disappears once the block is genuinely gone.</li>
 *   <li>Horizontal and vertical range are separate, and the centre follows the
 *       player on all three axes - which is what lets these modules see deep
 *       underground.</li>
 * </ul>
 */
public final class BlockESPHelper {

    /** Maps a block state to a marker colour, or {@code -1} when not wanted. */
    public interface Matcher {
        int color(BlockState state);
    }

    private final Map<BlockPos, Integer> found = new HashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;

    public Map<BlockPos, Integer> found() {
        return found;
    }

    public void reset() {
        found.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
    }

    /**
     * Runs one budgeted slice of the sweep.
     *
     * @param range        horizontal radius
     * @param verticalRange how far up and down to search
     * @param speed        blocks to check this tick
     * @param recentre     how far the player may move before the sweep restarts
     */
    public void tick(MinecraftClient mc, int range, int verticalRange, int speed,
                     double recentre, Matcher matcher) {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();

        if (centreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - centreX) > recentre
                || Math.abs(origin.getY() - centreY) > recentre
                || Math.abs(origin.getZ() - centreZ) > recentre) {
            centreX = origin.getX();
            centreY = origin.getY();
            centreZ = origin.getZ();
            scanner.resetCylinder();
            prune(origin, range, verticalRange);
        }

        // Re-validate what we already show so removed blocks vanish at once.
        found.entrySet().removeIf(e -> {
            if (!mc.world.isChunkLoaded(e.getKey())) return false;
            BlockState state = mc.world.getBlockState(e.getKey());
            if (state.isAir()) return true;
            int col = matcher.color(state);
            return col == -1 || col != e.getValue();
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, range, verticalRange, speed,
                    scratch,
                    state -> !state.isAir() && matcher.color(state) != -1,
                    (pos, state) -> {
                        int col = matcher.color(state);
                        if (col != -1) found.put(pos.toImmutable(), col);
                    });
        } catch (Throwable ignored) {
        }
    }

    private void prune(BlockPos origin, int range, int verticalRange) {
        int rr = range * range;
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > verticalRange + 2;
        });
    }

    /** Convenience: true when the block's registry path ends with {@code suffix}. */
    public static boolean pathEndsWith(BlockState state, String suffix) {
        try {
            return net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath().endsWith(suffix);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Convenience: true when the block's registry path contains {@code part}. */
    public static boolean pathContains(BlockState state, String part) {
        try {
            return net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath().contains(part);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAir(BlockState state) {
        return state.isAir() || state.isOf(Blocks.VOID_AIR);
    }
}
