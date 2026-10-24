package com.lucidclient.ui.theme;

/**
 * Native design tokens — a 1:1 port of the {@code lucid-native} palette
 * ({@code payload/src/ui/Theme.hpp} + the row palette in
 * {@code payload/src/ui/ClickGui.cpp}) so the Fabric ClickGUI is the same
 * interface as the native client's.
 *
 * <h3>Design language</h3>
 * One dark slab, one accent. No gradients on controls, no second hue: state is
 * said with opacity and with the accent, so a dense module list stays scannable
 * over a bright world. The plugins:
 * <ul>
 *   <li><b>{@link #MODULE_BG}</b> rows on a near-black body, hover one step up.</li>
 *   <li><b>{@code darker(accent, 2)}</b> as the "enabled" row fill — the accent
 *       stepped towards black rather than faded, so a translucent row can't pick
 *       up whatever is behind it and stop matching the accent elsewhere.</li>
 *   <li><b>{@link #TEXT_DIM} → {@link #TEXT_MID} → {@link #TEXT_ON}</b> for
 *       idle → hover → enabled text.</li>
 * </ul>
 *
 * Every value is an ARGB int. Helpers here are the only place colour maths
 * happens for this menu.
 */
public final class NativeTheme {
    private NativeTheme() {}

    // ══════════════════════════════════════════════════════════════
    //  Structural palette (Theme.hpp)
    // ══════════════════════════════════════════════════════════════
    /** The window body. Flat — a gradient across a slab this large reads as
     *  a lighting effect rather than as a surface. */
    public static final int PANEL          = 0xFF171A21;
    /** The rail, a shade above the body at the top and falling away below. */
    public static final int SIDEBAR        = 0xFF1A1C21;
    /** Popup / picker chrome. */
    public static final int HEADER         = 0xFF121419;

    /** Black @ 19.6% — control surfaces over the panel body. */
    public static final int SURFACE        = 0x32000000;
    /** Black @ 7.8% — idle hover wash. */
    public static final int SURFACE_HOVER  = 0x14000000;
    /** Black @ 13.7% — pressed / active wash. */
    public static final int SURFACE_ACTIVE = 0x23000000;
    /** White @ 6% — a hairline that only suggests an edge. */
    public static final int BORDER         = 0x0FFFFFFF;
    /** White @ 14% — an emphasised hairline. */
    public static final int BORDER_STRONG  = 0x24FFFFFF;
    /** White @ 20% — scrollbar thumb. */
    public static final int SCROLLBAR      = 0x33FFFFFF;

    // ══════════════════════════════════════════════════════════════
    //  ClickGUI row palette (ClickGui.cpp)
    // ══════════════════════════════════════════════════════════════
    /** Panel header, idle. */
    public static final int BASE         = 0xFF121212;
    /** Panel header, hovered. */
    public static final int HOVER_BG     = 0xFF1C1C1C;
    /** Panel header, expanded. */
    public static final int ACTIVE_BG    = 0xFF1D1D1D;
    /** Module row fill. */
    public static final int MODULE_BG    = 0xFF282828;
    /** Module row fill, hovered. */
    public static final int MODULE_HOVER = 0xFF323232;
    /** Setting row fill. */
    public static final int SETTING_BG   = 0xFF1D1D1D;
    /** Choice list / recessed well. */
    public static final int SETTING_DEEP = 0xFF141414;
    /** Slider / meter track. */
    public static final int TRACK_BG     = 0xFF141414;
    /** Toggle + swatch outline. */
    public static final int OUTLINE_DARK = 0xFF363636;

    /** Idle label. */
    public static final int TEXT_DIM   = 0xFFAAAAAA;
    /** Hovered label. */
    public static final int TEXT_MID   = 0xFFDDDDDD;
    /** Enabled label. */
    public static final int TEXT_ON    = 0xFFE8E8E8;
    public static final int TEXT_BRIGHT = 0xFFFFFFFF;
    /** Chevron, idle. */
    public static final int ITEM_IDLE  = 0xFF666666;
    /** Keybind brackets. */
    public static final int BIND_IDLE  = 0xFF949494;

    /** Black @ 47% — the scrim the whole menu sits on. */
    public static final int SCRIM  = 0x78000000;
    /** Black @ 55% — the picker's drop shadow. */
    public static final int SHADOW = 0x8C000000;

    // ── Status ─────────────────────────────────────────────────
    public static final int POSITIVE = 0xFF5BE59A;
    public static final int WARNING  = 0xFFFFC55C;
    public static final int NEGATIVE = 0xFFFF5C6E;

