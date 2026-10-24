package com.anormal.client.profiles;

import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.DoubleRangeSetting;
import com.anormal.client.setting.HeaderSetting;
import com.anormal.client.setting.IntRangeSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.MultiSelectSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.Setting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Named profiles stored as one readable JSON file per profile.
 *
 * <p>A profile captures the complete client state: which modules are switched on, every setting
 * of every module (including keybinds and the selected GUI theme) and the HUD modules. Because
 * one profile is exactly one file, sharing works by copying that file into
 * {@code config/anormal/profiles} - it shows up in the list the next time the screen is opened.</p>
 */
public final class ProfileManager {

    /** Bumped when the file layout changes; older files are still read. */
    private static final int FORMAT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProfileManager() {
    }

    // ------------------------------------------------------------------
    //  Locations
    // ------------------------------------------------------------------

    public static File directory() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "config/anormal/profiles");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Turns a user typed name into a safe file name; empty when nothing usable is left. */
    public static String sanitize(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String out = sb.toString().trim();
        while (out.startsWith(".")) out = out.substring(1);
        if (out.length() > 48) out = out.substring(0, 48).trim();
        return out;
    }

    public static File fileFor(String name) {
        String safe = sanitize(name);
        if (safe.isEmpty()) return null;
        return new File(directory(), safe + ".json");
    }

    public static boolean exists(String name) {
        File f = fileFor(name);
        return f != null && f.isFile();
    }

    /** Every profile in the folder, alphabetically. Files dropped in by hand are picked up too. */
    public static List<String> list() {
        List<String> out = new ArrayList<>();
        File[] files = directory().listFiles();
        if (files == null) return out;
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            if (!n.toLowerCase(Locale.ROOT).endsWith(".json")) continue;
            out.add(n.substring(0, n.length() - 5));
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ------------------------------------------------------------------
    //  Save
    // ------------------------------------------------------------------

    /**
     * Writes the current client state into {@code name}.
     *
     * @return {@code null} on success, otherwise a human readable reason
     */
    public static String save(String name) {
        File file = fileFor(name);
        if (file == null) return "Enter a profile name";

        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("name", sanitize(name));
        long now = System.currentTimeMillis();
        root.addProperty("savedAt", now);

        JsonObject modules = new JsonObject();
        for (Module module : ModuleManager.getModules()) {
            // The Profiles module itself is never part of a snapshot.
            if (module instanceof com.anormal.client.module.impl.client.Profiles) continue;

            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());

            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                if (setting instanceof HeaderSetting) continue;
                JsonElement encoded = encode(setting);
                if (encoded != null) settings.add(setting.getName(), encoded);
            }
            entry.add("settings", settings);
            modules.add(module.getName(), entry);
        }
        root.add("modules", modules);

        try {
            Files.write(file.toPath(), GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            return null;
        } catch (Throwable t) {
            return "Could not write file: " + t.getMessage();
        }
    }

    private static JsonElement encode(Setting<?> setting) {
        try {
            if (setting instanceof BooleanSetting b) {
                return GSON.toJsonTree(b.isEnabled());
            }
            if (setting instanceof NumberSetting n) {
                return GSON.toJsonTree(n.getValue());
            }
            if (setting instanceof ModeSetting m) {
                return GSON.toJsonTree(m.getValue());
            }
            if (setting instanceof KeybindSetting k) {
                return GSON.toJsonTree(k.getValue());
            }
            if (setting instanceof ColorSetting c) {
                JsonObject o = new JsonObject();
                o.addProperty("color", c.getValue());
                o.addProperty("rainbow", c.isRainbow());
                return o;
            }
            if (setting instanceof IntRangeSetting r) {
                JsonArray a = new JsonArray();
                a.add(r.getMin());
                a.add(r.getMax());
                return a;
            }
            if (setting instanceof DoubleRangeSetting r) {
                JsonArray a = new JsonArray();
                a.add(r.getMin());
                a.add(r.getMax());
                return a;
            }
            if (setting instanceof MultiSelectSetting ms) {
                JsonArray a = new JsonArray();
                for (String opt : ms.getOptions()) {
                    if (ms.isSelected(opt)) a.add(opt);
                }
                return a;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Load
    // ------------------------------------------------------------------

    /**
     * Restores a profile: settings first, then the on/off state, so a module that gets switched on
     * already has its saved values.
     *
     * @return {@code null} on success, otherwise a human readable reason
     */
    public static String load(String name) {
        File file = fileFor(name);
        if (file == null || !file.isFile()) return "Profile not found";

        JsonObject root;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) return "Not a profile file";
            root = parsed.getAsJsonObject();
        } catch (Throwable t) {
            return "Could not read file: " + t.getMessage();
        }

        JsonElement modulesElement = root.get("modules");
        if (modulesElement == null || !modulesElement.isJsonObject()) return "Profile has no modules";
        JsonObject modules = modulesElement.getAsJsonObject();

        int applied = 0;

        // Pass 1 - settings.
        for (String moduleName : modules.keySet()) {
            Module module = find(moduleName);
            if (module == null) continue;
            if (module instanceof com.anormal.client.module.impl.client.Profiles) continue;

            JsonObject entry = asObject(modules.get(moduleName));
            if (entry == null) continue;
            JsonElement settingsElement = entry.get("settings");
            if (settingsElement == null || !settingsElement.isJsonObject()) continue;

            JsonObject settings = settingsElement.getAsJsonObject();
            for (Setting<?> setting : module.getSettings()) {
                JsonElement value = settings.get(setting.getName());
                if (value == null) continue;
                try {
                    decode(setting, value);
                    applied++;
                } catch (Throwable ignored) {
                }
            }
        }

        // Pass 2 - enabled state.
        for (String moduleName : modules.keySet()) {
            Module module = find(moduleName);
            if (module == null) continue;
            if (module instanceof com.anormal.client.module.impl.client.Profiles) continue;

            JsonObject entry = asObject(modules.get(moduleName));
            if (entry == null) continue;
            JsonElement enabled = entry.get("enabled");
            if (enabled == null) continue;
            try {
                module.setEnabled(enabled.getAsBoolean());
            } catch (Throwable ignored) {
            }
        }

        // Theme is driven from the ClientSettings module every tick, but applying it here makes the
        // change visible immediately instead of one tick later.
        try {
            com.anormal.client.module.impl.client.ClientSettings cs =
                    ModuleManager.getModule(com.anormal.client.module.impl.client.ClientSettings.class);
            if (cs != null) cs.onTick();
        } catch (Throwable ignored) {
        }

        return applied == 0 ? "Profile applied (no settings matched)" : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void decode(Setting<?> setting, JsonElement value) throws Exception {
        if (setting instanceof BooleanSetting b) {
            ((Setting<Boolean>) (Setting) b).setValue(value.getAsBoolean());
            return;
        }
        if (setting instanceof NumberSetting n) {
            ((Setting<Double>) (Setting) n).setValue(value.getAsDouble());
            return;
        }
        if (setting instanceof ModeSetting m) {
            String v = value.getAsString();
            if (m.getModes().contains(v)) m.setMode(v);
            return;
        }
        if (setting instanceof KeybindSetting k) {
            ((Setting<Integer>) (Setting) k).setValue(value.getAsInt());
            return;
        }
        if (setting instanceof ColorSetting c) {
            JsonObject o = asObject(value);
            if (o == null) return;
            if (o.has("color")) c.setValue(o.get("color").getAsInt());
            if (o.has("rainbow")) c.setRainbow(o.get("rainbow").getAsBoolean());
            return;
        }
        if (setting instanceof IntRangeSetting r) {
            JsonArray a = value.getAsJsonArray();
            if (a.size() < 2) return;
            r.setMin(a.get(0).getAsInt());
            r.setMax(a.get(1).getAsInt());
            return;
        }
        if (setting instanceof DoubleRangeSetting r) {
            JsonArray a = value.getAsJsonArray();
            if (a.size() < 2) return;
            r.setMin(a.get(0).getAsDouble());
            r.setMax(a.get(1).getAsDouble());
            return;
        }
        if (setting instanceof MultiSelectSetting ms) {
            JsonArray a = value.getAsJsonArray();
            List<String> wanted = new ArrayList<>();
            for (JsonElement e : a) wanted.add(e.getAsString());
            for (String opt : ms.getOptions()) {
                if (wanted.contains(opt)) ms.select(opt);
                else ms.deselect(opt);
            }
        }
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static Module find(String name) {
        for (Module module : ModuleManager.getModules()) {
            if (module.getName().equalsIgnoreCase(name)) return module;
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Delete
    // ------------------------------------------------------------------

    public static String delete(String name) {
        File file = fileFor(name);
        if (file == null || !file.isFile()) return "Profile not found";
        try {
            Files.delete(file.toPath());
            return null;
        } catch (Throwable t) {
            return "Could not delete: " + t.getMessage();
        }
    }

    /** Last-modified time for the list, or 0 when it cannot be read. */
    public static long modified(String name) {
        File f = fileFor(name);
        return f == null ? 0L : f.lastModified();
    }

    /** Module / setting counts, for the list subtitle. */
    public static String summary(String name) {
        File file = fileFor(name);
        if (file == null || !file.isFile()) return "";
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonElement modules = root.get("modules");
            if (modules == null || !modules.isJsonObject()) return "";
            JsonObject mods = modules.getAsJsonObject();
            int enabled = 0;
            int settings = 0;
            for (String key : mods.keySet()) {
                JsonObject entry = asObject(mods.get(key));
                if (entry == null) continue;
                JsonElement e = entry.get("enabled");
                if (e != null && e.getAsBoolean()) enabled++;
                JsonElement s = entry.get("settings");
                if (s != null && s.isJsonObject()) settings += s.getAsJsonObject().size();
            }
            return enabled + " on / " + mods.size() + " modules";
        } catch (Throwable t) {
            return "";
        }
    }
}
