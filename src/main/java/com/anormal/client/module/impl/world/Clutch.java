package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Automatically places blocks to catch fatal falls (Vape-style Clutch).
 *
 * <p>Reads the fall state every tick, looks straight down and places a block on
 * the first solid surface underneath (or against a wall beside you for void
 * clutches). Behaviour mirrors Vape's Clutch settings.</p>
 */
public class Clutch extends Module {
    // --- Activation conditions ---
    public final BooleanSetting onVoid = new BooleanSetting("On Void", "Attempt to save you from the void", true);
    public final BooleanSetting onLethal = new BooleanSetting("On Lethal Fall", "Activate when about to take lethal fall damage", true);
    public final BooleanSetting onMoreThan = new BooleanSetting("On More Than Blocks", "Trigger when falling more than X blocks", true);
    public final NumberSetting blocks = new NumberSetting("Blocks", "Minimum fall distance to trigger", 4.0, 2.0, 30.0, 1.0);

    // --- Additional settings ---
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Look down only while placing, restore your view after", true);
    public final BooleanSetting showBlockCount = new BooleanSetting("Show Block Count", "Show available clutch blocks near the crosshair", true);
    public final BooleanSetting resetAngle = new BooleanSetting("Reset Angle", "Reset your view angles after clutching", true);
    public final BooleanSetting returnToSlot = new BooleanSetting("Return To Slot", "Switch back to your original hotbar slot", true);
    public final BooleanSetting allowStaircase = new BooleanSetting("Allow Staircase Up", "Also clutch against walls to step up", true);
    public final BooleanSetting clutchMoveDelay = new BooleanSetting("Clutch Move Delay", "Freeze movement briefly after a long clutch", true);
    public final NumberSetting maxBlocks = new NumberSetting("Max Blocks", "Skip clutches that need more blocks than this", 10.0, 2.0, 40.0, 1.0);

    // --- Block selection ---
    public final ModeSetting blockSelection = new ModeSetting("Block Selection",
            "Off = any block, Blacklist = skip risky blocks, Whitelist = only common clutch blocks",
            "Blacklist", "Off", "Blacklist", "Whitelist");

    private int originalSlot = -1;
    private float originalPitch = 0f;
    private boolean clutching = false;
    private int placedThisFall = 0;
    private int moveDelayTicks = 0;

    public Clutch() {
        super("Clutch", "Automatically places blocks to catch and prevent fatal falls", Category.WORLD);
        addSetting(onVoid);
        addSetting(onLethal);
        addSetting(onMoreThan);
        addSetting(blocks);
        addSetting(silentAim);
        addSetting(showBlockCount);
        addSetting(resetAngle);
        addSetting(returnToSlot);
        addSetting(allowStaircase);
        addSetting(clutchMoveDelay);
        addSetting(maxBlocks);
        addSetting(blockSelection);
    }

    @Override
    public void onDisable() {
        finishClutch();
    }

    private void finishClutch() {
        if (!clutching) return;
        clutching = false;
        placedThisFall = 0;
        if (resetAngle.isEnabled() || silentAim.isEnabled()) {
            try {
                mc.player.setPitch(originalPitch);
            } catch (Throwable ignored) {
            }
        }
        if (returnToSlot.isEnabled() && originalSlot >= 0) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {
            }
        }
        originalSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (moveDelayTicks > 0) {
            moveDelayTicks--;
            try {
                Vec3d v = mc.player.getVelocity();
                mc.player.setVelocity(0.0, v.y, 0.0);
            } catch (Throwable ignored) {
            }
        }

        // Landed / not falling any more -> clean up.
        if (mc.player.isOnGround() || mc.player.isCreative() || mc.player.isSpectator()) {
            finishClutch();
            return;
        }

        double fall = mc.player.fallDistance;
        double vy = mc.player.getVelocity().y;
        boolean falling = vy < -0.1 || fall > 0.5;
        if (!falling) return;

        boolean voidDanger = onVoid.isEnabled() && mc.player.getY() < mc.world.getBottomY() + 12;
        boolean lethal = onLethal.isEnabled() && fall > 3.0;
        boolean deep = onMoreThan.isEnabled() && fall > blocks.getValue().floatValue();

        if (!(voidDanger || lethal || deep)) return;

        int slot = findBlockSlot();
        if (slot == -1) return;

        if (!clutching) {
            clutching = true;
            placedThisFall = 0;
            originalSlot = mc.player.getInventory().getSelectedSlot();
            originalPitch = mc.player.getPitch();
        }

        if (placedThisFall >= maxBlocks.getValue().intValue()) return;

        try {
            mc.player.getInventory().setSelectedSlot(slot);
            // Look straight down so the placement is legitimate server-side.
            mc.player.setPitch(90.0f);
        } catch (Throwable ignored) {
            return;
        }

