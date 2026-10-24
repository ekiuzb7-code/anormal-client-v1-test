package com.lucidclient.module.setting;

import java.util.Arrays;
import java.util.List;

/**
 * An enum/mode setting rendered as a dropdown selector.
 * The value is stored as the enum constant name (String) so it survives
 * config reloads without holding a class reference.
 */
public class EnumSetting extends Setting<String> {
    private final List<String> options;

    public EnumSetting(String name, String description, List<String> options, String defaultValue) {
        super(name, description, options.contains(defaultValue) ? defaultValue : options.get(0));
        this.options = options;
    }

    public EnumSetting(String name, String description, String[] options, String defaultValue) {
        this(name, description, Arrays.asList(options), defaultValue);
    }

    public List<String> getOptions() { return options; }
    public int getSelectedIndex() { return options.indexOf(value); }

    public void setSelectedIndex(int i) {
        if (i >= 0 && i < options.size()) this.value = options.get(i);
    }

    @Override public int getControlHeight() { return 28; }
    @Override public String getType() { return "enum"; }
}
