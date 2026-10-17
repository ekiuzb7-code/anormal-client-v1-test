package com.anormal.client.autobuilder;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Drives a {@link PlacementPlanner.Plan} one ordinary right click at a time.
 *
 * <p>Nothing here spoofs a result: a block is only counted once the client's own world view
 * reports the expected state, so a refused placement stays pending instead of being reported
 * as a success.</p>
 */
public final class PlacementExecutor {

    public enum State {
        IDLE, PLANNING, RUNNING, PAUSED, FINISHED
    }

    public static final class Options {
        public boolean replaceConflicts = false;
        public int blocksPerTick = 2;
        public int delayMs = 120;
        public boolean swing = true;
        public double reach = 4.5;
    }

    private static final PlacementExecutor INSTANCE = new PlacementExecutor();

    public static PlacementExecutor get() {
        return INSTANCE;
    }

    /** Tasks looked at per tick at most; keeps a huge plan from stalling the client thread. */
    private static final int SCAN_PER_TICK = 600;
    /** Client thread time given to world comparison per tick while planning. */
    private static final long PLAN_BUDGET_NANOS = 6_000_000L;

    private State state = State.IDLE;
    private PlacementPlanner.Plan plan;
    private PlacementPlanner.Builder builder;
    private CompletableFuture<PlacementPlanner.Plan> sorting;
    private boolean autoRun;
    private int doneSinceCompact;
    private Schematic schematic;
    private BlockPos origin;
    private Options options = new Options();

    private int cursor;
    private long lastPlacement;
    private int placed;
    private int failed;
    private String status = "";

    private final Map<BlockPos, Integer> cooldowns = new HashMap<>();

    private PlacementExecutor() {
    }

    // ------------------------------------------------------------------
    //  Control
    // ------------------------------------------------------------------

    public void start(Schematic schematic, BlockPos origin, Options options) {
        if (schematic == null || origin == null) {
            this.status = "No schematic selected";
            return;
        }
        this.schematic = schematic;
        this.origin = origin;
        this.options = options == null ? new Options() : options;
        this.placed = 0;
        this.failed = 0;
        this.lastPlacement = 0L;
        beginPlanning(true);
    }

    /**
     * Plans in the background without starting to build (used by the "show remaining" overlay).
     * Does nothing when a plan already exists or is being made.
     */
    public void ensurePlan(Schematic schematic, BlockPos origin, Options options) {
        if (schematic == null || origin == null) return;
        if (state != State.IDLE || plan != null || builder != null || sorting != null) return;
        this.schematic = schematic;
        this.origin = origin;
        this.options = options == null ? new Options() : options;
        beginPlanning(false);
    }

    private void beginPlanning(boolean run) {
        abortPlanning();
        plan = null;
        cursor = 0;
        doneSinceCompact = 0;
        cooldowns.clear();

        builder = PlacementPlanner.begin(schematic, origin, options.replaceConflicts);
        if (builder == null) {
            state = State.IDLE;
            status = "No world loaded";
            return;
        }
        autoRun = run;
        state = State.PLANNING;
        status = "Planning...";
    }

    private void abortPlanning() {
        builder = null;
        if (sorting != null) {
            sorting.cancel(false);
            sorting = null;
        }
    }