        if (placeBlockBelow()) {
            placedThisFall++;
            if (clutchMoveDelay.isEnabled() && placedThisFall >= 2) moveDelayTicks = 4;
        }
    }

    private boolean placeBlockBelow() {
        BlockPos feet = mc.player.getBlockPos();

        // 1) Find the first solid surface below the player and place on top of it.
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos solid = feet.down(dy);
            if (solid.getY() < mc.world.getBottomY()) break;
            BlockState st;
            try {
                st = mc.world.getBlockState(solid);
            } catch (Throwable ignored) {
                break;
            }
            if (st.isAir() || !st.isSolidBlock(mc.world, solid)) continue;

            BlockPos place = solid.up();
            if (!mc.world.getBlockState(place).isAir()) return false;

            Vec3d hit = new Vec3d(solid.getX() + 0.5, solid.getY() + 1.0, solid.getZ() + 0.5);
            return interact(new BlockHitResult(hit, Direction.UP, solid, false));
        }

        // 2) Void / nothing below: place against a wall beside the block under us.
        if (allowStaircase.isEnabled()) {
            BlockPos below = feet.down();
            for (Direction d : Direction.values()) {
                if (d == Direction.UP) continue;
                BlockPos nb = below.offset(d);
                try {
                    BlockState st = mc.world.getBlockState(nb);
                    if (st.isAir() || !st.isSolidBlock(mc.world, nb)) continue;
                } catch (Throwable ignored) {
                    continue;
                }
                Direction face = d.getOpposite();
                Vec3d hit = new Vec3d(
                        nb.getX() + 0.5 + face.getOffsetX() * 0.5,
                        nb.getY() + 0.5 + face.getOffsetY() * 0.5,
                        nb.getZ() + 0.5 + face.getOffsetZ() * 0.5);
                return interact(new BlockHitResult(hit, face, nb, false));
            }
        }
        return false;
    }

    private boolean interact(BlockHitResult bhr) {
        try {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) continue;
            Block block = bi.getBlock();
            if (isAllowed(block)) return i;
        }
        return -1;
    }

    private boolean isAllowed(Block block) {
        String mode = blockSelection.getValue();
        if (mode.equalsIgnoreCase("Off")) return true;

        if (mode.equalsIgnoreCase("Blacklist")) {
            return !isRisky(block);
        }
        // Whitelist: only common, safe clutch blocks.
        return isCommon(block);
    }

    private boolean isRisky(Block b) {
        return b == Blocks.TNT
                || b == Blocks.RESPAWN_ANCHOR
                || b == Blocks.SAND || b == Blocks.RED_SAND || b == Blocks.GRAVEL
                || b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL
                || b == Blocks.SCAFFOLDING || b == Blocks.COBWEB
                || b == Blocks.WHITE_BED || b == Blocks.RED_BED || b == Blocks.BLACK_BED
                || b == Blocks.BLUE_BED || b == Blocks.GREEN_BED || b == Blocks.YELLOW_BED
                || b == Blocks.ORANGE_BED || b == Blocks.PURPLE_BED || b == Blocks.CYAN_BED
                || b == Blocks.LIME_BED || b == Blocks.PINK_BED || b == Blocks.MAGENTA_BED
                || b == Blocks.LIGHT_BLUE_BED || b == Blocks.LIGHT_GRAY_BED
                || b == Blocks.GRAY_BED || b == Blocks.BROWN_BED;
    }

    private boolean isCommon(Block b) {
        return b == Blocks.COBBLESTONE || b == Blocks.STONE || b == Blocks.DEEPSLATE
                || b == Blocks.COBBLED_DEEPSLATE || b == Blocks.OBSIDIAN
                || b == Blocks.END_STONE || b == Blocks.NETHERITE_BLOCK
                || b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.NETHERRACK
                || b == Blocks.BLACKSTONE || b == Blocks.CRAFTING_TABLE
                || b == Blocks.OAK_PLANKS || b == Blocks.SPRUCE_PLANKS || b == Blocks.BIRCH_PLANKS
                || b == Blocks.JUNGLE_PLANKS || b == Blocks.ACACIA_PLANKS
                || b == Blocks.DARK_OAK_PLANKS || b == Blocks.CRIMSON_PLANKS
                || b == Blocks.WARPED_PLANKS || b == Blocks.MANGROVE_PLANKS
                || b == Blocks.CHERRY_PLANKS || b == Blocks.BAMBOO_PLANKS;
    }

    private int countBlocks() {
        int n = 0;
        try {
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isEmpty() && s.getItem() instanceof BlockItem bi && isAllowed(bi.getBlock())) {
                    n += s.getCount();
                }
            }
        } catch (Throwable ignored) {
        }
        return n;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showBlockCount.isEnabled() || mc.player == null || mc.getWindow() == null) return;
        String text = "Blocks: " + countBlocks();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        int color = countBlocks() > 0 ? 0xFF55FF55 : 0xFFFF5555;
        context.drawText(mc.textRenderer, text, w / 2 + 12, h / 2 + 8, color, true);
    }
}
