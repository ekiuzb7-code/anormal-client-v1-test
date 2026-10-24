package com.lucidclient.module.setting;

/**
 * A true/false setting rendered as a toggle switch.
 */
public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    @Override public int getControlHeight() { return 24; }

    @Override public String getType() { return "boolean"; }

    public boolean isEnabled() { return value; }
    public void toggle() { value = !value; }
}
