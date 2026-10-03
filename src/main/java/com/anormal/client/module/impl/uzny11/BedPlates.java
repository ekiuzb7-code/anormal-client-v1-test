package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.block.BedBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

public class BedPlates extends Module {
    public final BooleanSetting showDistance = new BooleanSetting("Show distance", "Show bed distance", true);
    private int beds = 0;
    private double nearest = 0;

    public BedPlates() {
        super("BedPlates", "Shows block types around beds", Category.UZNY11);
        addSetting(showDistance);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            int n = 0; double best = Double.MAX_VALUE;
            BlockPos p = mc.player.getBlockPos();
            for (int x = -12; x <= 12; x++) for (int y = -6; y <= 6; y++) for (int z = -12; z <= 12; z++) {
                BlockPos b = p.add(x, y, z);
                try {
                    if (mc.world.getBlockState(b).getBlock() instanceof BedBlock) {
                        n++;
                        double d = Math.sqrt(x * x + y * y + z * z);
                        if (d < best) best = d;
                    }
                } catch (Throwable ignored) {}
            }
            beds = n; nearest = best == Double.MAX_VALUE ? 0 : best;
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null || mc.getWindow() == null) return;
        try {
            String t = showDistance.getValue() ? ("Beds: " + beds + " (" + (int) nearest + "m)") : ("Beds: " + beds);
            context.drawText(mc.textRenderer, t, 10, 70, 0xFFF50025, true);
        } catch (Throwable ignored) {}
    }
}
