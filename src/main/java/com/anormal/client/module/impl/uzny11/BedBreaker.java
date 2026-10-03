package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BedBreaker extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Bed scan radius in blocks", 4.0, 2.0, 6.0, 1.0);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between break attempts", 10.0, 1.0, 40.0, 1.0);
    private int ticks = 0;

    public BedBreaker() {
        super("BedBreaker", "BedWars helper that breaks beds in range", Category.UZNY11);
        addSetting(range);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        try {
            if (ticks++ < delay.getValue().intValue()) return;
            ticks = 0;
            BlockPos pp = mc.player.getBlockPos();
            int r = range.getValue().intValue();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos bp = pp.add(dx, dy, dz);
                        Identifier id = Registries.BLOCK.getId(mc.world.getBlockState(bp).getBlock());
                        if (id != null && id.getPath().contains("bed")) {
                            mc.interactionManager.attackBlock(bp, Direction.UP);
                            mc.player.swingHand(Hand.MAIN_HAND);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
