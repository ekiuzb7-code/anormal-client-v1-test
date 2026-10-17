package com.anormal.client.autobuilder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Compares a transformed schematic against the world and produces an ordered list of blocks
 * that still have to be placed.
 *
 * <p><b>Freeze fix:</b> planning used to run in one go on the client thread: one
 * {@code getBlockState} per schematic block, one {@code findFace} (six more lookups) per task and a
 * sort, every time the schematic was moved or rotated. For a large build that blocked the game for
 * seconds. Planning is now incremental: {@link Builder#step(long)} does a bounded slice of work per
 * tick and the (world independent) sort runs on a worker thread. The click face is looked up lazily by
 * the executor right before a block is placed.</p>
 *
 * <p>Ordering is dependency aware: blocks are sorted bottom up, so a block always appears
 * after the block it can be attached to, and row by row inside each layer.</p>
 */
public final class PlacementPlanner {

    private PlacementPlanner() {
    }

    public enum Status {
        /** The world already matches; nothing to do. */
        SATISFIED,
        /** Empty or replaceable, ready to be placed. */
        MISSING,
        /** A different, non replaceable block is in the way. */
        CONFLICT,
        /** Outside the world height limits. */
        OUT_OF_WORLD
    }

    public static final class Task {
        public final BlockPos pos;
        public final BlockState desired;
        public Status status;
        public Direction face;
        /** Sweep order inside a layer (serpentine rows), used only for sorting. */
        final int order;
        /** Set when the world shows the desired state; the entry is dropped on the next compaction. */
        public boolean done;

        Task(BlockPos pos, BlockState desired, Status status, Direction face, int order) {
            this.pos = pos;
            this.desired = desired;
            this.status = status;
            this.face = face;
            this.order = order;
        }
    }

    public static final class Plan {
        public final List<Task> pending = new ArrayList<>();
        /** Number of tasks in {@link #pending} that are not {@link Task#done} yet. */
        public int pendingCount;
        public int satisfied;
        public int conflicts;
        public int outOfWorld;
        public int total;

        public int remaining() {
            return pendingCount;
        }
    }

    /**
     * Starts an incremental plan.
     *
     * @return the builder, or {@code null} when there is no schematic / origin / world
     */
    public static Builder begin(Schematic schematic, BlockPos origin, boolean replaceConflicts) {
        if (schematic == null || origin == null) return null;
        World world = MinecraftClient.getInstance().world;
        if (world == null) return null;
        return new Builder(world, schematic, origin, replaceConflicts);
    }

    /** Time sliced plan construction. {@link #step} is client thread only, {@link #finish} is thread safe. */
    public static final class Builder {
        private final World world;
        private final BlockPos origin;
        private final boolean replaceConflicts;
        private final int sizeX;
        private final BlockPos[] keys;
        private final BlockState[] values;
        private final Plan plan = new Plan();
        private final List<Task> tasks;
        private int index;

        private Builder(World world, Schematic schematic, BlockPos origin, boolean replaceConflicts) {
            this.world = world;
            this.origin = origin;
            this.replaceConflicts = replaceConflicts;
            this.sizeX = Math.max(1, schematic.sizeX);

            int n = schematic.blocks.size();
            this.keys = new BlockPos[n];
            this.values = new BlockState[n];
            int i = 0;
            for (Map.Entry<BlockPos, BlockState> entry : schematic.blocks.entrySet()) {
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }
            this.plan.total = n;
            this.tasks = new ArrayList<>(Math.min(n, 1 << 20));
        }

        /** True when the player left the world this plan was started in. */
        public boolean isStale() {
            return MinecraftClient.getInstance().world != world;
        }

        /** Real fraction of schematic blocks that were already compared against the world. */
        public float fraction() {
            return keys.length == 0 ? 1.0f : index / (float) keys.length;
        }

        /**
         * Processes blocks until the time budget is used up.
         *
         * @return {@code true} once every block has been examined
         */
        public boolean step(long budgetNanos) {
            long deadline = System.nanoTime() + budgetNanos;
            while (index < keys.length) {
                int end = Math.min(keys.length, index + 256);
                for (; index < end; index++) {
                    examine(index);
                }
                if (System.nanoTime() >= deadline) break;
            }
            return index >= keys.length;
        }

        private void examine(int i) {
            BlockPos local = keys[i];
            BlockState desired = values[i];
            BlockPos pos = origin.add(local);

            if (isOutOfWorld(world, pos)) {
                plan.outOfWorld++;
                return;
            }

            BlockState current;
            try {
                current = world.getBlockState(pos);
            } catch (Throwable t) {
                plan.outOfWorld++;
                return;
            }

            if (current.equals(desired)) {
                plan.satisfied++;
                return;
            }

            boolean replaceable = current.isAir() || current.isReplaceable();
            if (!replaceable) {
                plan.conflicts++;
                if (!replaceConflicts) return;
            }

            int lx = local.getX();
            int lz = local.getZ();
            int order = lz * (sizeX + 1) + ((lz & 1) == 0 ? lx : sizeX - 1 - lx);
            tasks.add(new Task(pos, desired, Status.MISSING, null, order));
        }

        /** Sorts the collected tasks bottom up. Touches no world state, so it may run off thread. */
        public Plan finish() {
            tasks.sort(Comparator
                    .comparingInt((Task t) -> t.pos.getY())
                    .thenComparingInt(t -> t.order));
            plan.pending.addAll(tasks);
            plan.pendingCount = plan.pending.size();
            return plan;
        }
    }

    /**
     * @return the face of a neighbouring block that can be clicked to place at {@code pos},
     * or {@code null} when no solid neighbour exists yet.
     */
    public static Direction findFace(World world, BlockPos pos) {
        Direction[] order = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
        };
        for (Direction face : order) {
            BlockPos neighbour = pos.offset(face.getOpposite());
            try {
                BlockState state = world.getBlockState(neighbour);
                if (state.isAir()) continue;
                if (state.isReplaceable()) continue;
                if (state.isSolidBlock(world, neighbour)) return face;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean isOutOfWorld(World world, BlockPos pos) {
        try {
            if (world.isOutOfHeightLimit(pos)) return true;
        } catch (Throwable ignored) {
        }
        try {
            return !world.isInBuildLimit(pos);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
