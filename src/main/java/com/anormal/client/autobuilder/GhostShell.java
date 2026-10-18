package com.anormal.client.autobuilder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Flat, render friendly copy of a schematic's <em>visible surface</em>.
 *
 * <p>Why this exists: the world ghost and the GUI preview used to iterate the schematic's
 * {@code HashMap<BlockPos, BlockState>} on every frame (allocating a {@code BlockPos} per block and
 * drawing blocks in arbitrary hash order). On a big build that is what froze the game.</p>
 *
 * <p>This class is built <b>once</b> (on a worker thread) per transformed schematic. Blocks that are
 * completely buried inside the structure (all six neighbours present) are dropped, because they can
 * never be seen. What remains is stored in primitive arrays, so a frame is just a tight loop over
 * {@code int[]}.</p>
 *
 * <p>Instances are immutable after construction and therefore safe to share between threads.</p>
 */
public final class GhostShell {

    /** The schematic this shell was built from; used to detect stale shells. */
    public final Schematic owner;
    public final int count;
    /** Relative coordinates (same space as {@link Schematic#blocks}). */
    public final int[] x;
    public final int[] y;
    public final int[] z;
    /** 0xRRGGBB map colour of each block. */
    public final int[] rgb;
    /** Outward facing faces: bit0 down, 1 up, 2 north, 3 south, 4 west, 5 east. */
    public final byte[] faces;

    private GhostShell(Schematic owner, int count, int[] x, int[] y, int[] z, int[] rgb, byte[] faces) {
        this.owner = owner;
        this.count = count;
        this.x = x;
        this.y = y;
        this.z = z;
        this.rgb = rgb;
        this.faces = faces;
    }

    public static GhostShell build(Schematic schematic) {
        if (schematic == null || schematic.isEmpty()) {
            return new GhostShell(schematic, 0, new int[0], new int[0], new int[0], new int[0], new byte[0]);
        }

        Map<BlockPos, BlockState> blocks = schematic.blocks;
        int capacity = blocks.size();
        int[] xs = new int[capacity];
        int[] ys = new int[capacity];
        int[] zs = new int[capacity];
        int[] cs = new int[capacity];
        byte[] fs = new byte[capacity];
        Map<Block, Integer> colourCache = new HashMap<>();

        int n = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            int mask = exposedFaces(blocks, pos);
            if (mask == 0) continue;

            xs[n] = pos.getX();
            ys[n] = pos.getY();
            zs[n] = pos.getZ();
            cs[n] = colourOf(entry.getValue(), colourCache);
            fs[n] = (byte) mask;
            n++;
        }

        return new GhostShell(schematic, n,
                Arrays.copyOf(xs, n), Arrays.copyOf(ys, n), Arrays.copyOf(zs, n), Arrays.copyOf(cs, n),
                Arrays.copyOf(fs, n));
    }

    private static int exposedFaces(Map<BlockPos, BlockState> blocks, BlockPos p) {
        int m = 0;
        if (!blocks.containsKey(p.down())) m |= 1;
        if (!blocks.containsKey(p.up())) m |= 2;
        if (!blocks.containsKey(p.north())) m |= 4;
        if (!blocks.containsKey(p.south())) m |= 8;
        if (!blocks.containsKey(p.west())) m |= 16;
        if (!blocks.containsKey(p.east())) m |= 32;
        return m;
    }

    private static int colourOf(BlockState state, Map<Block, Integer> cache) {
        Block block = state.getBlock();
        Integer cached = cache.get(block);
        if (cached != null) return cached;

        int value;
        try {
            var mapColor = block.getDefaultMapColor();
            value = mapColor != null ? (mapColor.color & 0xFFFFFF) : (block.hashCode() & 0x7F7F7F);
        } catch (Throwable t) {
            value = block.hashCode() & 0x7F7F7F;
        }
        cache.put(block, value);
        return value;
    }
}
