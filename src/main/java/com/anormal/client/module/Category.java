package com.anormal.client.module;

public enum Category {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "⚡"),
    RENDER("Render", "👁"),
    PLAYER("Player", "👤"),
    WORLD("World", "🌍"),
    INVENTORY("Inventory", "🎒"),
    LEGIT("Legit HUD", "🛡"),
    CLIENT("Client", "⚙"),
    UZNY11("Uzny11", "★");

    private final String name;
    private final String icon;

    Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }
}
