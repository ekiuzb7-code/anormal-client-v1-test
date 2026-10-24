package com.anormal.client.lucid;

/**
 * Lucid Client color palette.
 * Pure white-and-black aesthetic with subtle gray accents.
 * All colors are ARGB ints (0xAARRGGBB).
 */
public final class LucidColors {
    private LucidColors() {}

    // ── Surfaces ────────────────────────────────────────────────
    /** Pure white (#FFFFFF) — primary background. */
    public static final int BACKGROUND       = 0xFFFFFFFF;
    /** #FAFAFA — secondary background. */
    public static final int BACKGROUND_ALT   = 0xFFFAFAFA;
    /** #F5F5F5 — panels. */
    public static final int PANEL            = 0xFFF5F5F5;
    /** #FFFFFF — cards (same as background but conceptually distinct). */
    public static final int CARD             = 0xFFFFFFFF;
    /** #F0F0F0 — hover state. */
    public static final int HOVER            = 0xFFF0F0F0;
    /** #E6E6E6 — borders. */
    public static final int BORDER           = 0xFFE6E6E6;
    /** Slightly darker border for emphasis. */
    public static final int BORDER_STRONG    = 0xFFD4D4D4;

    // ── Text ────────────────────────────────────────────────────
    /** #111111 — primary text. */
    public static final int TEXT_PRIMARY     = 0xFF111111;
    /** #666666 — secondary text. */
    public static final int TEXT_SECONDARY   = 0xFF666666;
    /** #999999 — muted text. */
    public static final int TEXT_MUTED       = 0xFF999999;
    /** White text (used on dark accents). */
    public static final int TEXT_INVERTED    = 0xFFFFFFFF;

    // ── Accent (Pure Black) ─────────────────────────────────────
    /** #111111 — primary accent. */
    public static final int ACCENT           = 0xFF111111;
    public static final int ACCENT_HOVER     = 0xFF2A2A2A;
    public static final int ACCENT_PRESSED   = 0xFF000000;

    // ── Status ──────────────────────────────────────────────────
    /** #22C55E — success. */
    public static final int SUCCESS          = 0xFF22C55E;
    /** #F59E0B — warning. */
    public static final int WARNING          = 0xFFF59E0B;
    /** #EF4444 — error. */
    public static final int ERROR            = 0xFFEF4444;

    // ── Frosted glass (with alpha) ─────────────────────────────
    /** Frosted white glass — 88% opacity white. */
    public static final int GLASS            = 0xE6FFFFFF;
    /** Lighter frosted glass — 78% opacity. */
    public static final int GLASS_LIGHT      = 0xC8FFFFFF;
    /** Darker scrim behind glass panels. */
    public static final int SCRIM            = 0x33000000;
    /** Very light overlay tint. */
    public static final int OVERLAY          = 0x14FFFFFF;

    // ── Shadows (drawn as solid rounded rects, low alpha) ──────
    public static final int SHADOW_SOFT      = 0x14000000;
    public static final int SHADOW_MEDIUM    = 0x1F000000;

    // ═══════════════════════════════════════════════════════════════
    //  DARK THEME — ClickGUI dark mode palette.
    //  Same minimalist black-and-white aesthetic, inverted: near-black
    //  surfaces with a white accent. Used by the License screen. The
    //  ClickGUI has its own palette in NativeTheme.
    // ═══════════════════════════════════════════════════════════════

    /** #0E0E10 — base ambient background (deepest dark). */
    public static final int DARK_BG            = 0xFF0E0E10;
    /** #151518 — slightly raised ambient. */
    public static final int DARK_BG_ALT         = 0xFF151518;
    /** #1B1B20 — category column / panel surface. */
    public static final int DARK_PANEL          = 0xFF1B1B20;
    /** #222227 — module card surface. */
    public static final int DARK_CARD           = 0xFF222227;
    /** #2A2A30 — module card hover state. */
    public static final int DARK_CARD_HOVER     = 0xFF2A2A30;
    /** #2E2E36 — soft separator border. */
    public static final int DARK_BORDER         = 0xFF2E2E36;
    /** #3A3A44 — emphasized border. */
    public static final int DARK_BORDER_STRONG  = 0xFF3A3A44;
    /** #44444C — disabled track / inactive control bg. */
    public static final int DARK_DISABLED        = 0xFF44444C;

