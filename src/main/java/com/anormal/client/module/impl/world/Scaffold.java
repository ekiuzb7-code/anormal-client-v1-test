package com.anormal.client.module.impl.world;

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
        super("Scaffold", "Assists with bridging techniques by placing blocks below", Category.WORLD);
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
    private boolean sneakedByModule = false;

    @Override
    public void onDisable() {
        if (mc.options != null && sneakedByModule) {
            mc.options.sneakKey.setPressed(false);
        }
        sneakedByModule = false;
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
            boolean needSneak = false;
            try {
                needSneak = mc.world.isAir(under) && mc.player.isOnGround();
            } catch (Throwable ignored) {}
            if (needSneak) {
                if (!mc.options.sneakKey.isPressed()) {
                    mc.options.sneakKey.setPressed(true);
                    sneakedByModule = true;
                }
            } else if (sneakedByModule) {
                mc.options.sneakKey.setPressed(false);
                sneakedByModule = false;
            }
        } else {
            // Auto place after bridging progress passes Activation Blocks
            BlockPos below = mc.player.getBlockPos().down();
            boolean air;
            try {
                air = mc.world.isAir(below);
            } catch (Throwable ignored) {
                return;
            }
            if (air) {
                placedStreak++;
                if (placedStreak < activationBlocks.getValue()) return;
                placeAt(below);
            } else {
                placedStreak = 0;
            }
        }
    }

    // Places a block AT the air pos by clicking a solid neighbor's face.
    // Clicking the air itself is rejected by the server, so find support first.
    private boolean placeAt(BlockPos below) {
        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return false;
        try {
            Direction[] faces = {Direction.NORTH, Direction.SOUTH, Direction.EAST,
                    Direction.WEST, Direction.DOWN, Direction.UP};
            BlockPos support = null;
            Direction face = null;
            for (Direction d : faces) {
                BlockPos n = below.offset(d);
                try {
                    if (!mc.world.isAir(n)) {
                        support = n;
                        face = d.getOpposite();
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (support == null) return false;
            BlockHitResult bhr = new BlockHitResult(
                    new Vec3d(below.getX() + 0.5, below.getY() + 0.5, below.getZ() + 0.5),
                    face, support, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        } catch (Throwable ignored) {
            return false;
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
            if (v2Sneak.isEnabled()) {
                if (!mc.options.sneakKey.isPressed()) {
                    mc.options.sneakKey.setPressed(true);
                    sneakedByModule = true;
                }
            } else if (sneakedByModule) {
                mc.options.sneakKey.setPressed(false);
                sneakedByModule = false;
            }
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
        if (placeAt(below)) {
            v2Cooldown = v2Delay.getValue().intValue();
        }
    }
}
