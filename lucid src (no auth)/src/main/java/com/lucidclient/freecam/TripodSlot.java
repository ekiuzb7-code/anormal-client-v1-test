package com.lucidclient.freecam;

/**
 * Tripod camera slots (Freecam mod): freecam key + a hotbar number (1-9)
 * opens a saved camera position per slot; pressing the same combo again
 * returns to that camera.
 */
public enum TripodSlot {
    NONE, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE;

    public static TripodSlot ofHotbarIndex(int index) {
        if (index < 0 || index > 8) return NONE;
        return values()[index + 1];
    }

    @Override
    public String toString() {
        return this == NONE ? "None" : "#" + ordinal();
    }
}
