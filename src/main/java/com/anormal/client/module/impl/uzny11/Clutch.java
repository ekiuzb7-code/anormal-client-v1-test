package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Clutch extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Clutch mode", "MLG", "MLG", "Water", "Hay", "Slime", "Boat");
    public final NumberSetting height = new NumberSetting("Height", "Minimum fall height", 5.0, 2.0, 20.0, 0.5);
    public final BooleanSetting autoPlace = new BooleanSetting("Auto Place", "Auto place blocks", true);

    private int stage = 0;
    private BlockPos clutchPos;

    public Clutch() {
        super("Clutch", "Saves you from fall damage", Category.UZNY11);
        addSetting(mode);
        addSetting(height);
        addSetting(autoPlace);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() || mc.player.fallDistance < height.getValue()) return;

        // Find clutch block in hotbar
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem) {
                Block block = ((net.minecraft.item.BlockItem) stack.getItem()).getBlock();
                if (isClutchBlock(block)) {
                    slot = i;
                    break;
                }
            }
        }
        if (slot == -1) return;

        // Place clutch block
        int prev = getSelectedSlot();
        setSelectedSlot(slot);

        BlockPos pos = mc.player.getBlockPos().down();
        mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND,
            new net.minecraft.util.hit.BlockHitResult(
                new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5),
                net.minecraft.util.math.Direction.UP, pos, false));

        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        setSelectedSlot(prev);
    }

    private boolean isClutchBlock(Block block) {
        return block == Blocks.WATER || block == Blocks.LAVA ||
               block == Blocks.HAY_BLOCK || block == Blocks.SLIME_BLOCK ||
               block == Blocks.COBWEB || block == Blocks.POWDER_SNOW;
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