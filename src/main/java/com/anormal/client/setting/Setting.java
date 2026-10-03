package com.anormal.client.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String name;
    private final String description;
    private T value;
    private BooleanSupplier visible = () -> true;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    // Style-gated visibility: hidden settings are skipped by the GUI entirely
    public void visibleIf(BooleanSupplier condition) {
        this.visible = condition;
    }

    public boolean isVisible() {
        try {
            return visible.getAsBoolean();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
