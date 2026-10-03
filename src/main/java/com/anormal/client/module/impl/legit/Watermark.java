package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;

public class Watermark extends Module {
    public final NumberSetting posX = new NumberSetting("Pos X", "X Position on screen", 4.0, 0.0, 1920.0, 1.0);
    public final NumberSetting posY = new NumberSetting("Pos Y", "Y Position on screen", 4.0, 0.0, 1080.0, 1.0);
    public final ModeSetting style = new ModeSetting("Style", "Watermark text style", "Full", "Full", "Short", "Minimal");
    public final BooleanSetting showVersion = new BooleanSetting("Show Version", "Show client version", true);
    public final BooleanSetting shadow = new BooleanSetting("Shadow", "Text drop shadow", true);
    public final BooleanSetting background = new BooleanSetting("Background", "Dark background panel", true);
    public final ColorSetting textColor = new ColorSetting("Text Color", "ANORMAL text color", ColorUtils.rgba(255, 255, 255, 255));
    public final ColorSetting accentColor = new ColorSetting("Accent Color", "Version text color", ColorUtils.rgba(255, 170, 0, 255));
    public final BooleanSetting useTexture = new BooleanSetting("Use Texture", "Draw logo image instead of text", true);
    public final ModeSetting logo = new ModeSetting("Logo", "Logo 1 wordmark or Logo 2 banner", "Logo 2", "Logo 1", "Logo 2");
    public final NumberSetting scale = new NumberSetting("Scale", "Logo image scale", 0.25, 0.1, 1.0, 0.05);

    public Watermark() {
        super("Watermark", "ANORMAL logo watermark overlay", Category.LEGIT);
        addSetting(posX);
        addSetting(posY);
        addSetting(style);
        addSetting(showVersion);
        addSetting(shadow);
        addSetting(background);
        addSetting(textColor);
        addSetting(accentColor);
        addSetting(useTexture);
        addSetting(logo);
        addSetting(scale);
    }

    private static final net.minecraft.util.Identifier LOGO1 =
            net.minecraft.util.Identifier.of("anormalclient", "watermark.png");
    private static final net.minecraft.util.Identifier LOGO2 =
            net.minecraft.util.Identifier.of("anormalclient", "logo_banner.png");
    private static boolean texturesRegistered = false;
    private static String texStatus = "not tried";

    public static net.minecraft.util.Identifier guiLogo() {
        ensureTextures();
        return LOGO1;
    }

    // Upload PNGs as real GPU textures once. Loose files sampled directly
    // render as garbage — registered textures draw exactly.
    private static void ensureTextures() {
        if (texturesRegistered) return;
        texturesRegistered = true;
        registerOne(LOGO1, "/assets/anormalclient/watermark.png");
        registerOne(LOGO2, "/assets/anormalclient/logo_banner.png");
    }

    private static void registerOne(net.minecraft.util.Identifier id, String path) {
        try {
            java.io.InputStream in = null;
            String via = "?";
            // 1) Proper asset pipeline (same source the renderer reads)
            try {
                var opt = net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(id);
                if (opt != null && opt.isPresent()) {
                    in = opt.get().getInputStream();
                    via = "resmgr";
                }
            } catch (Throwable ignored) {}
            // 2) Classpath fallback
            if (in == null) {
                try {
                    in = Watermark.class.getResourceAsStream(path);
                    if (in != null) via = "classpath";
                } catch (Throwable ignored) {}
            }
            if (in == null) {
                texStatus = "missing:" + path;
                return;
            }
            final java.io.InputStream src = in;
            byte[] bytes;
            try (src; java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
                in.transferTo(buf);
                bytes = buf.toByteArray();
            }
            try (java.io.ByteArrayInputStream bin = new java.io.ByteArrayInputStream(bytes)) {
                net.minecraft.client.texture.NativeImage img =
                        net.minecraft.client.texture.NativeImage.read(bin);
                net.minecraft.client.MinecraftClient.getInstance().getTextureManager()
                        .registerTexture(id, new net.minecraft.client.texture.NativeImageBackedTexture(() -> "anormal", img));
                texStatus = "ok:" + via + ":" + img.getWidth() + "x" + img.getHeight();
            }
        } catch (Throwable t) {
            texStatus = "err:" + t.getClass().getSimpleName();
        }
    }

    public static String texStatus() {
        return texStatus;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.textRenderer == null) return;
        int x = posX.getValue().intValue();
        int y = posY.getValue().intValue();

        // Logo image only — no text fallback (image or nothing).
        if (useTexture.isEnabled()) {
            try {
                ensureTextures();
                boolean banner = logo.is("Logo 2");
                int tw = banner ? 1024 : 512;
                int th = banner ? 256 : 128;
                int w = Math.max(8, (int) (tw * scale.getValue()));
                int h = Math.max(2, (int) (th * scale.getValue()));
                context.drawTexturedQuad(banner ? LOGO2 : LOGO1,
                        x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
            } catch (Throwable ignored) {}
            return;
        }

        String main = style.is("Short") ? "AN" : (style.is("Minimal") ? "A" : "ANORMAL");
        String ver = showVersion.isEnabled() ? " v1.0" : "";
        int mainW = mc.textRenderer.getWidth(main);
        int verW = ver.isEmpty() ? 0 : mc.textRenderer.getWidth(ver);

        if (background.isEnabled()) {
            RenderUtils.fill(context, x - 4, y - 3, x + mainW + verW + 4, y + 11, ThemeManager.getBackgroundColor());
            RenderUtils.drawBorder(context, x - 4, y - 3, x + mainW + verW + 4, y + 11, 1, ThemeManager.getAccentColor());
        }
        RenderUtils.drawText(context, mc.textRenderer, "§l" + main, x, y, textColor.getValue(), shadow.isEnabled());
        if (!ver.isEmpty()) {
            RenderUtils.drawText(context, mc.textRenderer, ver, x + mainW + 1, y, accentColor.getValue(), shadow.isEnabled());
        }
    }
}