    /** #F2F2F5 — primary text on dark surfaces. */
    public static final int DARK_TEXT_PRIMARY    = 0xFFF2F2F5;
    /** #A8A8B0 — secondary text on dark surfaces. */
    public static final int DARK_TEXT_SECONDARY  = 0xFFA8A8B0;
    /** #6A6A74 — muted text on dark surfaces. */
    public static final int DARK_TEXT_MUTED      = 0xFF6A6A74;

    /** Pure white — the accent on dark surfaces (mirror of ACCENT). */
    public static final int DARK_ACCENT          = 0xFFFFFFFF;
    /** #E0E0E0 — accent hover. */
    public static final int DARK_ACCENT_HOVER    = 0xFFE0E0E0;
    /** #0E0E10 — accent pressed (visually recedes). */
    public static final int DARK_ACCENT_PRESSED  = 0xFF0E0E10;

    /** #6E6E78 — dark grey accent for the Krypton-style ClickGUI (active
     *  module highlights, slider fills, title-bar emphasis). Subdued but
     *  visible against the dark card surface. */
    public static final int DARK_ACCENT_GREY       = 0xFF6E6E78;
    /** #8A8A94 — lighter hover variant of the grey accent. */
    public static final int DARK_ACCENT_GREY_HOVER  = 0xFF8A8A94;
    /** #3A3A44 — title bar surface for the dark ClickGUI windows. */
    public static final int DARK_TITLE_BAR          = 0xFF3A3A44;

    // ── Classic ClickGUI palette ───────────────────────────────
    // The classic Minecraft hack-mc look: pure flat near-black
    // rectangles, thin grey borders, white title text, inverted
    // (white-on-dark) accent for active toggle / slider fill.
    // No rounded corners anywhere — see drawSquareRect / drawSquareBorder.
    /** #1A1A1F — classic window panel fill (deepest dark). */
    public static final int CLASSIC_WINDOW_BG       = 0xF2000000;
    /** #000000 — top title-bar (slightly darker than body). */
    public static final int CLASSIC_TITLE_BAR        = 0xFF000000;
    /** #BEBEDCDA — module-card surface (translucent). */
    public static final int CLASSIC_MODULE_BG        = 0xD9000000;
    /** #FF007E1A — classic accent: vivid green — DEFAULT for active modules. */
    public static final int CLASSIC_ACCENT           = 0xFF00AC1A;
    /** #FF46FF2E — brighter accent hover state. */
    public static final int CLASSIC_ACCENT_HOVER     = 0xFF46FF2E;
    /** #66FFFFFF — border color for classic outline. */
    public static final int CLASSIC_BORDER           = 0x66FFFFFF;
    /** #FFFF8B00 — classic accent for warnings / clicked. */
    public static final int CLASSIC_ACCENT_CLICKED  = 0xFF8B00;
    /** #FF000000 — classic opaque black surface. */
    public static final int CLASSIC_BLACK            = 0xFF000000;
    /** #FFFFFFFF — classic opaque white text. */
    public static final int CLASSIC_WHITE            = 0xFFFFFFFF;

    // ═══════════════════════════════════════════════════════════════
    //  AURORA THEME — rounded-glass UI with blue→violet accents.
    //  Sampled from the reference screenshots: periwinkle #5B7CFF
    //  arraylist text / #7D6BFF glow accents over deep translucent
    //  glass. The remaining component classes read from
    //  this block (legacy palettes above are kept only for
    //  compatibility with the few remaining references).
    // ═══════════════════════════════════════════════════════════════

