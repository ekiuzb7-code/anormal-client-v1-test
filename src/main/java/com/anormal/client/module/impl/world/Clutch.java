package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Clutch extends Module {
    public final BooleanSetting onVoid = new BooleanSetting("On Void", "Saves from falling into void", true);
    public final BooleanSetting onLethal = new BooleanSetting("On Lethal Fall", "Saves from lethal fall damage", true);

    public Clutch() {
        super("Clutch", "Automatically places blocks to catch and prevent fatal falls", Category.WORLD);
        addSetting(onVoid);
        addSetting(onLethal);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.isOnGround() || mc.player.isCreative() || mc.player.isSpectator()) return;

        boolean isFallingLethal = onLethal.isEnabled() && (mc.player.fallDistance > 3.0f || mc.player.getVelocity().y < -0.5);
        boolean isFallingVoid = onVoid.isEnabled() && mc.player.getY() < 20;

        if (isFallingLethal || isFallingVoid) {
            // Find a block in hotbar
            int blockSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                    blockSlot = i;
                    break;
                }
            }

            if (blockSlot != -1) {
                mc.player.getInventory().selectedSlot = blockSlot;

                BlockPos playerPos = mc.player.getBlockPos();
                BlockPos[] checkPositions = {
                    playerPos.down(),
                    playerPos.down(2),
                    playerPos.down().north(),
                    playerPos.down().south(),
                    playerPos.down().east(),
                    playerPos.down().west()
                };

                for (BlockPos targetPos : checkPositions) {
                    if (mc.world.isAir(targetPos)) {
                        // Place block against any neighboring solid block or at floor
                        for (Direction dir : Direction.values()) {
                            BlockPos neighbor = targetPos.offset(dir);
                            if (!mc.world.isAir(neighbor)) {
                                BlockHitResult bhr = new BlockHitResult(
                                    new Vec3d(neighbor.getX() + 0.5 + dir.getOpposite().getOffsetX() * 0.5,
                                              neighbor.getY() + 0.5 + dir.getOpposite().getOffsetY() * 0.5,
                                              neighbor.getZ() + 0.5 + dir.getOpposite().getOffsetZ() * 0.5),
                                    dir.getOpposite(),
                                    neighbor,
                                    false
                                );
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                                mc.player.swingHand(Hand.MAIN_HAND);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
