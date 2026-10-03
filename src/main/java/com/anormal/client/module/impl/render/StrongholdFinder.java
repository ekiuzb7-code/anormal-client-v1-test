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

public class StrongholdFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 64.0, 16.0, 128.0, 8.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new frames in chat", true);
    public final ColorSetting color = new ColorSetting("Color", "Frame marker color", ColorUtils.rgba(120, 220, 60, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max frame markers drawn", 8.0, 1.0, 32.0, 1.0);

    private final List<BlockPos> cache = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private int ticks = 0;

    public StrongholdFinder() {
        super("StrongholdFinder", "Finds end portal frames marking strongholds", Category.RENDER);
        addSetting(range);
        addSetting(chatLog);
        addSetting(color);
        addSetting(maxShown);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 40) return;
        ticks = 0;
        cache.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < 64; x += 2)
                for (int y = -24; y <= 24 && cache.size() < 64; y += 2)
                    for (int z = -r; z <= r && cache.size() < 64; z += 2) {
                        if (x * x + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            if (Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath().equals("end_portal_frame")) cache.add(p.toImmutable());
                        } catch (Throwable ignored) {}
                    }
        } catch (Throwable ignored) {}
        if (chatLog.isEnabled() && mc.inGameHud != null) {
            for (BlockPos p : cache) {
                String key = p.getX() + "," + p.getY() + "," + p.getZ();
                if (logged.add(key)) {
                    try { mc.inGameHud.getChatHud().addMessage(Text.literal("§a[Stronghold] §fframe at " + key)); } catch (Throwable ignored) {}
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;
        try {
            int col = color.getValue(), drawn = 0, limit = maxShown.getValue().intValue();
            for (BlockPos p : cache) {
                if (drawn++ >= limit) break;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                if (sc == null) continue;
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x66000000);
                RenderUtils.drawBorder(context, sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, 1, col);
                if (mc.textRenderer != null) {
                    String d = (int) Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos())) + "m";
                    RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + 6, sc[1] - 4, col, true);
                }
            }
        } catch (Throwable ignored) {}
    }

}
