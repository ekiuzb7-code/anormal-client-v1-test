package com.anormal.client.setting;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public void toggle() {
        setValue(!getValue());
    }

    /** Lucid-style accessor used by the ported modules. */
    public boolean get() {
        return isEnabled();
    }

    public boolean isEnabled() {
        return getValue();
    }
}
