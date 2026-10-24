package dev.zaza.client.module;

public enum Category {
    COMBAT("combat"),
    VISUALS("visuals"),
    LOCAL("local"),
    WORLD("world");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
