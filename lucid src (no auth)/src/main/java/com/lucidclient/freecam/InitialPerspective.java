package com.lucidclient.freecam;

/** Where the freecam camera starts relative to the player (Freecam mod). */
public enum InitialPerspective {
    /** Camera at the player's eye position. */
    INSIDE,
    /** Camera just in front of the player's eyes. */
    FIRST_PERSON,
    /** Camera 4 blocks behind (F5 style). */
    THIRD_PERSON,
    /** Camera 4 blocks in FRONT, mirrored rotation. */
    THIRD_PERSON_MIRROR
}
