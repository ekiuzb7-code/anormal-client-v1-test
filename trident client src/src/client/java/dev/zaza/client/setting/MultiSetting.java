package dev.zaza.client.setting;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.IntPredicate;

public final class MultiSetting extends Setting<Set<Integer>> {
    private final String[] options;
    private IntPredicate optionVisible = option -> true;

    public MultiSetting(String id, String[] options, int... defaults) {
        super(id, new LinkedHashSet<>());
        this.options = options;
        for (int index : defaults) value.add(index);
    }

    public void optionVisibleWhen(IntPredicate condition) {
        optionVisible = condition;
    }

    public boolean isOptionVisible(int option) {
        return optionVisible.test(option);
    }

    public boolean has(int option) {
        return value.contains(option) && isOptionVisible(option);
    }

    public void toggle(int option) {
        if (!isOptionVisible(option)) return;
        if (value.contains(option)) {
            value.remove(option);
        } else {
            value.add(option);
        }
    }

    public String[] options() {
        return options;
    }

    /** Drop every selected option, e.g. before loading a stored config. */
    public void clear() {
        value.clear();
    }

    @Override
    public String format() {
        StringBuilder builder = new StringBuilder();
        for (int option = 0; option < options.length; option++) {
            if (!has(option)) continue;
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(options[option]);
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }
}
