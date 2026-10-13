package com.anormal.client.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Budgeted spherical block scanner shared by every block-finding module.
 *
 * <p>Scanning a sphere of radius {@code r} costs {@code (2r+1)^3} world lookups.
 * Doing that in a single tick is what froze the client (an r=48 sphere is
 * ~900 000 blocks). This scanner keeps a persistent cursor over a precomputed
 * offset list, sorted nearest-first, and processes at most {@code budget}
 * blocks per call. Cost per tick is therefore constant regardless of radius.</p>
 *
 * <p>Offsets are ordered by distance from the centre so that, when a budget cut
 * happens mid-sweep, the blocks closest to the player are already known.</p>
 */
public final class BlockScanner {

    public interface Callback {
        /** Called for each scanned block. Return value is ignored. */
        void accept(BlockPos.Mutable pos, BlockState state);

        /** Called when the cursor wrapped around and a full sweep finished. */
        default void onSweepComplete() {}
    }

    private int[] ox = new int[0];
    private int[] oy = new int[0];
    private int[] oz = new int[0];
    private int cachedRxz = -1;
    private int cachedRy = -1;
    private int cursor = 0;

    /** Clears the cursor so the next scan starts a fresh sweep. */
    public void reset() {
        cursor = 0;
    }

    public int size() {
        return ox.length;
    }

    private void ensure(int rxz, int ry) {
        if (rxz == cachedRxz && ry == cachedRy) return;
        cachedRxz = rxz;
        cachedRy = ry;
        List<int[]> list = new ArrayList<>();
        int rrz = rxz * rxz;
        for (int x = -rxz; x <= rxz; x++) {
            for (int z = -rxz; z <= rxz; z++) {
                int hz = x * x + z * z;
                if (hz > rrz) continue;
                for (int y = -ry; y <= ry; y++) {
                    list.add(new int[]{x, y, z, hz + y * y});
                }
            }
        }
        // Nearest-first: closest blocks are found before the budget runs out.
        list.sort(Comparator.comparingInt(a -> a[3]));
        ox = new int[list.size()];
        oy = new int[list.size()];
        oz = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ox[i] = list.get(i)[0];
            oy[i] = list.get(i)[1];
            oz[i] = list.get(i)[2];
        }
        cursor = 0;
    }

    /** Cubic/spherical scan (same radius on every axis). */
    public void scan(World world, int cx, int cy, int cz, int radius, int budget,
                     BlockPos.Mutable scratch, BlockFilter filter, Callback cb) {
        scan(world, cx, cy, cz, radius, radius, budget, scratch, filter, cb);
    }

    /**
     * Processes up to {@code budget} blocks of the ellipsoid centred on
     * {@code (cx, cy, cz)} with horizontal radius {@code rxz} and vertical
     * radius {@code ry}.
     *
     * @param filter when non-null, only blocks passing this test are handed to
     *               the callback (use it to skip air cheaply).
     */
    public void scan(World world, int cx, int cy, int cz, int rxz, int ry, int budget,
                     BlockPos.Mutable scratch, BlockFilter filter, Callback cb) {
        ensure(rxz, ry);
        int n = ox.length;
        if (n == 0) return;

        int done = 0;
        while (done < budget) {
            if (cursor >= n) {
                cursor = 0;
                try {
                    cb.onSweepComplete();
                } catch (Throwable ignored) {
                }
            }
            int step = Math.min(budget - done, n - cursor);
            for (int i = 0; i < step; i++, cursor++) {
                scratch.set(cx + ox[cursor], cy + oy[cursor], cz + oz[cursor]);
                if (!world.isChunkLoaded(scratch)) continue;
                BlockState state = world.getBlockState(scratch);
                if (filter != null && !filter.test(state)) continue;
                cb.accept(scratch, state);
            }
            done += step;
        }
    }

    public interface BlockFilter {
        boolean test(BlockState state);
    }
}
