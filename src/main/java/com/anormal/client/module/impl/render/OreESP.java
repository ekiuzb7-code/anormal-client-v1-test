package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class OreESP extends Module {
    public final BooleanSetting diamond = new BooleanSetting("Diamond", "Mark diamond ores", true);
    public final BooleanSetting ancientDebris = new BooleanSetting("Ancient Debris", "Mark ancient debris", true);
    public final BooleanSetting gold = new BooleanSetting("Gold", "Mark gold ores", true);
    public final BooleanSetting iron = new BooleanSetting("Iron", "Mark iron ores", false);
    public final BooleanSetting emerald = new BooleanSetting("Emerald", "Mark emerald ores", true);
    public final BooleanSetting lapis = new BooleanSetting("Lapis", "Mark lapis ores", false);
    public final BooleanSetting redstone = new BooleanSetting("Redstone", "Mark redstone ores", false);
    public final BooleanSetting coal = new BooleanSetting("Coal", "Mark coal ores", false);
    public final BooleanSetting copper = new BooleanSetting("Copper", "Mark copper ores", false);
    public final BooleanSetting quartz = new BooleanSetting("Quartz", "Mark nether quartz ores", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Box outline", true);
    public final NumberSetting range = new NumberSetting("Range", "Scan radius", 32.0, 8.0, 64.0, 4.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max ores marked", 40.0, 5.0, 120.0, 5.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(0, 230, 255, 255));

    private final List<BlockPos> cache = new ArrayList<>();
    private int ticks = 0;

    public OreESP() {
        super("OreESP", "Marks ores with per-ore toggles", Category.RENDER);
        addSetting(diamond);
        addSetting(ancientDebris);
        addSetting(gold);
        addSetting(iron);
        addSetting(emerald);
        addSetting(lapis);
        addSetting(redstone);
        addSetting(coal);
        addSetting(copper);
        addSetting(quartz);
        addSetting(showDistance);
        addSetting(tracer);
        addSetting(outline);
        addSetting(range);
        addSetting(maxShown);
        addSetting(color);
    }

    private boolean wanted(String path) {
        if (path.contains("diamond") && diamond.isEnabled()) return true;
        if (path.contains("ancient_debris") && ancientDebris.isEnabled()) return true;
        if (path.contains("gold") && gold.isEnabled()) return true;
        if (path.contains("iron") && iron.isEnabled()) return true;
        if (path.contains("emerald") && emerald.isEnabled()) return true;
        if (path.contains("lapis") && lapis.isEnabled()) return true;
        if (path.contains("redstone") && redstone.isEnabled()) return true;
        if ((path.contains("coal") || path.contains("carbon")) && coal.isEnabled()) return true;
        if (path.contains("copper") && copper.isEnabled()) return true;
        if (path.contains("quartz") && quartz.isEnabled()) return true;
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 12) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < maxShown.getValue().intValue(); x++)
                for (int y = -r; y <= r && cache.size() < maxShown.getValue().intValue(); y++)
                    for (int z = -r; z <= r && cache.size() < maxShown.getValue().intValue(); z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (wanted(path)) cache.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;
        int col = color.getValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        for (BlockPos p : cache) {
            int[] sc = com.anormal.client.util.ProjectionUtil.project(
                    new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (sc == null) continue;
            if (outline.isEnabled()) {
                context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] + 3, (col & 0x00FFFFFF) | 0x55000000);
                context.fill(sc[0] - 3, sc[1] - 3, sc[0] + 3, sc[1] - 2, col);
                context.fill(sc[0] - 3, sc[1] + 2, sc[0] + 3, sc[1] + 3, col);
                context.fill(sc[0] - 3, sc[1] - 2, sc[0] - 2, sc[1] + 2, col);
                context.fill(sc[0] + 2, sc[1] - 2, sc[0] + 3, sc[1] + 2, col);
            } else {
                context.fill(sc[0] - 2, sc[1] - 2, sc[0] + 2, sc[1] + 2, col);
            }
            if (tracer.isEnabled()) {
                int steps = Math.min(200, Math.max(Math.abs(sc[0] - w / 2), Math.abs(sc[1] - h / 2)));
                for (int i = 0; i <= steps; i++) {
                    int x = w / 2 + (sc[0] - w / 2) * i / Math.max(1, steps);
                    int y = h / 2 + (sc[1] - h / 2) * i / Math.max(1, steps);
                    context.fill(x, y, x + 1, y + 1, col);
                }
            }
            if (showDistance.isEnabled() && mc.textRenderer != null) {
                double dx = p.getX() - mc.player.getX(), dy = p.getY() - mc.player.getY(), dz = p.getZ() - mc.player.getZ();
                String d = "" + (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
                RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + 5, sc[1] - 4, 0xFFAAAAAA, true);
            }
        }
    }
}