    // ══════════════════════════════════════════════════════════════
    //  Accent — a live setting, so the whole client moves together
    //  when it changes (default: the native lavender).
    // ══════════════════════════════════════════════════════════════
    private static int accent = 0xFFBDA4BD;

    public static int accent() { return accent; }

    public static void setAccent(int argb) { accent = argb; }

    /**
     * The accent, moved along its own axis rather than to a different hue.
     *
     * <p>{@code lightness} below one steps towards black and above one towards
     * white; {@code alpha} scales the result (1.0 = the accent's own alpha).
     */
    public static int accentShade(float lightness, float alpha) {
        if (lightness <= 1f) {
            float scale = clamp01(lightness);
            return scaleRgb(accent, scale, alpha);
        }
        float towards = clamp01(lightness - 1f);
        int r = lerpChannel(red(accent), 255, towards);
        int g = lerpChannel(green(accent), 255, towards);
        int b = lerpChannel(blue(accent), 255, towards);
        return pack(Math.round(alphaOf(accent) * clamp01(alpha)), r, g, b);
    }

    /** {@code times} steps of the accent towards black, alpha untouched. */
    public static int darker(int color, int times) {
        int r = red(color), g = green(color), b = blue(color);
        for (int i = 0; i < times; i++) {
            r = (int) (r * 0.7f);
            g = (int) (g * 0.7f);
            b = (int) (b * 0.7f);
        }
        return pack(alphaOf(color), r, g, b);
    }

    /** Linear blend, delegating to the shared palette helper. */
    public static int mix(int a, int b, float t) {
        return Colors.lerp(a, b, t);
    }

    /** Scale an existing colour's alpha by {@code factor} (0..1). */
    public static int opacity(int color, float factor) {
        int a = Math.round(alphaOf(color) * clamp01(factor));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Replace a colour's alpha channel outright (0-255). */
    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    // ── Channel accessors ──────────────────────────────────────
    public static int alphaOf(int c) { return (c >>> 24) & 0xFF; }
    public static int red(int c)     { return (c >> 16) & 0xFF; }
    public static int green(int c)   { return (c >> 8) & 0xFF; }
    public static int blue(int c)    { return c & 0xFF; }

    public static int pack(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    // ── HSV (the colour picker's own axis) ─────────────────────
    /** Hue 0..1 of an ARGB colour. */
    public static float hueOf(int color) {
        float r = red(color) / 255f, g = green(color) / 255f, b = blue(color) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (delta <= 0.0001f) return 0f;
        float hue;
        if (max == r) hue = ((g - b) / delta) % 6f;
        else if (max == g) hue = (b - r) / delta + 2f;
        else hue = (r - g) / delta + 4f;
        hue /= 6f;
        return hue < 0f ? hue + 1f : hue;
    }

    /** Saturation 0..1 of an ARGB colour. */
    public static float saturationOf(int color) {
        float max = Math.max(red(color), Math.max(green(color), blue(color))) / 255f;
        float min = Math.min(red(color), Math.min(green(color), blue(color))) / 255f;
        return max <= 0f ? 0f : (max - min) / max;
    }

    /** Value (brightness) 0..1 of an ARGB colour. */
    public static float valueOf(int color) {
        return Math.max(red(color), Math.max(green(color), blue(color))) / 255f;
    }

    /** HSV → ARGB, the way the picker writes colours back. */
    public static int hsv(float hue, float saturation, float value, int alpha) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        float s = clamp01(saturation), v = clamp01(value);
        int i = (int) h;
        float f = h - i;
        float p = v * (1f - s);
        float q = v * (1f - s * f);
        float t = v * (1f - s * (1f - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return pack(alpha, Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }

    /** Health drives hue from the positive to the negative end of the palette,
     *  so a dying target reads at a glance without adding a fourth colour. */
    public static int healthColour(float health, float maxHealth) {
        float fraction = maxHealth <= 0f ? 0f : clamp01(health / maxHealth);
        if (fraction > 0.5f) return mix(WARNING, POSITIVE, (fraction - 0.5f) * 2f);
        return mix(NEGATIVE, WARNING, fraction * 2f);
    }

    // ── Internals ──────────────────────────────────────────────
    private static int scaleRgb(int color, float scale, float alpha) {
        return pack(Math.round(alphaOf(color) * clamp01(alpha)),
            Math.round(red(color) * scale),
            Math.round(green(color) * scale),
            Math.round(blue(color) * scale));
    }

    private static int lerpChannel(int from, int to, float t) {
        return Math.round(from + (to - from) * t);
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
}
