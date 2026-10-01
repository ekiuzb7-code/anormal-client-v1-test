package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Bridging mode", "Legit", "Legit", "GodBridge", "TellyBridge");
    public final BooleanSetting showBlockCount = new BooleanSetting("Block Count", "Displays blocks left in hotbar", true);

    public Scaffold() {
        super("Scaffold", "Assists with bridging techniques by placing blocks below", Category.WORLD);
        addSetting(mode);
        addSetting(showBlockCount);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (mode.is("Legit")) {
            BlockPos under = mc.player.getBlockPos().down();
            if (mc.world.isAir(under) && mc.player.isOnGround()) {
                mc.options.sneakKey.setPressed(true);
            } else {
                mc.options.sneakKey.setPressed(false);
            }
        } else {
            // Auto place
            BlockPos below = mc.player.getBlockPos().down();
            if (mc.world.isAir(below)) {
                if (mc.player.getMainHandStack().getItem() instanceof BlockItem) {
                    BlockHitResult bhr = new BlockHitResult(new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }
}
