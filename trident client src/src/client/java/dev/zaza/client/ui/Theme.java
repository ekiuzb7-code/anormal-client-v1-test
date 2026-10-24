package dev.zaza.client.ui;

import dev.zaza.client.util.Colors;
import java.util.List;

public final class Theme {
    public enum Slot {
        ACCENT, ON_ACCENT, TEXT, MUTED, DIM,
        MAIN, HEADER, PLATE, ROW, FIELD,
        CONTROL, CONTROL_ACTIVE, TRACK, BORDER, HIGHLIGHT,
        CARD, CARD_BORDER
    }

    public record Definition(String id, String label, int accent, int[] slots) {
        public int slot(Slot slot) {
            return slots[slot.ordinal()];
        }
    }

    private static final int RAW_TEXT = 0xFFECECF1;
    private static final int RAW_MUTED = 0xFF9B9BA3;
    private static final int RAW_DIM = 0xFF7A7A83;
    private static final int RAW_HEADER = 0xFF0B0B0D;
    private static final int RAW_FIELD = 0xFF1C1C21;
    private static final int RAW_CONTROL = 0xFF232329;
    private static final int RAW_CONTROL_ACTIVE = 0xFF2E2E36;
    private static final int RAW_TRACK = 0xFF3A3A42;
    private static final int RAW_HIGHLIGHT = 0x2EFFFFFF;

    private static final List<Definition> DEFINITIONS = List.of(
            definition("red_black", "red / black", 0xE23429,
                    0xFF0E0B0B, 0xFF171112, 0xFF1A1211, 0xFF2A1B19, 0xE03A1412),
            definition("ice_midnight", "ice / midnight", 0x22D3EE,
                    0xFF0A0D12, 0xFF0F141B, 0xFF121924, 0xFF1D2A3A, 0xE0122A44),
            definition("violet_graphite", "violet / graphite", 0xA855F7,
                    0xFF0E0C11, 0xFF14121A, 0xFF171420, 0xFF251F33, 0xE0251740),
            definition("lime_terminal", "lime / terminal", 0xA3E635,
                    0xFF0B0E09, 0xFF101409, 0xFF131A0C, 0xFF202B14, 0xE01E2E0F),
            definition("gold_sepia", "gold / sepia", 0xF5C518,
                    0xFF100E08, 0xFF161309, 0xFF1A160B, 0xFF2B2410, 0xE03A2A08),
            definition("pink_obsidian", "pink / obsidian", 0xF055C0,
                    0xFF100A0F, 0xFF171016, 0xFF1C121A, 0xFF2E1B2A, 0xE03A1038));

    private static final Transition blend = new Transition(1f, 360, Transition.Easing.EASE_OUT_CUBIC);
    private static final int[] from = new int[Slot.values().length];
    private static final int[] to = new int[Slot.values().length];
    private static int selected;

    public static int ACCENT;
    public static int ON_ACCENT;
    public static int TEXT;
    public static int MUTED;
    public static int DIM;
    public static int MAIN;
    public static int HEADER;
    public static int PLATE;
    public static int ROW;
    public static int FIELD;
    public static int CONTROL;
    public static int CONTROL_ACTIVE;
    public static int TRACK;
    public static int BORDER;
    public static int HIGHLIGHT;
    public static int CARD;
    public static int CARD_BORDER;

