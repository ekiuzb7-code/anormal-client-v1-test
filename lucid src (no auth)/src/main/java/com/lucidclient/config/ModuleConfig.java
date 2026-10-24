package com.lucidclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lucidclient.LucidClient;
import com.lucidclient.module.Module;
import com.lucidclient.module.ModuleManager;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.module.setting.KeybindSetting;
import com.lucidclient.module.setting.Setting;
import com.lucidclient.module.setting.StringSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Persists every module's state across restarts: enabled/disabled, the
 * module keybind, and all setting values (toggles, sliders, dropdowns,
 * colors, keybinds, text fields).
 *
 * <p><b>Load</b> runs once when modules are registered. Setting values are
 * applied immediately (they're inert data), but the enabled flags are
 * staged in {@link #pendingEnabled} and applied by
 * {@link #applyPendingEnabled()} on the first client tick where the player
 * is in a world — module {@code onEnable()} hooks often need a live
 * MinecraftClient (world, window, player), which doesn't exist yet during
 * mod init.
 *
 * <p><b>Save</b> runs on an autosave cadence from the client tick and once
 * more on client shutdown via {@code ClientLifecycleEvents.CLIENT_STOPPING},
 * so at most a few seconds of changes are ever lost. Saving is cheap (one
 * small JSON file) and runs on the client thread, same as all mutations.
 */
public final class ModuleConfig {

    private static final Path DIR =
        FabricLoader.getInstance().getConfigDir().resolve("lucidclient");
    private static final Path FILE = DIR.resolve("modules.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Directory holding the user's named configs (the ClickGUI's Configs panel). */
    public static Path configsDir() { return DIR.resolve("configs"); }

    /** Extension of a named config file. */
    public static final String CONFIG_EXTENSION = ".json";

    /** Enabled flags staged at load time, applied on first world join. */
    private static final Map<String, Boolean> pendingEnabled = new HashMap<>();

    private ModuleConfig() {}

    /** Read saved settings + staged enabled states into the module registry. */
    public static synchronized void load(ModuleManager mgr) {
        loadFrom(mgr, FILE, true);
    }

    /**
     * Read a config file into the module registry.
     *
     * @param file         the JSON document to read
     * @param stageEnabled when true, enabled flags are staged and applied on
     *                     the first world join (startup); when false they are
     *                     applied straight away, which is what the ClickGUI's
     *                     "Load" button wants for a mid-session switch.
     */
    public static synchronized void loadFrom(ModuleManager mgr, Path file, boolean stageEnabled) {
        pendingEnabled.clear();
        try {
            if (file == null || !Files.exists(file)) return;
            String json = Files.readString(file);
            if (json.isBlank()) return;
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (Module m : mgr.getAll()) {
                if (!root.has(m.getName())) continue;
                JsonObject mo = root.getAsJsonObject(m.getName());
                if (mo == null) continue;

                // Stage the enabled flag — applied on first world join, or
                // applied immediately when the user asked for this file.
                if (mo.has("enabled") && mo.get("enabled").isJsonPrimitive()) {
                    boolean on = mo.get("enabled").getAsBoolean();
                    if (stageEnabled) pendingEnabled.put(m.getName(), on);
                    else if (on != m.isEnabled()) m.setEnabled(on);
                }
                if (mo.has("keybind") && mo.get("keybind").isJsonPrimitive()) {
                    m.setKeybind(mo.get("keybind").getAsInt());
                }

                if (!mo.has("settings") || !mo.get("settings").isJsonObject()) continue;
                JsonObject so = mo.getAsJsonObject("settings");

                for (Setting<?> s : m.getSettings()) {
                    if (!so.has(s.getName())) continue;
                    try {
                        applySetting(s, so.get(s.getName()));
                    } catch (Throwable t) {
                        LucidClient.LOGGER.warn("Config: failed to apply setting {}.{}: {}",
                            m.getName(), s.getName(), t.toString());
                    }
                }
            }
            LucidClient.LOGGER.info("Module config loaded from {} ({} staged enabled states).",
                file.getFileName(), pendingEnabled.size());
        } catch (Throwable t) {
            LucidClient.LOGGER.warn("Failed to load module config {}: {}", file, t.toString());
        }
    }

    /** Names (without extension) of every config saved in {@link #configsDir()}. */
    public static synchronized List<String> listNamed() {
        List<String> names = new ArrayList<>();
        Path dir = configsDir();
        if (!Files.isDirectory(dir)) return names;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().endsWith(CONFIG_EXTENSION))
                .forEach(p -> {
                    String n = p.getFileName().toString();
                    names.add(n.substring(0, n.length() - CONFIG_EXTENSION.length()));
                });
        } catch (IOException e) {
            LucidClient.LOGGER.warn("Failed to list configs: {}", e.toString());
        }
        names.sort(Comparator.comparing(String::toLowerCase));
        return names;
    }

    /** Path of a named config, sanitised so a name can never escape the folder. */
    public static Path namedPath(String name) {
        StringBuilder safe = new StringBuilder();
        for (char c : name.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-') {
                safe.append(c);
            }
        }
        return configsDir().resolve(safe + CONFIG_EXTENSION);
    }

    /** Save the current state to a named config. @return true on success. */
    public static synchronized boolean saveNamed(ModuleManager mgr, String name) {
        if (name == null || name.isBlank()) return false;
        return saveTo(mgr, namedPath(name));
    }

    /** Load a named config, applying enabled flags immediately. */
    public static synchronized boolean loadNamed(ModuleManager mgr, String name) {
        if (name == null || name.isBlank()) return false;
        Path path = namedPath(name);
        if (!Files.exists(path)) return false;
        loadFrom(mgr, path, false);
        return true;
    }

    /** Delete a named config. @return true when a file was removed. */
    public static synchronized boolean deleteNamed(String name) {
        if (name == null || name.isBlank()) return false;
        try {
            return Files.deleteIfExists(namedPath(name));
        } catch (IOException e) {
            LucidClient.LOGGER.warn("Failed to delete config {}: {}", name, e.toString());
            return false;
        }
    }

    /** Apply one saved JSON value to a setting, dispatched by concrete type. */
    private static void applySetting(Setting<?> s, com.google.gson.JsonElement el) {
        if (!el.isJsonPrimitive()) return;
        if (s instanceof BooleanSetting b) b.set(el.getAsBoolean());
        else if (s instanceof IntSetting i) i.set(el.getAsInt());
        else if (s instanceof FloatSetting f) f.set(el.getAsFloat());
        else if (s instanceof KeybindSetting k) k.setKey(el.getAsInt());
        else if (s instanceof ColorSetting c) c.setColor(el.getAsInt());
        else if (s instanceof EnumSetting e) e.set(el.getAsString());
        else if (s instanceof StringSetting st) st.set(el.getAsString());
    }

    /**
     * Apply the enabled flags staged by {@link #load}. Called once per
     * session from the client tick, the first time the player is in a world.
     */
    public static synchronized void applyPendingEnabled(ModuleManager mgr) {
        if (pendingEnabled.isEmpty()) return;
        for (Module m : mgr.getAll()) {
            Boolean on = pendingEnabled.get(m.getName());
            if (on != null && on != m.isEnabled()) {
                m.setEnabled(on);
            }
        }
        pendingEnabled.clear();
        LucidClient.LOGGER.info("Module config: enabled states applied.");
    }

    /** Serialize every module's state + settings to the config file. */
    public static synchronized void save(ModuleManager mgr) {
        saveTo(mgr, FILE);
    }

    /** Serialize every module's state + settings to {@code file}. */
    public static synchronized boolean saveTo(ModuleManager mgr, Path file) {
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (Module m : mgr.getAll()) {
                JsonObject mo = new JsonObject();
                mo.addProperty("enabled", m.isEnabled());
                mo.addProperty("keybind", m.getKeybind());
                JsonObject so = new JsonObject();
                for (Setting<?> s : m.getSettings()) {
                    Object v = s.get();
                    if (v instanceof Boolean b) so.addProperty(s.getName(), b);
                    else if (v instanceof Number n) so.addProperty(s.getName(), n);
                    else so.addProperty(s.getName(), String.valueOf(v));
                }
                mo.add("settings", so);
                root.add(m.getName(), mo);
            }
            Files.writeString(file, GSON.toJson(root));
            return true;
        } catch (Throwable t) {
            LucidClient.LOGGER.warn("Failed to save module config {}: {}", file, t.toString());
            return false;
        }
    }

    /** Register the shutdown hook so the final state is always persisted. */
    public static void registerShutdownSave(LucidClient client) {
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            if (client.getModuleManager() != null && !client.getModuleManager().isEmpty()) {
                save(client.getModuleManager());
            }
        });
    }
}
