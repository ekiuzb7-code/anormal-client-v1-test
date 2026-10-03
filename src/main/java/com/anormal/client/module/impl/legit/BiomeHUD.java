package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class BiomeHUD extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 120.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showName = new BooleanSetting("Show Biome Name", "Show biome name", true);
    public final BooleanSetting showTemp = new BooleanSetting("Show Temperature", "Show temperature", true);
    public final BooleanSetting showWeather = new BooleanSetting("Show Weather", "Show weather", false);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", false);
    public final BooleanSetting compact = new BooleanSetting("Compact Mode", "Single line format", true);

    public BiomeHUD() {
        super("BiomeHUD", "Shows current biome on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showName);
        addSetting(showTemp);
        addSetting(showWeather);
        addSetting(showCoords);
        addSetting(compact);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || mc.textRenderer == null) return;
        try {
            String biome = "???";
            String temp = "";
            try {
                var entry = mc.world.getBiome(mc.player.getBlockPos());
                try {
                    biome = entry.getKey().map(k -> k.getValue().getPath()).orElse("???");
                } catch (Throwable ignored) {}
                try {
                    temp = String.format("%.1f", entry.value().getTemperature());
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            String text;
            if (compact.isEnabled()) {
                StringBuilder sb = new StringBuilder();
                if (showName.isEnabled()) sb.append(biome);
                if (showTemp.isEnabled() && !temp.isEmpty()) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(temp);
                }
                if (showWeather.isEnabled()) {
                    if (sb.length() > 0) sb.append(" | ");
                    boolean rain;
                    try {
                        rain = mc.world.isRaining();
                    } catch (Throwable ignored) {
                        rain = false;
                    }
                    sb.append(rain ? "Rain" : "Clear");
                }
                if (showCoords.isEnabled()) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(String.format("%.0f, %.0f, %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
                }
                text = sb.toString();
            } else {
                text = (showName.isEnabled() ? "Biome: " + biome : "")
                        + (showTemp.isEnabled() && !temp.isEmpty() ? "  Temp: " + temp : "");
                try {
                    boolean rain = mc.world.isRaining();
                    if (showWeather.isEnabled()) text += "  " + (rain ? "Rain" : "Clear");
                } catch (Throwable ignored) {}
                if (showCoords.isEnabled()) {
                    text += String.format("  %.0f / %.0f / %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
                }
                text = text.trim();
            }
            if (text.isEmpty()) return;

            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {}
    }
}