    static {
        write(DEFINITIONS.get(0));
        System.arraycopy(to, 0, from, 0, to.length);
        update();
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static int selected() {
        return selected;
    }

    public static Definition current() {
        return DEFINITIONS.get(selected);
    }

    public static void select(int index) {
        if (index == selected) return;
        // Freeze the currently displayed (possibly mid-blend) colors as the source.
        for (Slot slot : Slot.values()) {
            int i = slot.ordinal();
            from[i] = Colors.lerp(from[i], to[i], blend.value());
        }
        selected = Math.clamp(index, 0, DEFINITIONS.size() - 1);
        write(DEFINITIONS.get(selected));
        blend.snap(0f);
        blend.set(1f);
    }

    /** Blends every slot from -> to at the current transition point. Read-only: `to` stays the target. */
    public static void update() {
        float t = blend.value();
        ACCENT = Colors.lerp(from[Slot.ACCENT.ordinal()], to[Slot.ACCENT.ordinal()], t);
        ON_ACCENT = Colors.lerp(from[Slot.ON_ACCENT.ordinal()], to[Slot.ON_ACCENT.ordinal()], t);
        TEXT = Colors.lerp(from[Slot.TEXT.ordinal()], to[Slot.TEXT.ordinal()], t);
        MUTED = Colors.lerp(from[Slot.MUTED.ordinal()], to[Slot.MUTED.ordinal()], t);
        DIM = Colors.lerp(from[Slot.DIM.ordinal()], to[Slot.DIM.ordinal()], t);
        MAIN = Colors.lerp(from[Slot.MAIN.ordinal()], to[Slot.MAIN.ordinal()], t);
        HEADER = Colors.lerp(from[Slot.HEADER.ordinal()], to[Slot.HEADER.ordinal()], t);
        PLATE = Colors.lerp(from[Slot.PLATE.ordinal()], to[Slot.PLATE.ordinal()], t);
        ROW = Colors.lerp(from[Slot.ROW.ordinal()], to[Slot.ROW.ordinal()], t);
        FIELD = Colors.lerp(from[Slot.FIELD.ordinal()], to[Slot.FIELD.ordinal()], t);
        CONTROL = Colors.lerp(from[Slot.CONTROL.ordinal()], to[Slot.CONTROL.ordinal()], t);
        CONTROL_ACTIVE = Colors.lerp(from[Slot.CONTROL_ACTIVE.ordinal()], to[Slot.CONTROL_ACTIVE.ordinal()], t);
        TRACK = Colors.lerp(from[Slot.TRACK.ordinal()], to[Slot.TRACK.ordinal()], t);
        BORDER = Colors.lerp(from[Slot.BORDER.ordinal()], to[Slot.BORDER.ordinal()], t);
        HIGHLIGHT = Colors.lerp(from[Slot.HIGHLIGHT.ordinal()], to[Slot.HIGHLIGHT.ordinal()], t);
        CARD = Colors.lerp(from[Slot.CARD.ordinal()], to[Slot.CARD.ordinal()], t);
        CARD_BORDER = Colors.lerp(from[Slot.CARD_BORDER.ordinal()], to[Slot.CARD_BORDER.ordinal()], t);
    }

    private static void write(Definition definition) {
        for (Slot slot : Slot.values()) to[slot.ordinal()] = definition.slot(slot);
    }

    private static Definition definition(String id, String label, int accent, int main, int plate, int row,
                                         int border, int card) {
        int[] slots = new int[Slot.values().length];
        slots[Slot.ACCENT.ordinal()] = Colors.opaque(accent);
        slots[Slot.ON_ACCENT.ordinal()] = 0xFFFFFFFF;
        slots[Slot.TEXT.ordinal()] = RAW_TEXT;
        slots[Slot.MUTED.ordinal()] = RAW_MUTED;
        slots[Slot.DIM.ordinal()] = RAW_DIM;
        slots[Slot.MAIN.ordinal()] = main;
        slots[Slot.HEADER.ordinal()] = RAW_HEADER;
        slots[Slot.PLATE.ordinal()] = plate;
        slots[Slot.ROW.ordinal()] = row;
        slots[Slot.FIELD.ordinal()] = RAW_FIELD;
        slots[Slot.CONTROL.ordinal()] = RAW_CONTROL;
        slots[Slot.CONTROL_ACTIVE.ordinal()] = RAW_CONTROL_ACTIVE;
        slots[Slot.TRACK.ordinal()] = RAW_TRACK;
        slots[Slot.BORDER.ordinal()] = border;
        slots[Slot.HIGHLIGHT.ordinal()] = RAW_HIGHLIGHT;
        slots[Slot.CARD.ordinal()] = card;
        slots[Slot.CARD_BORDER.ordinal()] = (0x66000000 | (accent & 0xFFFFFF));
        return new Definition(id, label, Colors.opaque(accent), slots);
    }

    private Theme() {}
}
