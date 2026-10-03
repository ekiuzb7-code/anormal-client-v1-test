package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.BlockPos;

public class SafeWalk extends Module {
    public final BooleanSetting onGroundOnly = new BooleanSetting("On Ground Only", "Only prevent edge falls while on ground", true);
    public final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", "Only sneak on empty edges when holding blocks", false);
    public final BooleanSetting sneakAtEdges = new BooleanSetting("Sneak At Edges", "Auto-sneak near block edge", true);
    public final NumberSetting edgeDistance = new NumberSetting("Edge Distance", "Sneak within this of edge (blocks)", 0.15, 0.0, 0.5, 0.01);
    public final NumberSetting releaseDelay = new NumberSetting("Release Delay", "Ticks to wait before un-sneaking on safe ground", 2.0, 0.0, 10.0, 1.0);
    public final BooleanSetting strictMode = new BooleanSetting("Strict Mode", "More conservative edge detection", false);

    private boolean sneakedByModule = false;
    private int releaseTimer = 0;

    public SafeWalk() {
        super("SafeWalk", "Prevents walking off block edges without slowing down", Category.MOVEMENT);
        addSetting(onGroundOnly);
        addSetting(blocksOnly);
        addSetting(sneakAtEdges);
        addSetting(edgeDistance);
        addSetting(releaseDelay);
        addSetting(strictMode);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (onGroundOnly.isEnabled() && !mc.player.isOnGround()) {
            release();
            return;
        }
        if (blocksOnly.isEnabled() && !(mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem)) {
            release();
            return;
        }

        // Danger = over air (past edge) OR within edge distance of it.
        // Instant release the moment you're back inside: no late stand-up.
        boolean atEdge = false;
        try {
            BlockPos under = mc.player.getBlockPos().down();
            if (mc.world.isAir(under) && mc.player.isOnGround()) {
                atEdge = true;
            } else if (sneakAtEdges.isEnabled()) {
                double px = mc.player.getX();
                double pz = mc.player.getZ();
                double fx = px - Math.floor(px);
                double fz = pz - Math.floor(pz);
                double edgeDist = Math.min(Math.min(fx, 1.0 - fx), Math.min(fz, 1.0 - fz));

                // Player half-width (~0.3) eats into the margin on both sides
                // For edgeDistance 0.0, we want to detect right at the edge
                double margin = edgeDistance.getValue() + (strictMode.isEnabled() ? 0.35 : 0.3);

                BlockPos feet = mc.player.getBlockPos();
                boolean nearVoid = false;
                outer:
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = feet.add(dx, -1, dz);
                        try {
                            if (mc.world.isAir(n)) {
                                nearVoid = true;
                                break outer;
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                // Also check directly below player center for single-block gaps
                if (!nearVoid) {
                    BlockPos centerDown = feet.add(0, -1, 0);
                    try {
                        if (mc.world.isAir(centerDown)) nearVoid = true;
                    } catch (Throwable ignored) {}
                }

                if (nearVoid && edgeDist < margin) atEdge = true;
            }
        } catch (Throwable ignored) {}

        if (atEdge) {
            // At edge: sneak (stand still safely)
            if (!mc.options.sneakKey.isPressed()) {
                mc.options.sneakKey.setPressed(true);
                sneakedByModule = true;
            }
            releaseTimer = 0;
        } else {
            // Safe ground: wait for release delay before standing up
            int delay = releaseDelay.getValue().intValue();
            if (sneakedByModule) {
                if (releaseTimer >= delay) {
                    release();
                } else {
                    releaseTimer++;
                }
            }
        }
    }

    private void release() {
        if (sneakedByModule) {
            try {
                mc.options.sneakKey.setPressed(false);
            } catch (Throwable ignored) {}
            sneakedByModule = false;
            releaseTimer = 0;
        }
    }

    @Override
    public void onDisable() {
        release();
    }
}
