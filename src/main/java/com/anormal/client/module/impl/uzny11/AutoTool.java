package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import com.anormal.client.setting.BooleanSetting;

public class AutoTool extends Module {
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", "Switches back to previous slot after mining", true);
    public final BooleanSetting weaponCheck = new BooleanSetting("Weapon Check", "Prefers swords when targeting cobwebs", true);

    private int previousSlot = -1;
    private boolean wasMining = false;

    public AutoTool() {
        super("AutoTool", "Automatically equips the fastest tool for mining targeted block", Category.UZNY11);
        addSetting(switchBack);
        addSetting(weaponCheck);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        boolean isMining = mc.options.attackKey.isPressed() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK;

        if (isMining) {
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

            if (bestSlot != -1) {
                if (!wasMining) {
                    previousSlot = mc.player.getInventory().getSelectedSlot();
                }
                if (mc.player.getInventory().getSelectedSlot() != bestSlot) {
                    mc.player.getInventory().setSelectedSlot(bestSlot);
                }
                wasMining = true;
            }
        } else {
            if (wasMining && switchBack.isEnabled() && previousSlot != -1) {
                mc.player.getInventory().setSelectedSlot(previousSlot);
                previousSlot = -1;
            }
            wasMining = false;
        }
    }
}
