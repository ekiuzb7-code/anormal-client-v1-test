package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class Coords extends Module {
    public final BooleanSetting showBiome = new BooleanSetting("Show Biome", "Displays current biome name", true);
    public final BooleanSetting showFacing = new BooleanSetting("Show Facing", "Displays player facing direction", true);

    public Coords() {
        super("Coords", "Displays player XYZ coordinates, biome, and facing direction", Category.LEGIT);
        addSetting(showBiome);
        addSetting(showFacing);
        setEnabled(true);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        int screenHeight = mc.getWindow().getScaledHeight();
        int y = screenHeight - 14;

        String coordsText = String.format("XYZ: %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (showFacing.isEnabled()) {
            coordsText += " (" + mc.player.getHorizontalFacing().asString().toUpperCase() + ")";
        }

        int width = mc.textRenderer.getWidth(coordsText);
        RenderUtils.fill(context, 4, y - 2, 8 + width, y + 10, ThemeManager.getBackgroundColor());
        RenderUtils.drawBorder(context, 4, y - 2, 8 + width, y + 10, 1, ThemeManager.getBorderColor());

        context.drawTextWithShadow(mc.textRenderer, coordsText, 6, y, 0xFFFFFFFF);
    }
}
