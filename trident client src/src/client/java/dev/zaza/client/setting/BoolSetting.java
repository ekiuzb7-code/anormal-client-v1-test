package dev.zaza.client.setting;

public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String id, boolean defaultValue) {
        super(id, defaultValue);
    }

    public void toggle() {
        set(!value);
    }

    @Override
    public String format() {
        return value ? "on" : "off";
    }
}
