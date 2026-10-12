package com.anormal.client.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A multi-select list, e.g. LiquidBounce's {@code multiEnumChoice("FlushOn", FlushOn.entries)}.
 * Several options can be enabled at the same time.
 */
public class MultiSelectSetting extends Setting<List<String>> {
    private final List<String> options;

    public MultiSelectSetting(String name, String description, String... options) {
        this(name, description, new String[0], options);
    }

    public MultiSelectSetting(String name, String description, String[] defaults, String... options) {
        super(name, description, new ArrayList<>());
        this.options = Arrays.asList(options);
        List<String> init = new ArrayList<>();
        for (String d : defaults) {
            if (this.options.contains(d)) init.add(d);
        }
        setValue(init);
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean isSelected(String option) {
        return getValue().contains(option);
    }

    public void toggle(String option) {
        if (!options.contains(option)) return;
        List<String> cur = new ArrayList<>(getValue());
        if (!cur.remove(option)) cur.add(option);
        super.setValue(cur);
    }

    public void select(String option) {
        if (!isSelected(option)) toggle(option);
    }

    public void deselect(String option) {
        if (isSelected(option)) toggle(option);
    }

    public boolean anySelected() {
        return !getValue().isEmpty();
    }

    public String display() {
        return getValue().isEmpty() ? "None" : String.join(", ", getValue());
    }
}
