package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Scaffold extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Scaffold mode", "Normal", "Normal", "Swing");
    public final NumberSetting speed = new NumberSetting("Speed", "Blocks per second", 15.0, 1.0, 30.0, 1.0);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing arm when placing", true);
    public final BooleanSetting rotate = new NumberSetting("Rotate", "Rotate to place", true, 0.0, 1.0, 1.0);
    public final NumberSetting extend = new NumberSetting("Extend", "Max place distance", 4.5, 3.0, 6.0, 0.1);

    private int delay = 0;

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under you", Category.UZNY11);
        addSetting(mode);
        addSetting(speed);
        addSetting(swing);
        addSetting(rotate);
        addSetting(extend);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int d = (int) (20.0 / speed.getValue());
        if (++delay < d) return;
        delay = 0;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos below = playerPos.down();

        // Check if we need to place block
        if (!mc.world.getBlockState(below).isAir()) return;

        // Find block in hotbar
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return;

        // Try to place
        int prevSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        BlockPos placePos = below;
        Direction face = Direction.UP;

        // Try to find a valid face
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos neighbor = below.offset(d);
            if (!mc.world.getBlockState(neighbor).isAir()) {
                placePos = neighbor;
                face = d.getOpposite();
                break;
            }
        }

        // Rotate and place
        if (rotate.isEnabled()) {
            // Would need rotation logic here
        }

        mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND,
            new net.minecraft.util.hit.BlockHitResult(
                new net.minecraft.util.math.Vec3d(placePos.getX() + 0.5, placePos.getY() + 1, placePos.getZ() + 0.5),
                face, placePos, false));

        if (swing.isEnabled()) {
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }

        mc.player.getInventory().selectedSlot = prevSlot;
    }
}