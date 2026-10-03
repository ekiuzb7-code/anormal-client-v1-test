package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class ShulkerLabels extends Module {
    public final BooleanSetting setHere = new BooleanSetting("Set Label Here", "Label nearest shulker box", false);
    public final NumberSetting maxLabels = new NumberSetting("Max Labels", "Max stored labels", 5.0, 1.0, 15.0, 1.0);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on label", true);

    private static final class Label {
        final BlockPos pos; final String name;
        Label(BlockPos pos, String name) { this.pos = pos; this.name = name; }
    }

    private final List<Label> labels = new ArrayList<>();

    public ShulkerLabels() {
        super("ShulkerLabels", "Floating labels on shulker boxes", Category.PLAYER);
        addSetting(setHere);
        addSetting(maxLabels);
        addSetting(showDistance);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (!setHere.isEnabled()) return;
            setHere.setValue(false);
            BlockPos origin = mc.player.getBlockPos();
            BlockPos best = null;
            double bestD = 25.0;
            for (int x = -5; x <= 5; x++)
                for (int y = -5; y <= 5; y++)
                    for (int z = -5; z <= 5; z++) {
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (!path.contains("shulker_box")) continue;
                            double d = x * x + y * y + z * z;
                            if (d < bestD) {
                                bestD = d;
                                best = p.toImmutable();
                            }
                        } catch (Throwable ignored) {}
                    }
            if (best == null) return;
            for (Label l : labels) {
                if (l.pos.equals(best)) return;
            }
            labels.add(new Label(best, "Shulker " + (labels.size() + 1)));
            while (labels.size() > maxLabels.getValue().intValue()) labels.remove(0);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null || labels.isEmpty()) return;
        try {
            for (Label l : labels) {
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(l.pos.getX() + 0.5, l.pos.getY() + 1.0, l.pos.getZ() + 0.5), tickDelta);
                if (sc == null) continue;
                String text = l.name;
                if (showDistance.isEnabled()) {
                    try {
                        text += " " + (int) Math.sqrt(l.pos.getSquaredDistance(mc.player.getBlockPos())) + "m";
                    } catch (Throwable ignored) {}
                }
                RenderUtils.drawText(context, mc.textRenderer, text, sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] - 12, 0xFFC080FF, true);
                context.fill(sc[0] - 2, sc[1] - 2, sc[0] + 2, sc[1] + 2, 0xFFC080FF);
            }
        } catch (Throwable ignored) {}
    }

}
