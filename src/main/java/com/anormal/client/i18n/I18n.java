package com.anormal.client.i18n;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny translation layer for the whole client.
 *
 * <p>Every translatable string is keyed by its <b>English</b> source text, so
 * the code keeps using plain English literals and the dictionary is a simple
 * lookup table. Missing entries fall back to English, which means a partial
 * translation can never break the UI.</p>
 *
 * <p>Usage: {@code I18n.tr("Combat")} returns "Jang" when Uzbek is selected and
 * "Combat" otherwise.</p>
 */
public final class I18n {
    private I18n() {}

    public enum Lang {
        EN("English", "English"),
        UZ("Uzbek", "O'zbekcha"),
        RU("Russian", "Русский");

        private final String id;
        private final String label;

        Lang(String id, String label) {
            this.id = id;
            this.label = label;
        }

        /** Value used by the ClientSettings "Language" mode setting. */
        public String id() {
            return id;
        }

        /** Native name shown in the dropdown. */
        public String label() {
            return label;
        }
    }

    private static final Map<String, String> UZ = new HashMap<>();
    private static final Map<String, String> RU = new HashMap<>();

    private static Lang current = Lang.EN;

    static {
        Uz.load(UZ);
        Ru.load(RU);
    }

    public static Lang getLang() {
        return current;
    }

    public static void setLang(Lang lang) {
        current = lang == null ? Lang.EN : lang;
    }

    /** Sets the language from the ClientSettings mode value. */
    public static void setLangById(String id) {
        if (id == null) {
            current = Lang.EN;
            return;
        }
        for (Lang l : Lang.values()) {
            if (l.id().equalsIgnoreCase(id) || l.name().equalsIgnoreCase(id)) {
                current = l;
                return;
            }
        }
        current = Lang.EN;
    }

    /**
     * Translates a source string. Returns the input unchanged when the language
     * is English or when no translation exists.
     */
    public static String tr(String key) {
        if (key == null || key.isEmpty() || current == Lang.EN) return key;
        Map<String, String> dict = current == Lang.UZ ? UZ : RU;
        String v = dict.get(key);
        return (v == null || v.isEmpty()) ? key : v;
    }

    /** Number of loaded entries, for diagnostics. */
    public static int size(Lang lang) {
        return lang == Lang.UZ ? UZ.size() : (lang == Lang.RU ? RU.size() : 0);
    }
}
