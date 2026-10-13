package com.anormal.client.setting;

/**
 * Visual section header for settings menus (e.g. DETECTION, INDICATORS).
 * Does not store an editable value or consume clicks.
 */
public class HeaderSetting extends Setting<Void> {
    public HeaderSetting(String title) {
        super(title, "", null);
    }
}
