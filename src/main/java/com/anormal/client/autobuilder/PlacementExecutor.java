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
import java.util.Map;

/**
 * Drives a {@link PlacementPlanner.Plan} one ordinary right click at a time.
 *
 * <p>Nothing here spoofs a result: a block is only counted once the client's own world view
 * reports the expected state, so a refused placement stays pending instead of being reported
 * as a success.</p>
 */
public final class PlacementExecutor {

    public enum State {
        IDLE, RUNNING, PAUSED, FINISHED
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

    private State state = State.IDLE;
    private PlacementPlanner.Plan plan;
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
        this.plan = PlacementPlanner.plan(schematic, origin, this.options.replaceConflicts);
        this.cursor = 0;
        this.placed = 0;
        this.failed = 0;
        this.lastPlacement = 0L;
        this.cooldowns.clear();
        this.state = plan.pending.isEmpty() ? State.FINISHED : State.RUNNING;
        this.status = plan.pending.isEmpty()
                ? "Nothing to place - the build already matches"
                : "Building...";
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
        state = State.IDLE;
        plan = null;
        cursor = 0;
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

    /** Re-plans after the origin or the transform changed. */
    public void replan(Schematic schematic, BlockPos origin) {
        if (this.schematic == schematic && this.origin != null && this.origin.equals(origin)) return;
        boolean wasRunning = state == State.RUNNING;
        this.schematic = schematic;
        this.origin = origin;
        if (schematic == null || origin == null) {
            cancel();
            return;
        }
        this.plan = PlacementPlanner.plan(schematic, origin, options.replaceConflicts);
        this.cursor = 0;
        this.cooldowns.clear();
        if (wasRunning) state = State.RUNNING;
        if (plan.pending.isEmpty() && wasRunning) state = State.FINISHED;
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    public void tick() {
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

        if (plan.pending.isEmpty()) {
            state = State.FINISHED;
            status = "Finished";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPlacement < Math.max(0, options.delayMs)) return;

        int budget = Math.max(1, options.blocksPerTick);
        int attempts = 0;
        int scanned = 0;
        int limit = plan.pending.size() * 2 + 8;

        while (attempts < budget && !plan.pending.isEmpty() && scanned < limit) {
            if (cursor >= plan.pending.size()) cursor = 0;
            if (cursor < 0) cursor = 0;

            PlacementPlanner.Task task = plan.pending.get(cursor);
            scanned++;

            BlockState current;
            try {
                current = mc.world.getBlockState(task.pos);
            } catch (Throwable t) {
                cursor++;
                continue;
            }

            if (current.equals(task.desired)) {
                complete(cursor);
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

        if (plan.pending.isEmpty()) {
            state = State.FINISHED;
            status = "Finished - " + placed + " blocks placed";
        } else {
            status = "Placing... " + placed + " done, " + plan.pending.size() + " left";
        }
    }

    private void complete(int index) {
        int last = plan.pending.size() - 1;
        if (index < 0 || index > last) return;
        BlockPos done = plan.pending.get(index).pos;
        if (index != last) plan.pending.set(index, plan.pending.get(last));
        plan.pending.remove(last);
        cooldowns.remove(done);
        placed++;
        if (cursor > plan.pending.size()) cursor = 0;
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
        return state == State.RUNNING || state == State.PAUSED;
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
        return plan == null ? 0 : plan.pending.size();
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

    /** Positions that still need work, used by the "show remaining" overlay. */
    public java.util.List<BlockPos> remainingPositions(int limit) {
        if (plan == null) return java.util.List.of();
        int size = Math.min(limit, plan.pending.size());
        java.util.List<BlockPos> out = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) out.add(plan.pending.get(i).pos);
        return out;
    }
}
