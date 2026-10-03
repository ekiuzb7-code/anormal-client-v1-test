package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class SpawnerFinder extends Module {
    public final NumberSetting scale = new NumberSetting("Scale", "Scale of the rendered overlays", 1.0, 0.5, 2.5, 0.1);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Shows distance to the spawner on the overlay", true);
    public final BooleanSetting showMob = new BooleanSetting("Show Mob", "Shows spawner kind (Dungeon/Trial)", true);
    public final ModeSetting filter = new ModeSetting("Spawners", "Which mob spawners to find", "All", "All", "Dungeon", "Trial");
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Only renders spawners within this range", 48.0, 8.0, 96.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Spawner overlay color", ColorUtils.rgba(255, 60, 220, 255));

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public SpawnerFinder() {
        super("SpawnerFinder", "Locates and highlights mob spawners through walls", Category.RENDER);
        addSetting(scale);
        addSetting(showDistance);
        addSetting(showMob);
        addSetting(filter);
        addSetting(maxDistance);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 20) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = maxDistance.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < 64; x++)
                for (int y = -r; y <= r && cache.size() < 64; y++)
                    for (int z = -r; z <= r && cache.size() < 64; z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        String path;
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                        } catch (Throwable t) {
                            continue;
                        }
                        if (!path.contains("spawner")) continue;
                        if (filter.is("Dungeon") && !path.equals("spawner")) continue;
                        if (filter.is("Trial") && !path.contains("trial")) continue;
                        cache.add(p.toImmutable());
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null || cache.isEmpty()) return;
        int c = color.getValue();
        int s = (int) (6 * scale.getValue());
        try {
            for (BlockPos p : cache) {
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                if (sc == null) continue;
                context.fill(sc[0] - s, sc[1] - s, sc[0] + s, sc[1] + s, (c & 0x00FFFFFF) | 0x55000000);
                context.fill(sc[0] - s, sc[1] - s, sc[0] + s, sc[1] - s + 1, c);
                context.fill(sc[0] - s, sc[1] + s - 1, sc[0] + s, sc[1] + s, c);
                context.fill(sc[0] - s, sc[1] - s + 1, sc[0] - s + 1, sc[1] + s - 1, c);
                context.fill(sc[0] + s - 1, sc[1] - s + 1, sc[0] + s, sc[1] + s - 1, c);
                if (showDistance.isEnabled()) {
                    String d = (int) Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos())) + "m";
                    RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + s + 2, sc[1] - 4, c, true);
                }
                if (showMob.isEnabled()) {
                    String kind = "Spawner";
                    try {
                        String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                        if (path.contains("trial")) kind = "Trial Spawner";
                        else if (path.equals("spawner")) kind = "Dungeon";
                        else kind = path;
                    } catch (Throwable ignored) {}
                    RenderUtils.drawText(context, mc.textRenderer, "§e" + kind, sc[0] - mc.textRenderer.getWidth(kind) / 2, sc[1] + s + 2, c, true);
                }
            }
        } catch (Throwable ignored) {}
    }


}
