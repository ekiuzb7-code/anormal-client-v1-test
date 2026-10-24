package dev.zaza.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zaza.ZazaHook;
import dev.zaza.client.ZazaClient;
import dev.zaza.client.module.Module;
import dev.zaza.client.ui.ZazaSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Data(Map<String, Boolean> modules, Map<String, Map<String, Object>> settings, int theme, int sound,
                       int volume, String closeAnimation) {}

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("zazahook.json");
    }

    private ConfigStore() {}

    public static void load() {
        try {
            if (Files.exists(path())) {
                Data data = GSON.fromJson(Files.readString(path()), Data.class);
                if (data != null) apply(data);
            }
        } catch (Exception exception) {
            ZazaHook.LOGGER.warn("Cannot load zazahook config", exception);
        }
    }

    public static void save() {
        try {
            Map<String, Boolean> modules = new HashMap<>();
            for (var module : ZazaClient.MODULES.all()) modules.put(module.id(), module.enabled());
            Map<String, Map<String, Object>> settings = new LinkedHashMap<>();
            for (var module : ZazaClient.MODULES.all()) {
                modules.put(module.id(), module.enabled());
                Map<String, Object> moduleSettings = settings(module);
                if (moduleSettings != null && !moduleSettings.isEmpty()) settings.put(module.id(), moduleSettings);
            }
            Data data = new Data(modules, settings, ZazaSettings.themeIndex(), ZazaSettings.soundIndex(),
                    ZazaSettings.volume(), ZazaSettings.closeAnimation().name());
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(data));
        } catch (Exception exception) {
            ZazaHook.LOGGER.warn("Cannot save zazahook config", exception);
        }
    }

    private static void apply(Data data) {
        if (data.modules() != null) {
            for (var entry : data.modules().entrySet()) {
                var module = ZazaClient.MODULES.byId(entry.getKey());
                if (module != null) {
                    module.setEnabled(entry.getValue());
                    Map<String, Object> moduleSettings = data.settings() != null ? data.settings().get(entry.getKey()) : null;
                    if (moduleSettings != null) applySettings(module, moduleSettings);
                }
            }
        }
        ZazaSettings.applyTheme(data.theme());
        ZazaSettings.setSound(data.sound());
        ZazaSettings.setVolume(data.volume());
        if (data.closeAnimation() != null) ZazaSettings.setCloseAnimation(data.closeAnimation());
    }

    /** The aim assist exposes an editable settings list; other modules have none yet. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> settings(Module module) {
        if (module instanceof dev.zaza.client.module.impl.CombatModule combat) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (var setting : combat.settings()) values.put(setting.id(), setting.get());
            return values;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void applySettings(Module module, Map<String, Object> values) {
        if (!(module instanceof dev.zaza.client.module.impl.CombatModule combat)) return;
        for (var setting : combat.settings()) {
            Object value = values.get(setting.id());
            if (value == null) continue;
            switch (setting) {
                case dev.zaza.client.setting.BoolSetting bool ->
                        bool.set(value instanceof Boolean flag ? flag : ((Number) value).intValue() != 0);
                case dev.zaza.client.setting.SliderSetting slider -> slider.set(((Number) value).intValue());
                case dev.zaza.client.setting.EnumSetting option -> option.set(((Number) value).intValue());
                case dev.zaza.client.setting.MultiSetting multi -> {
                    if (value instanceof List<?> list) {
                        multi.clear();
                        for (Object item : list) multi.toggle(((Number) item).intValue());
                    }
                }
                default -> {
                }
            }
        }
    }
}
