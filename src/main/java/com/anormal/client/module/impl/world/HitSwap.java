package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;

public class HitSwap extends Module {
    public final BooleanSetting maces = new BooleanSetting("Maces", "Swap to Mace for breach damage", true);
    public final BooleanSetting smashOnly = new BooleanSetting("Smash Only", "Only swap to Mace while falling", true);
    public final BooleanSetting axes = new BooleanSetting("Axes", "Swap to Axe to disable shields", true);
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", "Switch back to Sword after hit", true);

    private int originalSlot = -1;
    private int swapTimer = 0;

    public HitSwap() {
        super("HitSwap", "Swaps weapon right as attack connects to apply special weapon attributes", Category.WORLD);
        addSetting(maces);
        addSetting(smashOnly);
        addSetting(axes);
        addSetting(switchBack);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (swapTimer > 0) {
            swapTimer--;
            if (swapTimer == 0 && originalSlot != -1 && switchBack.isEnabled()) {
                mc.player.getInventory().selectedSlot = originalSlot;
                originalSlot = -1;
            }
            return;
        }

        if (mc.options.attackKey.isPressed()) {
            int axeSlot = -1;
            int maceSlot = -1;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() instanceof AxeItem) axeSlot = i;
                if (stack.getItem() instanceof MaceItem) maceSlot = i;
            }

            boolean targetIsBlocking = mc.targetedEntity instanceof LivingEntity target && target.isBlocking();
            boolean isFalling = mc.player.fallDistance > 1.0f || mc.player.getVelocity().y < -0.2;

            if (axes.isEnabled() && targetIsBlocking && axeSlot != -1) {
                originalSlot = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = axeSlot;
                swapTimer = 3;
            } else if (maces.isEnabled() && (!smashOnly.isEnabled() || isFalling) && maceSlot != -1) {
                originalSlot = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = maceSlot;
                swapTimer = 3;
            }
        }
    }
}
