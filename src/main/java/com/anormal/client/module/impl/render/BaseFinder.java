package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class BaseFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Blocks to count as base", 4.0, 2.0, 10.0, 1.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new bases in chat", true);
    public final ColorSetting color = new ColorSetting("Color", "Base marker color", ColorUtils.rgba(255, 220, 0, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max base markers drawn", 5.0, 1.0, 20.0, 1.0);
    private static final class Base {
        final BlockPos pos; final int count;
        Base(BlockPos pos, int count) { this.pos = pos; this.count = count; }
    }
    private final List<Base> bases = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private int ticks = 0;
    public BaseFinder() {
        super("BaseFinder", "Finds base block clusters and logs them", Category.RENDER);
        addSetting(range); addSetting(minBlocks); addSetting(chatLog); addSetting(color); addSetting(maxShown);
    }
    private boolean isBase(String p) {
        if (p.equals("bedrock")) return false;
        return p.contains("crafting_table") || p.contains("furnace") || p.contains("bed") || p.contains("chest");
    }
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 40) return;
        ticks = 0; bases.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        List<BlockPos> found = new ArrayList<>();
        try {
            for (int x = -r; x <= r && found.size() < 300; x += 2)
                for (int y = -12; y <= 12 && found.size() < 300; y += 2)
                    for (int z = -r; z <= r && found.size() < 300; z += 2) {
                        if (x * x + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            if (isBase(Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath())) found.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
        List<BlockPos> left = new ArrayList<>(found);
        while (!left.isEmpty()) {
            BlockPos seed = left.remove(0);
            List<BlockPos> group = new ArrayList<>();
            group.add(seed);
            boolean grew;
            do {
                grew = false;
                for (int i = left.size() - 1; i >= 0; i--) {
                    BlockPos p = left.get(i);
                    for (BlockPos c : group) {
                        double dx = p.getX() - c.getX(), dy = p.getY() - c.getY(), dz = p.getZ() - c.getZ();
                        if (dx * dx + dy * dy + dz * dz <= 100.0) { group.add(p); left.remove(i); grew = true; break; }
                    }
                    if (grew) break;
                }
            } while (grew);
            if (group.size() < minBlocks.getValue().intValue()) continue;
            int ax = 0, ay = 0, az = 0;
            for (BlockPos c : group) { ax += c.getX(); ay += c.getY(); az += c.getZ(); }
            BlockPos center = new BlockPos(ax / group.size(), ay / group.size(), az / group.size());
            bases.add(new Base(center, group.size()));
            String key = center.getX() + "," + center.getY() + "," + center.getZ();
            if (chatLog.isEnabled() && logged.add(key) && mc.inGameHud != null) {
                try { mc.inGameHud.getChatHud().addMessage(Text.literal("§e[Base] §f" + group.size() + " blocks at " + key)); } catch (Throwable ignored) {}
            }
        }
    }
    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || bases.isEmpty()) return;
        try {
            int col = color.getValue(), drawn = 0, limit = maxShown.getValue().intValue();
            for (Base b : bases) {
                if (drawn++ >= limit) break;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(b.pos.getX() + 0.5, b.pos.getY() + 0.5, b.pos.getZ() + 0.5), tickDelta);
                if (sc == null) continue;
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x66000000);
                RenderUtils.drawBorder(context, sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, 1, col);
                if (mc.textRenderer != null) RenderUtils.drawText(context, mc.textRenderer, "§e" + b.count + "x", sc[0] + 6, sc[1] - 4, 0xFFFFFFFF, true);
            }
        } catch (Throwable ignored) {}
    }
}
