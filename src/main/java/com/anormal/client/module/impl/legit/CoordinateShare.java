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
import net.minecraft.text.Text;

public class CoordinateShare extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 10.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 360.0, 0.0, 1080.0, 1.0);
    public final BooleanSetting logInChat = new BooleanSetting("Log In Chat", "Print share text in local chat", false);
    public final ColorSetting textColor = new ColorSetting("Text Color", "Share text color", ColorUtils.rgba(255, 255, 255, 255));

    public CoordinateShare() {
        super("CoordinateShare", "Share-ready coords text plus chat log", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(logInChat);
        addSetting(textColor);
    }

    private String dim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private String shareText() {
        return String.format("XYZ: %.0f / %.0f / %.0f (%s)", mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim());
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (logInChat.isEnabled()) {
                logInChat.setValue(false);
                if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal("Share: " + shareText()));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;
        try {
            String text = shareText();
            int x = posX.getValue().intValue();
            int y = posY.getValue().intValue();
            int w = mc.textRenderer.getWidth(text);
            RenderUtils.fill(context, x - 4, y - 3, x + w + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + w + 4, y + 11, 1, ThemeManager.getBorderColor());
            RenderUtils.drawText(context, mc.textRenderer, text, x, y, textColor.getValue(), true);
        } catch (Throwable ignored) {}
    }
}
