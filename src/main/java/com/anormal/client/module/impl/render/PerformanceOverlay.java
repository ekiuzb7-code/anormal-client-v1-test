package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class PerformanceOverlay extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 60.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showGraph = new BooleanSetting("Graph", "Frame-time bar graph", true);
    public final BooleanSetting showMemory = new BooleanSetting("Memory", "Show used memory", true);

    private final long[] frames = new long[120];
    private int frameIdx = 0;
    private long lastFrame = 0;

    public PerformanceOverlay() {
        super("PerformanceOverlay", "Frame-time graph, FPS low and memory", Category.RENDER);
        addSetting(posX);
        addSetting(posY);
        addSetting(showGraph);
        addSetting(showMemory);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        long now = System.nanoTime();
        if (lastFrame != 0) {
            frames[frameIdx++ % frames.length] = now - lastFrame;
        }
        lastFrame = now;

        int fps = 0;
        try {
            fps = mc.getCurrentFps();
        } catch (Throwable ignored) {}
        long worst = 0;
        int counted = 0;
        for (long f : frames) {
            if (f <= 0) continue;
            counted++;
            if (f > worst) worst = f;
        }
        int low1 = worst > 0 ? (int) (1_000_000_000L / worst) : fps;
        long usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576L;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();
        String l1 = "FPS: " + fps + "  1% low: " + low1;
        String l2 = showMemory.isEnabled() ? "Mem: " + usedMb + " MB" : "";
        int w = Math.max(mc.textRenderer.getWidth(l1), l2.isEmpty() ? 0 : mc.textRenderer.getWidth(l2));
        int h = (l2.isEmpty() ? 14 : 24) + (showGraph.isEnabled() ? 26 : 0);

        RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + h, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + h, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, mc.textRenderer, l1, x, y, 0xFF55FFFF, true);
        if (!l2.isEmpty()) RenderUtils.drawText(context, mc.textRenderer, l2, x, y + 10, 0xFFAAAAAA, true);

        if (showGraph.isEnabled()) {
            int gy = y + (l2.isEmpty() ? 14 : 24);
            int gw = Math.max(60, w);
            RenderUtils.fill(context, x, gy, x + gw, gy + 22, 0xFF101014);
            int n = Math.min(counted, 60);
            for (int i = 0; i < n; i++) {
                long f = frames[(frameIdx - 1 - i + frames.length * 2) % frames.length];
                if (f <= 0) continue;
                double ms = f / 1_000_000.0;
                int bh = (int) Math.max(1, Math.min(20, ms / 50.0 * 20.0));
                int col = ms < 17 ? 0xFF55FF55 : (ms < 34 ? 0xFFFFFF55 : 0xFFFF5555);
                int bx = x + gw - 1 - i;
                context.fill(bx, gy + 22 - bh, bx + 1, gy + 22, col);
            }
        }
    }
}
