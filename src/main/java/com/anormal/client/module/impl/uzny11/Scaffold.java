package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Bridging mode", "Legit", "Legit", "GodBridge", "TellyBridge");
    public final BooleanSetting showBlockCount = new BooleanSetting("Block Count", "Displays blocks left in hotbar", true);
    public final NumberSetting pitchCheck = new NumberSetting("Pitch Check", "Only when aiming below this pitch", 50.0, 0.0, 90.0, 1.0);
    public final NumberSetting activationBlocks = new NumberSetting("Activation Blocks", "Manual blocks before takeover", 3.0, 0.0, 20.0, 1.0);
    public final ModeSetting style = new ModeSetting("Style", "V1 or V2 implementation", "V1", "V1", "V2");
    public final NumberSetting v2Delay = new NumberSetting("V2 Delay", "Ticks between auto placements", 2.0, 0.0, 10.0, 1.0);
    public final BooleanSetting v2Sneak = new BooleanSetting("V2 Sneak", "Sneak while bridging", true);
    public final BooleanSetting v2YawLock = new BooleanSetting("V2 Yaw Lock", "Snap view to cardinal direction", true);

    public Scaffold() {
        super("Scaffold", "Assists with bridging techniques by placing blocks below", Category.UZNY11);
        addSetting(mode);
        addSetting(showBlockCount);
        addSetting(pitchCheck);
        addSetting(activationBlocks);
        addSetting(style);
        addSetting(v2Delay);
        addSetting(v2Sneak);
        addSetting(v2YawLock);
        v2Delay.visibleIf(() -> style.is("V2"));
        v2Sneak.visibleIf(() -> style.is("V2"));
        v2YawLock.visibleIf(() -> style.is("V2"));
    }

    private int v2Cooldown = 0;

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        placedStreak = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.getPitch() < pitchCheck.getValue()) return;

        if (style.is("V2")) {
            tickV2();
            return;
        }
        if (mode.is("Legit")) {
            BlockPos under = mc.player.getBlockPos().down();
            if (mc.world.isAir(under) && mc.player.isOnGround()) {
                mc.options.sneakKey.setPressed(true);
            } else {
                mc.options.sneakKey.setPressed(false);
            }
        } else {
            // Auto place after bridging progress passes Activation Blocks
            BlockPos below = mc.player.getBlockPos().down();
            if (mc.world.isAir(below)) {
                placedStreak++;
                if (placedStreak < activationBlocks.getValue()) return;
                if (mc.player.getMainHandStack().getItem() instanceof BlockItem) {
                    BlockHitResult bhr = new BlockHitResult(new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else {
                placedStreak = 0;
            }
        }
    }

    private int placedStreak = 0;

    // V2: timed auto-place with sneak assist and cardinal yaw lock
    private void tickV2() {
        if (v2Cooldown > 0) {
            v2Cooldown--;
            return;
        }
        try {
            mc.options.sneakKey.setPressed(v2Sneak.isEnabled());
            if (v2YawLock.isEnabled()) {
                float yaw = mc.player.getYaw();
                float snapped = Math.round(yaw / 90.0f) * 90.0f;
                mc.player.setYaw(snapped);
            }
        } catch (Throwable ignored) {}
        BlockPos below = mc.player.getBlockPos().down();
        try {
            if (!mc.world.isAir(below)) return;
        } catch (Throwable ignored) {
            return;
        }
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;
        try {
            BlockHitResult bhr = new BlockHitResult(new Vec3d(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5), Direction.UP, below, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            v2Cooldown = v2Delay.getValue().intValue();
        } catch (Throwable ignored) {}
    }
}
