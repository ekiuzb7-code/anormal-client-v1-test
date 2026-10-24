package com.lucidclient.ui.theme;

/**
 * Centralised Lucid Client design tokens: corner radii, durations, spacing.
 * Keeps the entire UI consistent with the premium design language.
 */
public final class LucidTheme {
    private LucidTheme() {}

    // ── Corner radii (px) ───────────────────────────────────────
    public static final int RADIUS_PANEL   = 14;
    public static final int RADIUS_CARD    = 12;
    public static final int RADIUS_BUTTON  = 10;
    public static final int RADIUS_SMALL   = 8;
    public static final int RADIUS_PILL    = 999;

    // ── Animation durations (ms) ────────────────────────────────
    /** 180ms — the lower bound of the design spec. */
    public static final long DURATION_MIN   = 180L;
    /** 220ms — the upper bound of the design spec. */
    public static final long DURATION_MAX   = 220L;
    /** Standard fast transition. */
    public static final long DURATION_FAST  = 150L;
    /** Opening animation duration. */
    public static final long DURATION_OPEN  = 210L;

    // ── Spacing scale ──────────────────────────────────────────
    public static final int SPACE_XS = 4;
    public static final int SPACE_S  = 8;
    public static final int SPACE_M  = 12;
    public static final int SPACE_L  = 16;
    public static final int SPACE_XL = 24;
    public static final int SPACE_2XL = 32;

    // ── Layout ─────────────────────────────────────────────────
    public static final int PANEL_PADDING       = 16;
    public static final int PANEL_PADDING_TIGHT = 10;
    public static final int BORDER_WIDTH         = 1;

    // ── Typography ─────────────────────────────────────────────
    /** Small body text scale (0.85x). */
    public static final float SCALE_SMALL  = 0.85f;
    /** Large title text scale (1.6x). */
    public static final float SCALE_TITLE  = 1.6f;
    /** Numeric / monospace scale. */
    public static final float SCALE_MONO   = 0.9f;

    // ── Scrollbar ──────────────────────────────────────────────
    public static final int SCROLLBAR_WIDTH    = 4;
    public static final int SCROLLBAR_RADIUS    = 2;
    public static final int SCROLLBAR_COLOR     = 0x33000000;
    public static final int SCROLLBAR_HOVER     = 0x55000000;

    // ── ClickGUI layout ────────────────────────────────────────
    public static final int CATEGORY_GAP     = 14;
    public static final int CATEGORY_WIDTH   = 188;
    public static final int CATEGORY_HEADER_H = 52;
    public static final int CATEGORY_BODY_H  = 280;

    // ── ClickGUI layout (Krypton-style compact windows) ────────
    /** Compact category-window width. */
    public static final int CATEGORY_WIDTH_COMPACT   = 168;
    /** Compact category-window body height (excluding header). */
    public static final int CATEGORY_BODY_H_COMPACT  = 200;
    /** Compact category-window title-bar height. */
    public static final int CATEGORY_TITLE_H_COMPACT = 26;
    /** Header padding inside the compact title bar. */
    public static final int CATEGORY_TITLE_PAD        = 8;
    /** Default gap between stacked windows on the left edge. */
    public static final int CATEGORY_STACK_GAP        = 4;

    // ── Aurora theme: FX / glow / motion tokens ───────────────
    /** Corner radius of toggle-pill tracks in the Aurora settings panel. */
    public static final int RADIUS_TOGGLE  = 6;
    /** Extra padding around a window for its outer glow (px). */
    public static final int GLOW_SPREAD    = 3;
    /** Hover/brightness animation speed used by Aurora rows (per-ms). */
    public static final float SPEED_HOVER  = 1f / 130f;
    /** Toggle-knob travel animation speed (per-ms). */
    public static final float SPEED_KNOB   = 1f / 150f;
    /** Arraylist row appear animation speed (per-ms). */
    public static final float SPEED_ROW    = 1f / 170f;
}
