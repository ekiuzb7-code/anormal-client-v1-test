package dev.zaza.client.module;

import net.minecraft.client.Minecraft;

public abstract class Module {
    private final String name;
    private final String id;
    private final Category category;
    private boolean enabled;

    protected Module(String name, String id, Category category) {
        this.name = name;
        this.id = id;
        this.category = category;
    }

    public String name() {
        return name;
    }

    public String id() {
        return id;
    }

    public Category category() {
        return category;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (value) onEnable();
        else onDisable();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {}

    protected void onDisable() {}

    public void tick() {}

    protected static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
