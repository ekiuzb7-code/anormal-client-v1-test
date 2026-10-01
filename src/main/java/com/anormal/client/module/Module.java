package com.anormal.client.module;

import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final KeybindSetting keybind;
    private boolean enabled;
    private boolean expanded;
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = new KeybindSetting("Keybind", "Key to toggle " + name, defaultKey);
        this.settings.add(this.keybind);
    }

    public Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public int getKey() {
        return keybind.getValue();
    }

    public void setKey(int key) {
        keybind.setValue(key);
    }

    public KeybindSetting getKeybindSetting() {
        return keybind;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    public void onEnable() {}

    public void onDisable() {}

    public void onTick() {}

    public void onRender2D(DrawContext context, float tickDelta) {}
}
