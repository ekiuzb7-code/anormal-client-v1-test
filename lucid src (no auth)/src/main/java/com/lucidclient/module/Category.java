package com.lucidclient.module;

/**
 * Lucid Client module categories.
 * Each category is rendered as a floating glass panel in the ClickGUI.
 */
public enum Category {
    COMBAT("Combat", "Sword"),
    RENDER("Render", "Eye"),
    MOVEMENT("Movement", "Feather"),
    PLAYER("Player", "Heart"),
    MISC("Misc", "Settings"),
    DONUT("Donut", "Donut"),
    BASEFINDING("Basefinding", "Compass");

    private final String displayName;
    private final String iconName;

    Category(String displayName, String iconName) {
        this.displayName = displayName;
        this.iconName = iconName;
    }

    public String getDisplayName() { return displayName; }
    public String getIconName() { return iconName; }
}
