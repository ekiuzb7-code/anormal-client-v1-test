package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;

public class PropHunt extends Module {
    public final BooleanSetting showCount = new BooleanSetting("Show Count", "Show prop count on HUD", true);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Max prop scan distance", 40.0, 10.0, 100.0, 5.0);
    private int props = 0;

    public PropHunt() {
        super("PropHunt", "Renders hidden props", Category.UZNY11);
        addSetting(showCount); addSetting(maxDistance);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            int n = 0;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof FallingBlockEntity)) continue;
                if (mc.player.distanceTo(e) > maxDistance.getValue()) continue;
                n++;
            }
            props = n;
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showCount.getValue() || mc.textRenderer == null || mc.getWindow() == null) return;
        try {
            context.drawText(mc.textRenderer, "Props: " + props, 10, 60, 0xFFFFC870, true);
        } catch (Throwable ignored) {}
    }
}
