package com.lucidclient.module.setting;

/**
 * Base class for all module settings.
 *
 * Settings are rendered inline within a module's expanded card in the
 * ClickGUI. Each concrete subclass renders its own control.
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {
    protected final String name;
    protected final String description;
    protected T value;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }

    /**
     * @return the height in pixels this setting's control needs when rendered.
     *         Used by the ClickGUI to lay out expanded module cards.
     */
    public abstract int getControlHeight();

    /**
     * @return one of "boolean", "float", "int", "enum" — used to pick the renderer.
     */
    public abstract String getType();
}