    public void pause(String reason) {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            status = reason;
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            state = State.RUNNING;
            status = "Building...";
        }
    }

    public void cancel() {
        abortPlanning();
        state = State.IDLE;
        plan = null;
        cursor = 0;
        doneSinceCompact = 0;
        cooldowns.clear();
        status = "Cancelled";
    }

    /** Drops all progress information but keeps the schematic selection untouched. */
    public void reset() {
        cancel();
        placed = 0;
        failed = 0;
        status = "";
    }

    public void retryFailed() {
        if (schematic != null && origin != null) {
            start(schematic, origin, options);
        }
    }

    /**
     * Called after the origin or the transform changed. This is deliberately cheap: it only
     * invalidates the old plan. A running build is stopped (silently carrying on at a new position
     * would be surprising); an idle executor re-plans lazily when something asks for a plan.
     */
    public void replan(Schematic schematic, BlockPos origin) {
        if (this.schematic == schematic && this.origin != null && this.origin.equals(origin)) return;
        boolean wasActive = state == State.RUNNING || state == State.PAUSED
                || (state == State.PLANNING && autoRun);

        this.schematic = schematic;
        this.origin = origin;
        abortPlanning();
        plan = null;
        cursor = 0;
        doneSinceCompact = 0;
        cooldowns.clear();
        state = State.IDLE;

        if (schematic == null || origin == null) {
            status = "Cancelled";
            return;
        }
        status = wasActive ? "Build stopped: position or transform changed" : "";
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    private void tickPlanning() {
        if (builder != null) {
            if (builder.isStale()) {
                cancel();
                status = "World changed - plan dropped";
                return;
            }
            if (!builder.step(PLAN_BUDGET_NANOS)) {
                status = "Planning " + (int) (builder.fraction() * 100.0f) + "%";
                return;
            }
            final PlacementPlanner.Builder finished = builder;
            builder = null;
            status = "Sorting...";
            sorting = CompletableFuture.supplyAsync(finished::finish);
            return;
        }

        if (sorting != null) {
            if (!sorting.isDone()) return;
            PlacementPlanner.Plan result;
            try {
                result = sorting.get();
            } catch (Throwable t) {
                result = null;
            }
            sorting = null;

            if (result == null) {
                state = State.IDLE;
                status = "Planning failed";
                return;
            }
            plan = result;
            cursor = 0;
            doneSinceCompact = 0;

            if (!autoRun) {
                state = State.IDLE;
                status = plan.remaining() + " blocks to place";
            } else if (plan.remaining() == 0) {
                state = State.FINISHED;
                status = "Nothing to place - the build already matches";
            } else {
                state = State.RUNNING;
                status = "Building...";
            }
            return;
        }

        // Nothing left to plan with; should not happen, but never get stuck in PLANNING.
        state = State.IDLE;
    }

    public void tick() {
        if (state == State.PLANNING) {
            tickPlanning();
            return;
        }
        if (state != State.RUNNING || plan == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            pause("No world loaded");
            return;
        }
        if (mc.currentScreen != null) {
            status = "Paused while a screen is open";
            return;
        }

        if (plan.remaining() <= 0) {
            state = State.FINISHED;
            status = "Finished";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPlacement < Math.max(0, options.delayMs)) return;

        int budget = Math.max(1, options.blocksPerTick);
        int attempts = 0;
        int scanned = 0;
        List<PlacementPlanner.Task> list = plan.pending;

        while (attempts < budget && scanned < SCAN_PER_TICK && plan.remaining() > 0) {
            if (cursor >= list.size() || cursor < 0) {
                cursor = 0;
                if (doneSinceCompact > 0) {
                    list.removeIf(t -> t.done);
                    doneSinceCompact = 0;
                }
                if (list.isEmpty()) break;
            }

            PlacementPlanner.Task task = list.get(cursor);
            scanned++;
            if (task.done) {
                cursor++;
                continue;
            }

            BlockState current;
            try {
                current = mc.world.getBlockState(task.pos);
            } catch (Throwable t) {
                cursor++;
                continue;
            }

            if (current.equals(task.desired)) {
                complete(task);
                cursor++;
                continue;
            }

            Integer cooldown = cooldowns.get(task.pos);
            if (cooldown != null && cooldown > 0) {
                cooldowns.put(task.pos, cooldown - 1);
                cursor++;
                continue;
            }

            Direction face = task.face;
            if (face == null) {
                face = PlacementPlanner.findFace(mc.world, task.pos);
                if (face == null) {
                    cursor++;
                    continue;
                }
                task.face = face;
            }

            BlockPos neighbour = task.pos.offset(face.getOpposite());
            Vec3d hitVec = Vec3d.ofCenter(neighbour).add(
                    face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);

            Vec3d eye = mc.player.getEyePos();
            if (eye.squaredDistanceTo(hitVec) > options.reach * options.reach) {
                cursor++;
                continue;
            }

            int slot = findHotbarSlot(mc, task.desired);
            if (slot < 0) {
                pause("Missing material: " + BlockStateParser.shortName(task.desired));
                return;
            }

            if (place(mc, task, neighbour, face, hitVec, slot)) {
                attempts++;
                lastPlacement = System.currentTimeMillis();
                cooldowns.put(task.pos, 6);
            } else {
                failed++;
                cooldowns.put(task.pos, 12);
            }
            cursor++;
        }

        if (plan.remaining() <= 0) {
            state = State.FINISHED;
            status = "Finished - " + placed + " blocks placed";
        } else {
            status = "Placing... " + placed + " done, " + plan.remaining() + " left";
        }
    }

    /**
     * Marks a task finished without reordering the list (the old swap-with-last trick destroyed the
     * bottom-up order, so roofs were attempted before the walls that carry them).
     */
    private void complete(PlacementPlanner.Task task) {
        if (task.done) return;
        task.done = true;
        plan.pendingCount = Math.max(0, plan.pendingCount - 1);
        doneSinceCompact++;
        cooldowns.remove(task.pos);
        placed++;
    }

    private boolean place(MinecraftClient mc, PlacementPlanner.Task task, BlockPos neighbour,
                          Direction face, Vec3d hitVec, int slot) {
        try {
            PlayerInventory inventory = mc.player.getInventory();
            if (inventory.getSelectedSlot() != slot) {
                inventory.setSelectedSlot(slot);
            }

            Vec3d target = Vec3d.ofCenter(task.pos);
            Vec3d eye = mc.player.getEyePos();
            double dx = target.x - eye.x;
            double dy = target.y - eye.y;
            double dz = target.z - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);

            BlockHitResult hit = new BlockHitResult(hitVec, face, neighbour, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (options.swing) mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private int findHotbarSlot(MinecraftClient mc, BlockState desired) {
        try {
            Item item = desired.getBlock().asItem();
            if (item == Items.AIR) return -1;

            PlayerInventory inventory = mc.player.getInventory();
            int hotbar = Math.max(1, inventory.getHotbarSize());
            for (int slot = 0; slot < hotbar; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                if (stack.getItem() == item) return slot;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    // ------------------------------------------------------------------
    //  Read only state for the UI
    // ------------------------------------------------------------------

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isActive() {
        return state == State.PLANNING || state == State.RUNNING || state == State.PAUSED;
    }

    public String getStatus() {
        return status;
    }

    public int getPlaced() {
        return placed;
    }

    public int getFailed() {
        return failed;
    }

    public int getRemaining() {
        return plan == null ? 0 : plan.remaining();
    }

    public int getTotal() {
        return plan == null ? 0 : plan.total;
    }

    public int getSatisfied() {
        return plan == null ? 0 : plan.satisfied;
    }

    public int getConflicts() {
        return plan == null ? 0 : plan.conflicts;
    }

    public int getOutOfWorld() {
        return plan == null ? 0 : plan.outOfWorld;
    }

    public Options getOptions() {
        return options;
    }

    /** Fraction (0..1) of the world comparison that is really finished, or -1 when not planning. */
    public float getPlanningProgress() {
        if (state != State.PLANNING) return -1.0f;
        if (builder != null) return builder.fraction();
        return 1.0f;
    }

    /** Positions that still need work, used by the "show remaining" overlay. */
    public java.util.List<BlockPos> remainingPositions(int limit) {
        if (plan == null || plan.remaining() <= 0) return java.util.List.of();
        List<PlacementPlanner.Task> list = plan.pending;
        int n = list.size();
        java.util.List<BlockPos> out = new java.util.ArrayList<>(Math.min(limit, plan.remaining()));
        int start = Math.max(0, Math.min(cursor, n));
        int looked = 0;
        for (int i = 0; i < n && out.size() < limit && looked < 4000; i++, looked++) {
            PlacementPlanner.Task task = list.get((start + i) % n);
            if (!task.done) out.add(task.pos);
        }
        return out;
    }
}
