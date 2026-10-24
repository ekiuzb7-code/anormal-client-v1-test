package dev.zaza.client.render.font;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Fonts {
    public static final Font INTER_BOLD = Font.load("fonts/inter-bold", '?');
    public static final Font MATERIAL_ICONS = Font.load("icons/material", 0xe8b8);

    public static final float DISTANCE_RANGE = INTER_BOLD.distanceRange();

    static {
        if (MATERIAL_ICONS.distanceRange() != DISTANCE_RANGE) {
            throw new IllegalStateException("Every MSDF atlas must share the text pipeline's distance range");
        }
    }
}
