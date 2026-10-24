package com.lucidclient.module;

import com.lucidclient.module.setting.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;

/**
 * Base class for all Lucid Client modules.
 *
 * Each module supports:
 *   Left-click  → Toggle
 *   Right-click → Open settings (expand inline within the category column)
 *   Middle-click → Change keybind
 *
 * Modules override {@link #onTick(MinecraftClient)} to implement behaviour.
 * The tick handler is invoked every client tick only while the module is enabled.
 *
 * For modules that need to react to input even when disabled (e.g. a toggle
 * key that re-enables the module), override {@link #onTickAlways(MinecraftClient)}
 * — it fires every tick regardless of enabled state.
 */
public class Module {
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled;
    private int keybind = -1; // -1 = unbound
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = false;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public int getKeybind() { return keybind; }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setKeybind(int keybind) {
        this.keybind = keybind;
    }

    public List<Setting<?>> getSettings() { return settings; }

    /** Register a setting — returns it for chaining. */
    protected <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    /** True when the module's settings card is expanded in the ClickGUI. */
    private transient boolean settingsExpanded;

    public boolean isSettingsExpanded() { return settingsExpanded; }
    public void setSettingsExpanded(boolean expanded) { this.settingsExpanded = expanded; }
    public void toggleSettingsExpanded() { this.settingsExpanded = !this.settingsExpanded; }

    // ── Lifecycle hooks ───────────────────────────────────────
    /** Called once when the module is enabled. Override in subclasses. */
    protected void onEnable() {}
    /** Called once when the module is disabled. Override in subclasses. */
    protected void onDisable() {}
    /** Called every client tick (≈50ms / 20Hz) while enabled.
     *  Use for target selection and logic that reads tick-synchronised state. */
    public void onTick(MinecraftClient client) {}

    /** Called every client tick regardless of enabled state.
     *  Override to handle e.g. a toggle-key press that re-enables the module. */
    public void onTickAlways(MinecraftClient client) {}

    /** Called every render frame (≈60fps / vsync) while enabled.
     *  Use for smooth, frame-rate-driven updates such as rotation smoothing.
     *  @param tickDelta partial tick progress 0..1 for interpolation. */
    public void onRender(MinecraftClient client, float tickDelta) {}
}
