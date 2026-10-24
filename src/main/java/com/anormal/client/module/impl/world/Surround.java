package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.rotation.Rotation;
import com.anormal.client.rotation.RotationController;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Wraps your feet in blast-resistant blocks for crystal protection.
 *
 * <p>The old version always clicked {@code target.down()} with an UP face. When that block was air
 * - exactly the case when you stand on a ledge - the click was invalid and nothing happened. It
 * also never checked reach and never rotated towards the block, so the server saw placements made
 * without looking at them.</p>
 *
 * <p>Now it finds any solid neighbour of the target, respects real reach, centres you in the block
 * first (a surround only works from the middle) and sends the rotation before the placement.</p>
 */
public class Surround extends Module {

    public final NumberSetting delay = new NumberSetting("Delay",
            "Ticks between two placements", 2.0, 0.0, 10.0, 1.0);

    public final BooleanSetting corners = new BooleanSetting("Corners",
            "Also fill the four diagonal blocks", true);

    public final BooleanSetting autoCenter = new BooleanSetting("Auto Center",
            "Walk to the middle of your block before placing", true);

    public final ModeSetting blockMode = new ModeSetting("Block Mode",
            "Which blocks may be used", "Blast Resistant", "Obsidian Only", "Blast Resistant", "Any Block");

    public final BooleanSetting rotate = new BooleanSetting("Rotate",
            "Turn towards the block before placing it", true);

    public final BooleanSetting silentRotate = new BooleanSetting("Silent Rotate",
            "Send the rotation without moving your camera", true);

    public final NumberSetting reach = new NumberSetting("Reach",
            "Maximum placement distance (clamped to your real reach)", 4.5, 2.0, 6.0, 0.1);

    public final BooleanSetting autoDisable = new BooleanSetting("Auto Disable",
            "Switch off once you are fully surrounded", true);

    // ------------------------------------------------------------------
    //  State
    // ------------------------------------------------------------------
    private static final String[] BLAST_RESISTANT = {
            "obsidian", "crying_obsidian", "ancient_debris", "netherite_block",
            "respawn_anchor", "enchanting_table", "anvil", "reinforced_deepslate"
    };

    private final RotationController rotation = new RotationController();

    private int cooldown = 0;
    private int originalSlot = -1;
    private boolean swapped = false;

    // Centring: only the keys this module pressed are ever released again.
    private static final int K_FORWARD = 0;
    private static final int K_BACK = 1;
    private static final int K_LEFT = 2;
    private static final int K_RIGHT = 3;
    private final boolean[] owned = new boolean[4];

    public Surround() {
        super("Surround", "Wraps your feet in obsidian for crystal protection", Category.WORLD);
        addSetting(delay);
        addSetting(corners);
        addSetting(autoCenter);
        addSetting(blockMode);
        addSetting(rotate);
        addSetting(silentRotate);
        addSetting(reach);
        addSetting(autoDisable);

        silentRotate.visibleIf(() -> rotate.isEnabled());
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        originalSlot = -1;
        swapped = false;
        rotation.reset();
    }

    @Override
    public void onDisable() {
        releaseMovement();
        restoreSlot();
    }

    // ------------------------------------------------------------------
    //  Tick
    // ------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // A surround only works from the middle of the block - otherwise you are clipped into the
        // edge and the placement is rejected.
        if (autoCenter.isEnabled() && centerStep()) {
            cooldown = 1;
            return;
        }
        releaseMovement();

        BlockPos feet = mc.player.getBlockPos();
        BlockPos target = nextTarget(feet);
        if (target == null) {
            if (autoDisable.isEnabled()) setEnabled(false);
            return;
        }

        int slot = findBlockSlot();
        if (slot == -1) return;

        Support support = findSupport(target);
        if (support == null) return;

        swapTo(slot);
        if (rotate.isEnabled()) {
            Rotation aim = Rotation.toward(mc.player.getEyePos(), support.hit);
            if (silentRotate.isEnabled()) {
                rotation.send(aim.yaw(), aim.pitch());
            } else {
                mc.player.setYaw(aim.yaw());
                mc.player.setPitch(aim.pitch());
            }
        }

        try {
            BlockHitResult hit = new BlockHitResult(support.hit, support.face, support.neighbour, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            cooldown = Math.max(1, delay.getValue().intValue());
        } catch (Throwable ignored) {
        }
    }

    /** The first block around the feet that still has to be filled. */
    private BlockPos nextTarget(BlockPos feet) {
        BlockPos[] sides = {feet.north(), feet.south(), feet.east(), feet.west()};
        for (BlockPos pos : sides) {
            if (replaceable(pos)) return pos;
        }
        if (!corners.isEnabled()) return null;

        BlockPos[] diagonals = {
                feet.north().east(), feet.north().west(), feet.south().east(), feet.south().west()
        };
        for (BlockPos pos : diagonals) {
            if (replaceable(pos)) return pos;
        }
        return null;
    }

