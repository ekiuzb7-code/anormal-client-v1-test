package dev.zaza.client.ui;

import dev.zaza.client.util.Colors;

/**
 * Derives the interactive palette from the current {@link Theme} selection:
 * hover tints, active surfaces and card colors, crossfaded in lockstep with
 * the main theme blend. Colors derive from the target definition, so the
 * derived set never lags a blend behind the base palette.
 */
public final class ThemeAdjust {
    public static int hover;
    public static int active;
    public static int controlHover;
    public static int tabPill;
    public static int appearanceCard;

    private static final Transition blend = new Transition(1f, 360, Transition.Easing.EASE_OUT_CUBIC);
    private static final int COUNT = 5;
    private static final int[] from = new int[COUNT];
    private static final int[] to = new int[COUNT];
    private static boolean ready;

    private ThemeAdjust() {}

    public static void select() {
        // Freeze the currently displayed values as the blend source.
        if (ready) {
            float t = blend.value();
            for (int i = 0; i < COUNT; i++) from[i] = Colors.lerp(from[i], to[i], t);
        } else {
            ready = true;
        }
        int[] target = compute(Theme.current());
        System.arraycopy(target, 0, to, 0, COUNT);
        blend.snap(0f);
        blend.set(1f);
        update();
    }

    public static void update() {
        if (!ready) {
            select();
            blend.snap(1f);
        }
        float t = blend.value();
        hover = Colors.lerp(from[0], to[0], t);
        active = Colors.lerp(from[1], to[1], t);
        controlHover = Colors.lerp(from[2], to[2], t);
        tabPill = Colors.lerp(from[3], to[3], t);
        appearanceCard = Colors.lerp(from[4], to[4], t);
    }

    private static int[] compute(Theme.Definition definition) {
        int accent = definition.accent();
        int control = definition.slot(Theme.Slot.CONTROL);
        return new int[] {
                Colors.withAlpha(accent, 0.16f),
                Colors.darken(accent, 0.55f),
                Colors.lighten(control, 0.06f),
                Colors.withAlpha(accent, 0.20f),
                Colors.darken(accent, 0.74f)
        };
    }
}
