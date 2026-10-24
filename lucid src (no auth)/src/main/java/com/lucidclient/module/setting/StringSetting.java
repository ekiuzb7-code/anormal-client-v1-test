package com.lucidclient.module.setting;

/**
 * A free-text string setting rendered as a clickable text field in the
 * ClickGUI. When clicked, the field becomes "focused" and captures
 * keyboard input (character typing + backspace) until the user presses
 * Enter or Escape, or clicks another control.
 *
 * <p>This mirrors the {@link KeybindSetting} "waiting" capture pattern:
 * {@link LucidScreen} scans all modules for a focused {@code StringSetting}
 * (via {@link #isFocused()}) and routes {@code charTyped}/{@code keyPressed}
 * events to it directly, bypassing the component dispatch chain.
 *
 * <p>The focused flag is {@code transient} so it never serialises — a
 * reloaded config never leaves a field stuck in capture mode.
 */
public class StringSetting extends Setting<String> {
    private final int maxLength;

    /** Transient — only meaningful while the ClickGUI is open. */
    private transient boolean focused = false;

    public StringSetting(String name, String description, String defaultValue) {
        this(name, description, defaultValue, 64);
    }

    public StringSetting(String name, String description, String defaultValue, int maxLength) {
        super(name, description, defaultValue == null ? "" : defaultValue);
        this.maxLength = maxLength;
        if (defaultValue != null && defaultValue.length() > maxLength) {
            this.value = defaultValue.substring(0, maxLength);
        }
    }

    public int getMaxLength() { return maxLength; }

    public boolean isFocused() { return focused; }

    public void setFocused(boolean focused) { this.focused = focused; }

    /** Append a typed character, respecting the max length. */
    public void appendChar(char chr) {
        if (value.length() >= maxLength) return;
        // Ignore control characters — only printable/space.
        if (chr < 32 || chr == 127) return;
        value = value + chr;
    }

    /** Delete the last character. */
    public void backspace() {
        if (value.isEmpty()) return;
        value = value.substring(0, value.length() - 1);
    }

    /** Replace the whole value, clamped to max length. */
    public void setValue(String v) {
        if (v == null) v = "";
        if (v.length() > maxLength) v = v.substring(0, maxLength);
        value = v;
    }

    /** True when the field has no value (used to show a placeholder). */
    public boolean isEmpty() { return value == null || value.isEmpty(); }

    @Override public int getControlHeight() { return 24; }
    @Override public String getType() { return "string"; }
}