    private boolean replaceable(BlockPos pos) {
        try {
            BlockState state = mc.world.getBlockState(pos);
            return state.isAir() || state.isReplaceable();
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Placement
    // ------------------------------------------------------------------

    private static final class Support {
        final BlockPos neighbour;
        final Direction face;
        final Vec3d hit;

        Support(BlockPos neighbour, Direction face, Vec3d hit) {
            this.neighbour = neighbour;
            this.face = face;
            this.hit = hit;
        }
    }

    /**
     * Any solid, non-interactive neighbour whose face is within reach. The old code assumed the
     * block below always exists, which is exactly what fails on a ledge.
     */
    private Support findSupport(BlockPos target) {
        Direction[] order = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
        };
        Vec3d eye = mc.player.getEyePos();
        double max = reach();
        double maxSq = max * max;

        for (Direction side : order) {
            BlockPos neighbour = target.offset(side);
            try {
                BlockState state = mc.world.getBlockState(neighbour);
                if (state.isAir() || state.isReplaceable()) continue;
                if (isInteractive(state)) continue;
                if (!state.isSolidBlock(mc.world, neighbour)) continue;
            } catch (Throwable t) {
                continue;
            }

            Direction face = side.getOpposite();
            Vec3d hit = Vec3d.ofCenter(neighbour).add(
                    face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (eye.squaredDistanceTo(hit) > maxSq) continue;
            return new Support(neighbour, face, hit);
        }
        return null;
    }

    private boolean isInteractive(BlockState state) {
        try {
            if (state.hasBlockEntity()) return true;
            var block = state.getBlock();
            return block instanceof net.minecraft.block.DoorBlock
                    || block instanceof net.minecraft.block.TrapdoorBlock
                    || block instanceof net.minecraft.block.FenceGateBlock
                    || block instanceof net.minecraft.block.ButtonBlock
                    || block instanceof net.minecraft.block.LeverBlock
                    || block instanceof net.minecraft.block.CraftingTableBlock
                    || block instanceof net.minecraft.block.AnvilBlock
                    || block instanceof net.minecraft.block.NoteBlock
                    || block instanceof net.minecraft.block.AbstractRedstoneGateBlock;
        } catch (Throwable t) {
            return false;
        }
    }

    private double reach() {
        double configured = reach.getValue();
        try {
            configured = Math.min(configured, mc.player.getBlockInteractionRange());
        } catch (Throwable ignored) {
        }
        return configured;
    }

    // ------------------------------------------------------------------
    //  Blocks
    // ------------------------------------------------------------------

    private int findBlockSlot() {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;

            String path = Registries.BLOCK.getId(blockItem.getBlock()).getPath();
            if (path.equals("air") || isInteractiveBlock(path)) continue;

            if (blockMode.is("Any Block")) return i;
            if (blockMode.is("Obsidian Only")) {
                if (path.equals("obsidian")) return i;
                continue;
            }

            for (String name : BLAST_RESISTANT) {
                if (path.equals(name)) return i;
            }
            if (fallback < 0) fallback = i;
        }
        // "Blast Resistant" falls back to any solid block rather than doing nothing.
        return blockMode.is("Obsidian Only") ? -1 : fallback;
    }

    private boolean isInteractiveBlock(String path) {
        return path.contains("door") || path.contains("button") || path.contains("lever")
                || path.contains("chest") || path.contains("bed") || path.contains("tnt")
                || path.contains("scaffolding") || path.contains("torch");
    }

    private void swapTo(int slot) {
        if (!swapped) originalSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        swapped = true;
    }

    private void restoreSlot() {
        if (swapped && mc.player != null && originalSlot >= 0 && originalSlot < 9) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {
            }
        }
        swapped = false;
        originalSlot = -1;
    }

    // ------------------------------------------------------------------
    //  Centring
    // ------------------------------------------------------------------

    /**
     * Walks towards the middle of the current block using ordinary movement keys.
     *
     * @return {@code true} while still moving
     */
    private boolean centerStep() {
        if (!mc.player.isOnGround()) return false;

        double cx = Math.floor(mc.player.getX()) + 0.5;
        double cz = Math.floor(mc.player.getZ()) + 0.5;
        double dx = cx - mc.player.getX();
        double dz = cz - mc.player.getZ();
        if (dx * dx + dz * dz < 0.02) {
            releaseMovement();
            return false;
        }

        double rad = Math.toRadians(mc.player.getYaw());
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = -fwdZ;
        double rightZ = fwdX;

        double forward = dx * fwdX + dz * fwdZ;
        double strafe = dx * rightX + dz * rightZ;

        applyKey(K_FORWARD, forward > 0.02);
        applyKey(K_BACK, forward < -0.02);
        applyKey(K_RIGHT, strafe > 0.02);
        applyKey(K_LEFT, strafe < -0.02);
        return true;
    }

    private void applyKey(int which, boolean want) {
        try {
            var key = switch (which) {
                case K_FORWARD -> mc.options.forwardKey;
                case K_BACK -> mc.options.backKey;
                case K_LEFT -> mc.options.leftKey;
                default -> mc.options.rightKey;
            };
            if (want) {
                if (!key.isPressed()) {
                    key.setPressed(true);
                    owned[which] = true;
                }
            } else if (owned[which]) {
                key.setPressed(false);
                owned[which] = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseMovement() {
        for (int i = 0; i < owned.length; i++) applyKey(i, false);
    }
}
