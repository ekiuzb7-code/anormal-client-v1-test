package com.anormal.client.autobuilder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compares a transformed schematic against the world and produces an ordered list of blocks
 * that still have to be placed.
 *
 * <p>Ordering is dependency aware: blocks are sorted bottom up, so a block always appears
 * after the block it can be attached to. Each task also carries the face that should be
 * clicked to place it, which is what the executor needs to perform an ordinary right click.</p>
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

        Task(BlockPos pos, BlockState desired, Status status, Direction face) {
            this.pos = pos;
            this.desired = desired;
            this.status = status;
            this.face = face;
        }
    }

    public static final class Plan {
        public final List<Task> pending = new ArrayList<>();
        public int satisfied;
        public int conflicts;
        public int outOfWorld;
        public int total;

        public int remaining() {
            return pending.size();
        }
    }

    public static Plan plan(Schematic schematic, BlockPos origin, boolean replaceConflicts) {
        Plan plan = new Plan();
        if (schematic == null || origin == null) return plan;

        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;
        if (world == null) return plan;

        plan.total = schematic.blockCount();

        List<Task> tasks = new ArrayList<>(schematic.blockCount());

        for (var entry : schematic.blocks.entrySet()) {
            BlockPos local = entry.getKey();
            BlockState desired = entry.getValue();
            BlockPos pos = origin.add(local);

            if (isOutOfWorld(world, pos)) {
                plan.outOfWorld++;
                continue;
            }

            BlockState current;
            try {
                current = world.getBlockState(pos);
            } catch (Throwable t) {
                plan.outOfWorld++;
                continue;
            }

            if (current.equals(desired)) {
                plan.satisfied++;
                continue;
            }

            boolean replaceable = current.isAir() || current.isReplaceable();

            if (!replaceable) {
                if (!replaceConflicts) {
                    plan.conflicts++;
                    continue;
                }
                plan.conflicts++;
            }

            tasks.add(new Task(pos, desired, Status.MISSING, null));
        }

        // Bottom up: supports are placed before the blocks that sit on them.
        tasks.sort(Comparator
                .comparingInt((Task t) -> t.pos.getY())
                .thenComparingInt(t -> Math.abs(t.pos.getX()) + Math.abs(t.pos.getZ())));

        for (Task task : tasks) {
            task.face = findFace(world, task.pos);
            if (task.face == null) {
                // Nothing to click against yet; it may become placeable once lower layers exist.
                task.status = Status.MISSING;
            }
            plan.pending.add(task);
        }

        return plan;
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
