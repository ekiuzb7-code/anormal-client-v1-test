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
    public final ModeSetting mode = new ModeSetting("Mode", "Scaffold mode", "Normal", "Normal", "Swing", "Sneak");
    public final NumberSetting speed = new NumberSetting("Speed", "Blocks per second", 15.0, 1.0, 30.0, 1.0);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing arm", true);
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Rotate to place", true);
    public final NumberSetting extend = new NumberSetting("Extend", "Max place distance", 4.5, 3.0, 6.0, 0.1);
    public final BooleanSetting tower = new BooleanSetting("Tower", "Tower up when jump", true);

    private int delay = 0;

    public Scaffold() {
        super("Scaffold", "Automatically places blocks under you", Category.UZNY11);
        addSetting(mode);
        addSetting(speed);
        addSetting(swing);
        addSetting(rotate);
        addSetting(extend);
        addSetting(tower);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int tickDelay = (int) (20.0 / speed.getValue());
        if (++delay < tickDelay) return;
        delay = 0;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos below = playerPos.down();

        if (!mc.world.getBlockState(below).isAir()) {
            if (tower.isEnabled() && mc.options.jumpKey.isPressed() && mc.player.isOnGround()) {
                mc.player.jump();
            }
            return;
        }

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return;

        int prev = getSelectedSlot();
        setSelectedSlot(slot);

        BlockPos placePos = below;
        Direction face = Direction.UP;

        for (Direction dir : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos neighbor = below.offset(dir);
            if (!mc.world.getBlockState(neighbor).isAir()) {
                placePos = neighbor;
                face = dir.getOpposite();
                break;
            }
        }

        mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND,
            new net.minecraft.util.hit.BlockHitResult(
                new net.minecraft.util.math.Vec3d(placePos.getX() + 0.5, placePos.getY() + 1, placePos.getZ() + 0.5),
                face, placePos, false));

        if (swing.isEnabled()) mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        setSelectedSlot(prev);
    }

    private int getSelectedSlot() {
        try {
            return mc.player.getInventory().getSelectedSlot();
        } catch (Exception e) {
            return 0;
        }
    }

    private void setSelectedSlot(int slot) {
        try {
            mc.player.getInventory().setSelectedSlot(slot);
        } catch (Exception e) {
            // Ignore
        }
    }
}