package com.anormal.client.module;

import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
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
    /**
     * How the keybind behaves.
     *
     * <p>{@code Toggle} - press once to switch on, press again to switch off
     * (the classic behaviour).</p>
     * <p>{@code Hold} - the module is only active while the key is held down,
     * and switches off the moment it is released. This is what you want for
     * Freecam, Zoom, FreeLook and similar "while pressed" modules.</p>
     */
    private final ModeSetting keybindMode;
    private boolean enabled;
    private boolean expanded;
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category, int defaultKey) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keybind = new KeybindSetting("Keybind", "Key to toggle " + name, defaultKey);
        this.keybindMode = new ModeSetting("Keybind Mode",
                "Toggle = press to switch on/off. Hold = active only while the key is held down.",
                "Toggle", "Toggle", "Hold");
        this.settings.add(this.keybind);
        this.settings.add(this.keybindMode);
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
            try {
                com.anormal.client.util.NotificationManager.onModuleToggled(this, enabled);
            } catch (Throwable ignored) {
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

    public ModeSetting getKeybindModeSetting() {
        return keybindMode;
    }

    /** True when the module should only be active while its key is held down. */
    public boolean isHoldMode() {
        return keybindMode.is("Hold");
    }

    /** True when the keybind switches the module on and off. */
    public boolean isToggleMode() {
        return !keybindMode.is("Hold");
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
