package com.lucidclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.ui.theme.LucidTheme;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClickGuiConfig — persists the Krypton-style category-window positions
 * and collapse state to {@code .minecraft/config/lucidclient/clickgui.json}.
 *
 * The ClickGUI is now free-layout: each category window can be dragged
 * anywhere, and collapsed. Saving/restoring that layout across sessions
 * matches the Krypton client's UX (hit Right Shift → your saved setup).
 *
 * Schema (one entry per category enum name):
 * <pre>
 * {
 *   "Combat":    { "x": 40, "y": 60,  "collapsed": false },
 *   "Misc":      { "x": 220, "y": 60, "collapsed": true  },
 *   ...
 * }
 * </pre>
 *
 * Missing categories fall back to the default stacked layout
 * ({@link #defaultLayout()}), so adding a new category never breaks an
 * existing config.
 */
public final class ClickGuiConfig {
    private static final String CONFIG_FILE = "clickgui.json";
    private static final Path CONFIG_DIR =
        FabricLoader.getInstance().getConfigDir().resolve("lucidclient");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** Per-category saved window state. */
    public static final class WindowState {
        public int x;
        public int y;
        public boolean collapsed;

        public WindowState(int x, int y, boolean collapsed) {
            this.x = x;
            this.y = y;
            this.collapsed = collapsed;
        }
    }

    /** Load the saved window states. Returns null if no config exists. */
    public static java.util.Map<String, WindowState> load() {
        Path path = CONFIG_DIR.resolve(CONFIG_FILE);
        if (!Files.exists(path)) return null;
        try {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).isJsonObject()
                ? JsonParser.parseString(json).getAsJsonObject() : null;
            if (root == null) return null;
            java.util.Map<String, WindowState> map = new java.util.HashMap<>();
            for (String key : root.keySet()) {
                JsonObject e = root.getAsJsonObject(key);
                map.put(key, new WindowState(
                    e.has("x") ? e.get("x").getAsInt() : 0,
                    e.has("y") ? e.get("y").getAsInt() : 0,
                    e.has("collapsed") && e.get("collapsed").getAsBoolean()
                ));
            }
            return map;
        } catch (Throwable t) {
            LucidClient.LOGGER.warn("Failed to load clickgui config: {}", t.getMessage());
            return null;
        }
    }

    /** Save the given window states. */
    public static void save(java.util.Map<String, WindowState> states) {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            for (java.util.Map.Entry<String, WindowState> e : states.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("x", e.getValue().x);
                o.addProperty("y", e.getValue().y);
                o.addProperty("collapsed", e.getValue().collapsed);
                root.add(e.getKey(), o);
            }
            Path path = CONFIG_DIR.resolve(CONFIG_FILE);
            Files.writeString(path, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            LucidClient.LOGGER.warn("Failed to save clickgui config: {}", ioe.getMessage());
        }
    }

    /**
     * Default layout: stack the compact windows down the left edge with
     * a small gap. Column wraps below {@code maxRows} windows.
     */
    public static WindowState defaultLayout(Category cat, int index, int screenW, int screenH) {
        int w = 168;
        int h = 226; // title + body
        int gap = LucidTheme.CATEGORY_STACK_GAP;
        int margin = 12;
        int perRow = Math.max(1, (screenH - margin * 2) / (h + gap));
        int col = index / perRow;
        int row = index % perRow;
        int x = margin + col * (w + gap);
        int y = margin + row * (h + gap);
        return new WindowState(x, y, false);
    }
}
