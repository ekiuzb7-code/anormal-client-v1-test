package com.anormal.client.module;

import com.anormal.client.i18n.I18n;

public enum Category {
    COMBAT("Combat", "⚔"),
    MOVEMENT("Movement", "⚡"),
    RENDER("Render", "👁"),
    PLAYER("Player", "👤"),
    WORLD("World", "🌍"),
    INVENTORY("Inventory", "🎒"),
    LEGIT("Legit HUD", "🛡"),
    CLIENT("Client", "⚙"),
    /**
     * Not a module list - selecting this tab shows the author credits instead.
     * The GUI checks for this constant explicitly.
     */
    DEVELOPER("Developer", "★");

    private final String name;
    private final String icon;

    Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    /** English name, used as the translation key. */
    public String getRawName() {
        return name;
    }

    /** Translated display name. */
    public String getName() {
        return I18n.tr(name);
    }

    public String getIcon() {
        return icon;
    }

    /** True when this tab shows the credits panel instead of modules. */
    public boolean isInfoOnly() {
        return this == DEVELOPER;
    }
}
