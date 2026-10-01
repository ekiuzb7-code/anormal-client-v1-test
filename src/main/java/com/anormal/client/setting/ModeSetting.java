package com.anormal.client.setting;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;
    private int index;

    public ModeSetting(String name, String description, String defaultMode, String... modes) {
        super(name, description, defaultMode);
        this.modes = Arrays.asList(modes);
        this.index = this.modes.indexOf(defaultMode);
        if (this.index == -1) {
            this.index = 0;
            super.setValue(this.modes.isEmpty() ? "" : this.modes.get(0));
        }
    }

    public List<String> getModes() {
        return modes;
    }

    public int getIndex() {
        return index;
    }

    public void cycle() {
        if (modes.isEmpty()) return;
        index = (index + 1) % modes.size();
        super.setValue(modes.get(index));
    }

    public void cycleBack() {
        if (modes.isEmpty()) return;
        index = (index - 1 + modes.size()) % modes.size();
        super.setValue(modes.get(index));
    }

    public boolean is(String mode) {
        return getValue().equalsIgnoreCase(mode);
    }
}
