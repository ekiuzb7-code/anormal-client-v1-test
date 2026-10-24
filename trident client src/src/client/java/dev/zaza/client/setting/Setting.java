package dev.zaza.client.setting;

import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    private final String id;
    private final T defaultValue;
    private BooleanSupplier visible = () -> true;
    protected T value;

    protected Setting(String id, T defaultValue) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String id() {
        return id;
    }

    public String name() {
        char[] chars = id.toCharArray();
        if (chars.length == 0) return id;
        boolean capitalize = true;
        StringBuilder builder = new StringBuilder();
        for (char c : chars) {
            if (c == '_' || c == ' ') {
                builder.append(' ');
                capitalize = true;
            } else {
                builder.append(capitalize ? Character.toUpperCase(c) : c);
                capitalize = false;
            }
        }
        return builder.toString();
    }

    public void visibleWhen(BooleanSupplier condition) {
        visible = condition;
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public void reset() {
        value = defaultValue;
    }

    /** One-line summary shown next to the setting, e.g. "80°" or "WindMouse". */
    public String format() {
        return String.valueOf(value);
    }
}
