package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class Coords extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 965.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting showDirection = new BooleanSetting("Direction", "Displays facing direction", true);
    public final BooleanSetting showBiome = new BooleanSetting("Biome", "Displays current biome name", false);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Coordinates text color", ColorUtils.rgba(255, 255, 255, 255));

    public Coords() {
        super("Coords", "Displays player coordinates and direction on HUD", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(showDirection);
        addSetting(showBiome);
        addSetting(textColor);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        String coordsText = String.format("XYZ: %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (showDirection.isEnabled()) {
            coordsText += " (" + mc.player.getHorizontalFacing().asString().toUpperCase() + ")";
        }

        int textWidth = mc.textRenderer.getWidth(coordsText);
        RenderUtils.fill(context, x - 4, y - 3, x + textWidth + 4, y + 11, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, x - 4, y - 3, x + textWidth + 4, y + 11, 1, ThemeManager.getBorderColor());

        RenderUtils.drawText(context, mc.textRenderer, coordsText, x, y, textColor.getValue(), true);
    }
}
