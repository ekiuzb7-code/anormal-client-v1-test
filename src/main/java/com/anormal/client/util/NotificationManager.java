package com.anormal.client.util;

import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.client.Notifications;

import java.util.ArrayList;
import java.util.List;

/**
 * Queue of on-screen toast notifications with a smooth slide/fade lifecycle.
 *
 * <p>Every notification goes through three phases: a short slide-in, a hold,
 * and a slide-out. The renderer asks for the current offset/alpha, so the
 * animation is driven by wall-clock time and stays smooth regardless of the
 * frame rate.</p>
 */
public final class NotificationManager {

    private NotificationManager() {}

    /** Slide-in / slide-out length in milliseconds. */
    public static final long IN_MS = 240L;
    public static final long OUT_MS = 300L;

    public static final class Entry {
        public final String title;
        public final String subtitle;
        public final int accent;
        public final long start;
        public final long hold;

        public Entry(String title, String subtitle, int accent, long hold) {
            this.title = title;
            this.subtitle = subtitle;
            this.accent = accent;
            this.start = System.currentTimeMillis();
            this.hold = hold;
        }

        public long elapsed() {
            return System.currentTimeMillis() - start;
        }

        /** 0..1 animation progress, plus whether it is currently fading out. */
        public float progress() {
            long e = elapsed();
            if (e < IN_MS) return e / (float) IN_MS;
            if (e < IN_MS + hold) return 1f;
            float p = (e - IN_MS - hold) / (float) OUT_MS;
            return 1f - Math.min(1f, p);
        }

        public boolean isRemoving() {
            return elapsed() >= IN_MS + hold;
        }

        public boolean isDead() {
            return elapsed() >= IN_MS + hold + OUT_MS;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static void clear() {
        ENTRIES.clear();
    }

    /** Drops finished notifications; call once per tick. */
    public static void tick() {
        ENTRIES.removeIf(Entry::isDead);
    }

    /** True when the Notifications module is on and accepts this kind of toast. */
    public static boolean accepting() {
        Notifications n = ModuleManager.getModule(Notifications.class);
        return n != null && n.isEnabled();
    }

    public static void push(String title, String subtitle, int accent) {
        if (!accepting()) return;
        Notifications n = ModuleManager.getModule(Notifications.class);
        long hold = n == null ? 1800L : n.duration.getValue().longValue();
        ENTRIES.add(new Entry(title, subtitle, accent, hold));

        int max = n == null ? 5 : n.maxVisible.getValue().intValue();
        while (ENTRIES.size() > Math.max(1, max)) ENTRIES.remove(0);
    }

    /** Fired from {@link Module#setEnabled}. */
    public static void onModuleToggled(Module module, boolean enabled) {
        if (!accepting()) return;
        Notifications n = ModuleManager.getModule(Notifications.class);
        if (n == null) return;
        if (!enabled && !n.showDisable.isEnabled()) return;

        int accent = enabled ? n.enabledColor.getValue() : n.disabledColor.getValue();
        push(module.getName(), enabled ? "Enabled" : "Disabled", accent);
    }

    /** Fired by modules that want a custom toast. */
    public static void info(String title, String subtitle) {
        if (!accepting()) return;
        Notifications n = ModuleManager.getModule(Notifications.class);
        push(title, subtitle, n == null ? 0xFF55FF55 : n.enabledColor.getValue());
    }
}
