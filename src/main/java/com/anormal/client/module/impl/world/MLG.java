package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

import com.anormal.client.setting.BooleanSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class MLG extends Module {
    public final BooleanSetting useBuckets = new BooleanSetting("Use Buckets", "Places water bucket to break fall", true);
    public final BooleanSetting pickUp = new BooleanSetting("Pick Up Water", "Picks water back up after landing", true);

    private boolean placedWater = false;
    private BlockPos placedPos = null;

    public MLG() {
        super("MLG", "Automatically uses water buckets or cobwebs to prevent fall damage", Category.WORLD);
        addSetting(useBuckets);
        addSetting(pickUp);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (placedWater && pickUp.isEnabled() && mc.player.isOnGround()) {
            // Pick up water bucket
            if (placedPos != null && mc.world.getBlockState(placedPos).getBlock() == net.minecraft.block.Blocks.WATER) {
                int emptyBucket = -1;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).getItem() == Items.BUCKET) {
                        emptyBucket = i;
                        break;
                    }
                }
                if (emptyBucket != -1) {
                    mc.player.getInventory().selectedSlot = emptyBucket;
                    BlockHitResult bhr = new BlockHitResult(new Vec3d(placedPos.getX() + 0.5, placedPos.getY() + 0.5, placedPos.getZ() + 0.5), Direction.UP, placedPos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
            placedWater = false;
            placedPos = null;
            return;
        }

        if (mc.player.fallDistance > 3.0f && mc.player.getVelocity().y < -0.6) {
            BlockPos groundPos = mc.player.getBlockPos().down();
            if (!mc.world.isAir(groundPos)) {
                int waterSlot = -1;
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getStack(i).getItem() == Items.WATER_BUCKET) {
                        waterSlot = i;
                        break;
                    }
                }

                if (waterSlot != -1) {
                    mc.player.getInventory().selectedSlot = waterSlot;
                    BlockPos airPos = mc.player.getBlockPos();
                    BlockHitResult bhr = new BlockHitResult(new Vec3d(airPos.getX() + 0.5, groundPos.getY() + 1.0, airPos.getZ() + 0.5), Direction.UP, groundPos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    placedWater = true;
                    placedPos = airPos;
                }
            }
        }
    }
}
