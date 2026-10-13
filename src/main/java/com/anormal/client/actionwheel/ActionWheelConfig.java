package com.anormal.client.actionwheel;

import com.anormal.client.util.ColorUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ActionWheelConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<WheelAction> actions = new ArrayList<>();
    private static boolean initialized = false;

    public static List<WheelAction> getActions() {
        if (!initialized) init();
        return actions;
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        if (!load()) {
            loadUniversalPreset();
            save();
        }
    }

    public static void loadUniversalPreset() {
        actions.clear();
        actions.add(new WheelAction("RTP", WheelAction.Type.COMMAND, "/rtp", "⚡", ColorUtils.rgba(0, 230, 255, 255)));
        actions.add(new WheelAction("Home", WheelAction.Type.COMMAND, "/home", "🏠", ColorUtils.rgba(255, 215, 0, 255)));
        actions.add(new WheelAction("Spawn", WheelAction.Type.COMMAND, "/spawn", "🏰", ColorUtils.rgba(80, 240, 120, 255)));
        actions.add(new WheelAction("Base Finder", WheelAction.Type.MODULE, "BaseFinder", "🔍", ColorUtils.rgba(180, 70, 255, 255)));
        actions.add(new WheelAction("Chunk Finder", WheelAction.Type.MODULE, "ChunkFinder", "💎", ColorUtils.rgba(255, 80, 220, 255)));
        actions.add(new WheelAction("XRay", WheelAction.Type.MODULE, "XRay", "👁", ColorUtils.rgba(255, 140, 0, 255)));
        actions.add(new WheelAction("Shop / AH", WheelAction.Type.COMMAND, "/ah", "💰", ColorUtils.rgba(255, 220, 50, 255)));
        actions.add(new WheelAction("TPA / Clan", WheelAction.Type.COMMAND, "/tpa", "⚔", ColorUtils.rgba(255, 60, 60, 255)));
    }

    public static void loadDonutSMPPreset() {
        actions.clear();
        actions.add(new WheelAction("RTP", WheelAction.Type.COMMAND, "/rtp", "⚡", ColorUtils.rgba(0, 230, 255, 255)));
        actions.add(new WheelAction("Home 1", WheelAction.Type.COMMAND, "/home 1", "🏠", ColorUtils.rgba(255, 215, 0, 255)));
        actions.add(new WheelAction("Auction", WheelAction.Type.COMMAND, "/ah", "💰", ColorUtils.rgba(255, 180, 0, 255)));
        actions.add(new WheelAction("Sell", WheelAction.Type.COMMAND, "/sell", "💵", ColorUtils.rgba(80, 240, 120, 255)));
        actions.add(new WheelAction("Base Finder", WheelAction.Type.MODULE, "BaseFinder", "🔍", ColorUtils.rgba(180, 70, 255, 255)));
        actions.add(new WheelAction("Chunk Finder", WheelAction.Type.MODULE, "ChunkFinder", "💎", ColorUtils.rgba(255, 80, 220, 255)));
        actions.add(new WheelAction("Buy Totem", WheelAction.Type.COMMAND, "/buy totem", "🛡", ColorUtils.rgba(255, 220, 0, 255)));
        actions.add(new WheelAction("Spawn", WheelAction.Type.COMMAND, "/spawn", "🏰", ColorUtils.rgba(0, 200, 255, 255)));
    }

    public static void loadLifestealPreset() {
        actions.clear();
        actions.add(new WheelAction("RTP", WheelAction.Type.COMMAND, "/rtp", "⚡", ColorUtils.rgba(0, 230, 255, 255)));
        actions.add(new WheelAction("Base", WheelAction.Type.COMMAND, "/home", "🏠", ColorUtils.rgba(255, 215, 0, 255)));
        actions.add(new WheelAction("Kit", WheelAction.Type.COMMAND, "/kit", "📦", ColorUtils.rgba(80, 240, 120, 255)));
        actions.add(new WheelAction("KillAura", WheelAction.Type.MODULE, "KillAura", "⚔", ColorUtils.rgba(255, 60, 60, 255)));
        actions.add(new WheelAction("AutoCrystal", WheelAction.Type.MODULE, "AutoCrystal", "🔮", ColorUtils.rgba(255, 80, 220, 255)));
        actions.add(new WheelAction("XRay", WheelAction.Type.MODULE, "XRay", "👁", ColorUtils.rgba(255, 140, 0, 255)));
        actions.add(new WheelAction("TPA", WheelAction.Type.COMMAND, "/tpa", "🤝", ColorUtils.rgba(0, 200, 255, 255)));
        actions.add(new WheelAction("Clan Alert", WheelAction.Type.MESSAGE, "Enemy spotted near me!", "📢", ColorUtils.rgba(255, 120, 0, 255)));
    }

    private static File getConfigFile() {
        File dir = FabricLoader.getInstance().getConfigDir().toFile();
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "anormal_action_wheel.json");
    }

    public static boolean save() {
        try {
            File f = getConfigFile();
            try (FileWriter writer = new FileWriter(f)) {
                GSON.toJson(actions, writer);
            }
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    public static boolean load() {
        try {
            File f = getConfigFile();
            if (!f.exists()) return false;
            try (FileReader reader = new FileReader(f)) {
                Type type = new TypeToken<List<WheelAction>>() {}.getType();
                List<WheelAction> loaded = GSON.fromJson(reader, type);
                if (loaded != null && !loaded.isEmpty()) {
                    actions.clear();
                    actions.addAll(loaded);
                    return true;
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
    }
}
