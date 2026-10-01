package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class AutoTool extends Module {
    public AutoTool() {
        super("AutoTool", "Automatically equips the fastest tool for mining targeted block", Category.PLAYER);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.crosshairTarget == null) return;
        if (!mc.options.attackKey.isPressed()) return;

        if (mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) mc.crosshairTarget;
            BlockState state = mc.world.getBlockState(bhr.getBlockPos());

            int bestSlot = -1;
            float bestSpeed = 1.0f;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    float speed = stack.getMiningSpeedMultiplier(state);
                    if (speed > bestSpeed) {
                        bestSpeed = speed;
                        bestSlot = i;
                    }
                }
            }

            if (bestSlot != -1 && mc.player.getInventory().selectedSlot != bestSlot) {
                mc.player.getInventory().selectedSlot = bestSlot;
            }
        }
    }
}
