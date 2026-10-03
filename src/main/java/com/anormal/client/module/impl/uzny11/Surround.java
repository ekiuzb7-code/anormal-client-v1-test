package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Surround extends Module {
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between placements", 2.0, 0.0, 10.0, 1.0);
    public final BooleanSetting corners = new BooleanSetting("Corners", "Also place corner blocks", true);
    public final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", "Disable when surrounded", true);

    private int cooldown = 0;

    public Surround() {
        super("Surround", "Wraps your feet in obsidian for crystal protection", Category.UZNY11);
        addSetting(delay);
        addSetting(corners);
        addSetting(autoDisable);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        BlockPos feet = mc.player.getBlockPos();
        BlockPos[] sides = {feet.north(), feet.south(), feet.east(), feet.west()};
        BlockPos[] extra = corners.isEnabled()
                ? new BlockPos[]{feet.north().east(), feet.north().west(), feet.south().east(), feet.south().west()}
                : new BlockPos[0];

        BlockPos target = null;
        for (BlockPos p : sides) {
            try {
                if (mc.world.isAir(p) || mc.world.getBlockState(p).isReplaceable()) {
                    target = p;
                    break;
                }
            } catch (Throwable ignored) {}
        }
        if (target == null) {
            for (BlockPos p : extra) {
                try {
                    if (mc.world.isAir(p) || mc.world.getBlockState(p).isReplaceable()) {
                        target = p;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (target == null) {
            if (autoDisable.isEnabled()) setEnabled(false);
            return;
        }

        int obby = findObsidian();
        if (obby == -1) return;
        int prev = mc.player.getInventory().getSelectedSlot();
        try {
            mc.player.getInventory().setSelectedSlot(obby);
            BlockPos support = target.down();
            BlockHitResult bhr = new BlockHitResult(
                    new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                    Direction.UP, support, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            cooldown = delay.getValue().intValue();
        } catch (Throwable ignored) {
        } finally {
            try {
                mc.player.getInventory().setSelectedSlot(prev);
            } catch (Throwable ignored) {}
        }
    }

    private int findObsidian() {
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() instanceof BlockItem
                        && Registries.BLOCK.getId(((BlockItem) stack.getItem()).getBlock()).getPath().equals("obsidian")) {
                    return i;
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }
}
