package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Profiles extends Module {
    public final ModeSetting slot = new ModeSetting("Slot", "Profile slot", "1", "1", "2", "3", "4", "5");
    public final BooleanSetting saveNow = new BooleanSetting("Save Now", "Save current setup into slot", false);
    public final BooleanSetting loadNow = new BooleanSetting("Load Now", "Load slot setup", false);
    public final BooleanSetting autoLoadStates = new BooleanSetting("Auto-Load States", "Restore enabled states on switch", true);
    public final ModeSetting preset = new ModeSetting("Preset", "Built-in gamemode preset", "None", "None", "Default", "BedWars", "SkyWars", "PvP", "LongRange");
    public final BooleanSetting applyPreset = new BooleanSetting("Apply Preset", "Apply selected preset now", false);

    public Profiles() {
        super("Profiles", "Save and switch module setups per server or playstyle", Category.CLIENT);
        addSetting(slot);
        addSetting(saveNow);
        addSetting(loadNow);
        addSetting(autoLoadStates);
        addSetting(preset);
        addSetting(applyPreset);
    }

    private File file() {
        try {
            File dir = new File(MinecraftClient.getInstance().runDirectory, "config/anormal");
            dir.mkdirs();
            return new File(dir, "profile-" + slot.getValue() + ".txt");
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onTick() {
        if (saveNow.isEnabled()) {
            saveNow.setValue(false);
            save();
        }
        if (loadNow.isEnabled()) {
            loadNow.setValue(false);
            load();
        }
        if (applyPreset.isEnabled()) {
            applyPreset.setValue(false);
            applyPreset();
        }
    }

    public void save() {
        File f = file();
        if (f == null) return;
        try {
            List<String> lines = new ArrayList<>();
            for (Module m : ModuleManager.getModules()) {
                lines.add("M:" + m.getName() + ":" + m.isEnabled());
                for (Setting<?> s : m.getSettings()) {
                    try {
                        lines.add("S:" + m.getName() + "." + s.getName() + ":" + s.getValue());
                    } catch (Throwable ignored) {}
                }
            }
            Files.write(f.toPath(), lines);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void load() {
        File f = file();
        if (f == null || !f.exists()) return;
        try {
            for (String line : Files.readAllLines(f.toPath())) {
                try {
                    if (line.startsWith("M:")) {
                        String[] p = line.split(":", 3);
                        if (p.length < 3) continue;
                        Module m = find(p[1]);
                        if (m != null && m != this && autoLoadStates.isEnabled()) {
                            m.setEnabled(Boolean.parseBoolean(p[2]));
                        }
                    } else if (line.startsWith("S:")) {
                        String[] p = line.split(":", 3);
                        if (p.length < 3) continue;
                        int dot = p[1].lastIndexOf('.');
                        if (dot < 0) continue;
                        Module m = find(p[1].substring(0, dot));
                        if (m == null || m == this) continue;
                        String sname = p[1].substring(dot + 1);
                        for (Setting<?> s : m.getSettings()) {
                            if (!s.getName().equals(sname)) continue;
                            String v = p[2];
                            try {
                                if (s instanceof BooleanSetting) ((Setting<Boolean>) (Setting) s).setValue(Boolean.parseBoolean(v));
                                else if (s instanceof com.anormal.client.setting.NumberSetting) ((Setting<Double>) (Setting) s).setValue(Double.parseDouble(v));
                                else if (s instanceof ModeSetting ms) {
                                    if (ms.getModes().contains(v)) ms.setMode(v);
                                } else ((Setting<Integer>) (Setting) s).setValue(Integer.parseInt(v));
                            } catch (Throwable ignored) {}
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private Module find(String name) {
        for (Module m : ModuleManager.getModules()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    // ---------- Built-in gamemode presets (low, anticheat-conscious values) ----------

    public void applyPreset() {
        String p = preset.getValue();
        if (p.equals("None")) return;
        try {
            // Baseline only: enables recommended modules + safe values.
            // Nothing is force-disabled — turn on whatever else you want.
            switch (p) {
                case "Default" -> disableAll();
                case "BedWars" -> presetBedWars();
                case "SkyWars" -> { presetBedWars(); presetSkyWars(); }
                case "PvP" -> presetPvP();
                case "LongRange" -> presetLongRange();
            }
            // Legit HUD always on (invisible to anticheats)
            on("TextGUI");
            on("Keystrokes");
            on("FPS");
        } catch (Throwable ignored) {}
    }

    private void disableAll() {
        for (Module m : ModuleManager.getModules()) {
            if (m == this) continue;
            try {
                m.setEnabled(false);
            } catch (Throwable ignored) {}
        }
    }

    private void presetBedWars() {
        on("Sprint");
        on("AimAssist");
        num("AimAssist", "H-Speed", 4.0);
        num("AimAssist", "V-Speed", 3.0);
        num("AimAssist", "Max Angle", 60.0);
        num("AimAssist", "Distance", 4.2);
        on("AutoClicker");
        num("AutoClicker", "Min CPS", 8.0);
        num("AutoClicker", "Max CPS", 11.0);
        bool("AutoClicker", "Hold to Click", true);
        bool("AutoClicker", "Break Blocks", true);
        on("WTap");
        num("WTap", "Chance", 30.0);
        on("JumpReset");
        num("JumpReset", "Chance", 60.0);
        on("TargetInfo");
    }

    private void presetSkyWars() {
        on("ChestStealer");
        num("ChestStealer", "Min Delay", 2.0);
        num("ChestStealer", "Max Delay", 4.0);
        bool("ChestStealer", "Shuffle", true);
        bool("ChestStealer", "Blacklist", true);
        on("AutoArmor");
        on("AutoTool");
        on("TargetFilter");
    }

    private void presetPvP() {
        on("AimAssist");
        num("AimAssist", "H-Speed", 6.0);
        num("AimAssist", "V-Speed", 4.0);
        num("AimAssist", "Max Angle", 90.0);
        num("AimAssist", "Distance", 4.5);
        on("AutoClicker");
        num("AutoClicker", "Min CPS", 9.0);
        num("AutoClicker", "Max CPS", 13.0);
        bool("AutoClicker", "Hold to Click", true);
        on("WTap");
        num("WTap", "Chance", 50.0);
        on("JumpReset");
        num("JumpReset", "Chance", 70.0);
        on("Velocity");
        num("Velocity", "Horizontal", 85.0);
        num("Velocity", "Vertical", 100.0);
        num("Velocity", "Chance", 50.0);
        on("HitSelect");
        num("HitSelect", "Chance", 40.0);
        on("TargetInfo");
        on("DuelInfo");
        on("ReachDisplay");
    }

    private void presetLongRange() {
        // Latency-edge PvP: delayed-position abuse + long reach timing
        on("AimAssist");
        num("AimAssist", "H-Speed", 7.0);
        num("AimAssist", "V-Speed", 5.0);
        num("AimAssist", "Max Angle", 120.0);
        num("AimAssist", "Distance", 6.0);
        on("AutoClicker");
        num("AutoClicker", "Min CPS", 9.0);
        num("AutoClicker", "Max CPS", 12.0);
        bool("AutoClicker", "Hold to Click", true);
        on("BackTrack");
        num("BackTrack", "Latency", 150.0);
        bool("BackTrack", "Render Server Pos", true);
        on("FakeLag");
        mode("FakeLag", "Mode", "Repel");
        num("FakeLag", "Delay", 120.0);
        on("Reach");
        num("Reach", "Range", 3.5);
        on("WTap");
        num("WTap", "Chance", 60.0);
        on("JumpReset");
        num("JumpReset", "Chance", 70.0);
        on("Velocity");
        num("Velocity", "Horizontal", 90.0);
        num("Velocity", "Vertical", 100.0);
        num("Velocity", "Chance", 60.0);
        on("TargetInfo");
        on("DuelInfo");
    }

    private void mode(String module, String setting, String value) {
        Module m = find(module);
        if (m == null) return;
        for (Setting<?> s : m.getSettings()) {
            if (s.getName().equals(setting) && s instanceof ModeSetting ms) {
                try {
                    if (ms.getModes().contains(value)) ms.setValue(value);
                } catch (Throwable ignored) {}
                return;
            }
        }
    }

    private void on(String module) {
        Module m = find(module);
        if (m != null) {
            try {
                m.setEnabled(true);
            } catch (Throwable ignored) {}
        }
    }

    private void off(String... modules) {
        for (String name : modules) {
            Module m = find(name);
            if (m != null) {
                try {
                    m.setEnabled(false);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void num(String module, String setting, double value) {
        Module m = find(module);
        if (m == null) return;
        for (Setting<?> s : m.getSettings()) {
            if (s.getName().equals(setting) && s instanceof NumberSetting n) {
                try {
                    n.setValue(value);
                } catch (Throwable ignored) {}
                return;
            }
        }
    }

    private void bool(String module, String setting, boolean value) {
        Module m = find(module);
        if (m == null) return;
        for (Setting<?> s : m.getSettings()) {
            if (s.getName().equals(setting) && s instanceof BooleanSetting b) {
                try {
                    b.setValue(value);
                } catch (Throwable ignored) {}
                return;
            }
        }
    }
}
