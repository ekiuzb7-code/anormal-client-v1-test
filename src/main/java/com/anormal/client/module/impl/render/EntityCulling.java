package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

public class EntityCulling extends Module {
    public final NumberSetting distance = new NumberSetting("Distance", "Entities beyond this stop rendering", 64.0, 16.0, 128.0, 8.0);

    private Double saved = null;

    public EntityCulling() {
        super("EntityCulling", "Stops rendering entities beyond a distance", Category.RENDER);
        addSetting(distance);
    }

    @Override
    public void onEnable() {
        saved = getScale();
    }

    @Override
    public void onDisable() {
        if (saved != null) {
            setScale(saved);
            saved = null;
        }
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        double want = Math.max(0.5, Math.min(5.0, distance.getValue() / 64.0));
        try {
            Double cur = getScale();
            if (cur != null && Math.abs(cur - want) > 0.01) setScale(want);
        } catch (Throwable ignored) {}
    }

    private Double getScale() {
        try {
            Object opt = mc.options.getClass().getMethod("getEntityDistanceScaling").invoke(mc.options);
            Object v = opt.getClass().getMethod("getValue").invoke(opt);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Throwable ignored) {}
        return null;
    }

    private void setScale(double v) {
        try {
            Object opt = mc.options.getClass().getMethod("getEntityDistanceScaling").invoke(mc.options);
            opt.getClass().getMethod("setValue", Object.class).invoke(opt, v);
        } catch (Throwable ignored) {}
    }
}