    /** #0D0D15 — deepest ambient backdrop (window body base). */
    public static final int AURORA_BG            = 0xFF0D0D15;
    /** #131320 — raised ambient surface (settings recess). */
    public static final int AURORA_BG_ALT        = 0xFF131320;
    /** 0xE614141F — window glass fill: translucent deep navy so the
     *  blurred world glows through the rounded panels. */
    public static final int AURORA_GLASS         = 0xE614141F;
    /** 0xB4191930 — lighter layered glass for cards and rows. */
    public static final int AURORA_GLASS_LIGHT   = 0xB4191930;
    /** #1B1B2E — raised row/card surface (opaque). */
    public static final int AURORA_CARD          = 0xFF1B1B2E;
    /** #232338 — hover state for rows and cards. */
    public static final int AURORA_CARD_HOVER    = 0xFF232338;
    /** 0x33FFFFFF — soft white hairline border on glass panels. */
    public static final int AURORA_BORDER        = 0x33FFFFFF;
    /** 0x66FFFFFF — emphasized border (hover / focus states). */
    public static final int AURORA_BORDER_STRONG = 0x66FFFFFF;

    /** #5B7CFF — primary periwinkle-blue accent (screenshot hue). */
    public static final int AURORA_ACCENT        = 0xFF5B7CFF;
    /** #7D8CFF — brighter accent hover. */
    public static final int AURORA_ACCENT_HOVER  = 0xFF7D8CFF;
    /** #9B5CFF — violet gradient stop. */
    public static final int AURORA_ACCENT_2      = 0xFF9B5CFF;
    /** #7D6BFF — mid blend of the accent pair (solid glow fill). */
    public static final int AURORA_GLOW          = 0xFF7D6BFF;
    /** #F2F2FA — primary text on glass. */
    public static final int AURORA_TEXT          = 0xFFF2F2FA;
    /** #A9A9C4 — secondary text. */
    public static final int AURORA_TEXT_DIM      = 0xFFA9A9C4;
    /** #6C6C88 — muted text. */
    public static final int AURORA_TEXT_FAINT    = 0xFF6C6C88;

    /** Top color of the accent gradient (blue). */
    public static final int AURORA_GRAD_TOP      = 0xFF5B7CFF;
    /** Bottom color of the accent gradient (violet). */
    public static final int AURORA_GRAD_BOTTOM   = 0xFF9B5CFF;

    /** 0x2E5B7CFF — soft accent glow layer 1 (tight). */
    public static final int AURORA_GLOW_1        = 0x2E5B7CFF;
    /** 0x1A5B7CFF — soft accent glow layer 2 (wide). */
    public static final int AURORA_GLOW_2        = 0x1A5B7CFF;

    /** Translucent panel glass for dark mode (~86% opaque near-black). */
    public static final int DARK_GLASS           = 0xDC131318;
    /** Lighter translucent dark glass for subtle layered cards. */
    public static final int DARK_GLASS_LIGHT     = 0xB022222A;
    /** Dim scrim that lets the game show through (slight blur effect). */
    public static final int DARK_SCRIM           = 0x99000000;
    /** Soft drop shadow for dark cards (low-alpha black). */
    public static final int DARK_SHADOW_SOFT     = 0x66000000;

    /**
     * Apply an alpha channel to an RGB color.
     * @param rgb the RGB color (alpha is ignored)
     * @param alpha 0-255
     */
    public static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Linearly interpolate between two colors.
     * @param a color A (ARGB)
     * @param b color B (ARGB)
     * @param t 0..1
     */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aA = (a >> 24) & 0xFF, aR = (a >> 16) & 0xFF, aG = (a >> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >> 24) & 0xFF, bR = (b >> 16) & 0xFF, bG = (b >> 8) & 0xFF, bB = b & 0xFF;
        int rA = (int) (aA + (bA - aA) * t);
        int rR = (int) (aR + (bR - aR) * t);
        int rG = (int) (aG + (bG - aG) * t);
        int rB = (int) (aB + (bB - aB) * t);
        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }
}
