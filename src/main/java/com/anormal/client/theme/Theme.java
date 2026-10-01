package com.anormal.client.theme;

public enum Theme {
    VAPE_V4("Vape V4", "Dark sleek charcoal theme with vibrant orange/neon accents"),
    GLASSMORPHISM("Glassmorphism", "Modern frosted glass with glowing linear borders and blur effect");

    private final String displayName;
    private final String description;

    Theme(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
