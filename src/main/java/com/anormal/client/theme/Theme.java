package com.anormal.client.theme;

public enum Theme {
    VAPE_V4("Anormal", "Dark sleek charcoal theme with vibrant orange/neon accents"),
    GLASSMORPHISM("Glassmorphism", "Modern frosted glass with glowing linear borders and blur effect"),
    BRUTALISM("Brutalism", "Neo-brutalist high contrast with bold borders, hard shadows and chunky buttons");

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
