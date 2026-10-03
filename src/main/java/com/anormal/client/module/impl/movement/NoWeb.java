package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NoWeb extends Module {
    public NoWeb() {
        super("NoWeb", "Escapes cobwebs fast", Category.MOVEMENT);
    }

    private boolean inWeb() {
        try {
            BlockPos feet = mc.player.getBlockPos();
            BlockPos eye = new BlockPos((int) mc.player.getX(), (int) (mc.player.getY() + 1.0), (int) mc.player.getZ());
            for (BlockPos p : new BlockPos[]{feet, feet.up(), eye}) {
                String path;
                try {
                    path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                } catch (Throwable ignored) {
                    continue;
                }
                if (path.contains("cobweb")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!inWeb()) return;
        try {
            // Hop out: upward pop + keep momentum instead of sinking
            Vec3d v = mc.player.getVelocity();
            double up = v.y < 0.15 ? 0.35 : v.y;
            mc.player.setVelocity(v.x * 1.1, up, v.z * 1.1);
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
        } catch (Throwable ignored) {}
    }
}
