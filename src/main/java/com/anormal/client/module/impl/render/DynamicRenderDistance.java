package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class DynamicRenderDistance extends Module {
    public final NumberSetting targetFps = new NumberSetting("Target FPS", "FPS to tune toward", 120.0, 30.0, 240.0, 10.0);
    public final NumberSetting minChunks = new NumberSetting("Min Chunks", "Lowest render distance", 4.0, 2.0, 16.0, 1.0);
    public final NumberSetting maxChunks = new NumberSetting("Max Chunks", "Highest render distance", 12.0, 4.0, 32.0, 1.0);

    private Integer saved = null;
    private int ticks = 0;

    public DynamicRenderDistance() {
        super("DynamicRenderDistance", "Auto-tunes render distance for target FPS", Category.RENDER);
        addSetting(targetFps);
        addSetting(minChunks);
        addSetting(maxChunks);
    }

    @Override
    public void onEnable() {
        saved = getViewDistance();
        ticks = 0;
    }

    @Override
    public void onDisable() {
        if (saved != null) {
            setViewDistance(saved);
            saved = null;
        }
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        if (++ticks < 40) return;
        ticks = 0;
        try {
            Integer cur = getViewDistance();
            if (cur == null) return;
            int fps = mc.getCurrentFps();
            int target = targetFps.getValue().intValue();
            int min = Math.min(minChunks.getValue().intValue(), maxChunks.getValue().intValue());
            int max = Math.max(minChunks.getValue().intValue(), maxChunks.getValue().intValue());
            if (fps < target - 5 && cur > min) setViewDistance(cur - 1);
            else if (fps > target + 10 && cur < max) setViewDistance(cur + 1);
        } catch (Throwable ignored) {}
    }

    private Integer getViewDistance() {
        try {
            Object opt = mc.options.getClass().getMethod("getViewDistance").invoke(mc.options);
            Object v = opt.getClass().getMethod("getValue").invoke(opt);
            if (v instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {}
        return null;
    }

    private void setViewDistance(int v) {
        try {
            Object opt = mc.options.getClass().getMethod("getViewDistance").invoke(mc.options);
            opt.getClass().getMethod("setValue", Object.class).invoke(opt, v);
        } catch (Throwable ignored) {}
    }
}
