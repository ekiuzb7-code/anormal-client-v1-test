package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class MLG extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "MLG mode", "Water", "Water", "Hay", "Slime", "Boat", "Powder Snow");
    public final NumberSetting height = new NumberSetting("Height", "Minimum fall height", 5.0, 2.0, 20.0, 0.5);
    public final BooleanSetting bucket = new BooleanSetting("Bucket", "Use water bucket", true);

    public MLG() {
        super("MLG", "MLG water bucket clutch", Category.UZNY11);
        addSetting(mode);
        addSetting(height);
        addSetting(bucket);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.isOnGround() || mc.player.fallDistance < height.getValue()) return;

        // Find MLG block
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem) {
                var block = ((net.minecraft.item.BlockItem) stack.getItem()).getBlock();
                if (isMLGBlock(block)) { slot = i; break; }
            }
        }
        if (slot == -1) return;

        int prev = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        BlockPos pos = mc.player.getBlockPos().down();
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
            new net.minecraft.util.hit.BlockHitResult(
                new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5),
                Direction.UP, pos, false));

        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().selectedSlot = prev;
    }

    private boolean isMLGBlock(net.minecraft.block.Block block) {
        return block == Blocks.WATER || block == Blocks.HAY_BLOCK ||
               block == Blocks.SLIME_BLOCK || block == Blocks.POWDER_SNOW;
    }
}