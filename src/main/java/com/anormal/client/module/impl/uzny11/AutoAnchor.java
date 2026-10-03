package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoAnchor extends Module {
    public final NumberSetting delay = new NumberSetting("Delay", "Delay between actions (ticks)", 4, 0, 20, 1);
    public final BooleanSetting doubleAnchor = new BooleanSetting("Double Anchor", "Place second anchor after first explodes", false);
    public final BooleanSetting safeAnchor = new BooleanSetting("Safe Anchor", "Place glowstone cover", false);

    private int ticks = 0;
    private int stage = 0; // 0=place, 1=charge, 2=detonate
    private BlockPos anchorPos;

    public AutoAnchor() {
        super("AutoAnchor", "Auto place, charge and detonate respawn anchors", Category.UZNY11);
        addSetting(delay);
        addSetting(doubleAnchor);
        addSetting(safeAnchor);
    }

    @Override
    public void onEnable() {
        ticks = 0;
        stage = 0;
        anchorPos = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        if (stage == 0) {
            // Place anchor
            int slot = findItem(Blocks.RESPAWN_ANCHOR);
            if (slot == -1) return;

            BlockPos pos = mc.player.getBlockPos().add(0, 1, 0);
            if (!mc.world.getBlockState(pos).isAir()) return;

            int prev = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = slot;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(
                    new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                    Direction.UP, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.getInventory().selectedSlot = prev;
            anchorPos = pos;
            stage = 1;

        } else if (stage == 1) {
            // Charge with glowstone
            if (anchorPos == null) return;

            int slot = findItem(Blocks.GLOWSTONE);
            if (slot == -1) { stage = 2; return; }

            int prev = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = slot;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(
                    new net.minecraft.util.math.Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 1, anchorPos.getZ() + 0.5),
                    Direction.UP, anchorPos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.getInventory().selectedSlot = prev;

        } else if (stage == 2) {
            // Detonate
            if (anchorPos == null) { stage = 0; return; }

            mc.player.getInventory().selectedSlot = 0; // empty hand or any item
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(
                    new net.minecraft.util.math.Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 1, anchorPos.getZ() + 0.5),
                    Direction.UP, anchorPos, false));
            mc.player.swingHand(Hand.MAIN_HAND);

            if (doubleAnchor.isEnabled()) {
                stage = 0; // repeat
            } else {
                setEnabled(false);
            }
        }
    }

    private int findItem(Block block) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem) {
                if (((net.minecraft.item.BlockItem) stack.getItem()).getBlock() == block) return i;
            }
        }
        return -1;
    }
}