package dev.zaza.client.setting;

public final class EnumSetting extends Setting<Integer> {
    private final String[] options;

    public EnumSetting(String id, int defaultIndex, String... options) {
        super(id, defaultIndex);
        this.options = options;
    }

    public String[] options() {
        return options;
    }

    public String selected() {
        return options[value];
    }

    public void cycle() {
        set((value + 1) % options.length);
    }

    @Override
    public void set(Integer value) {
        super.set(Math.clamp(value, 0, options.length - 1));
    }

    @Override
    public String format() {
        return selected();
    }
}
