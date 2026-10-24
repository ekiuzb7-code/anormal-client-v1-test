package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;

/**
 * Global look for every HUD overlay: frame outline, outer glow and text glow.
 *
 * <p>Applied centrally by {@code HudStyle}, which wraps each module's measured frame - so a single
 * set of settings restyles all overlays at once instead of every module growing its own colour
 * pickers.</p>
 */
public class HudTheme extends Module {

    public final BooleanSetting outline = new BooleanSetting("Outline",
            "Draw a frame around every HUD overlay", false);
    public final ColorSetting outlineColor = new ColorSetting("Outline Color",
            "Frame colour", ColorUtils.rgba(255, 255, 255, 90));
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width",
            "Frame thickness in pixels", 1.0, 1.0, 3.0, 1.0);

    public final BooleanSetting glow = new BooleanSetting("Glow",
            "Soft halo around every HUD overlay", false);
    public final ColorSetting glowColor = new ColorSetting("Glow Color",
            "Halo colour", ColorUtils.rgba(90, 169, 255, 255));
    public final NumberSetting glowStrength = new NumberSetting("Glow Strength",
            "How far the halo spreads", 2.0, 1.0, 5.0, 1.0);

    public final BooleanSetting textGlow = new BooleanSetting("Text Glow",
            "Halo behind HUD text (costs extra draws)", false);
    public final ColorSetting textGlowColor = new ColorSetting("Text Glow Color",
            "Text halo colour", ColorUtils.rgba(90, 169, 255, 255));

    private static HudTheme instance;

    public HudTheme() {
        super("HudTheme", "Global frame outline, glow and text glow for HUD overlays",
                Category.CLIENT);
        addSetting(outline);
        addSetting(outlineColor);
        addSetting(outlineWidth);
        addSetting(glow);
        addSetting(glowColor);
        addSetting(glowStrength);
        addSetting(textGlow);
        addSetting(textGlowColor);
        instance = this;
    }

    public static HudTheme getInstance() {
        return instance;
    }
}
