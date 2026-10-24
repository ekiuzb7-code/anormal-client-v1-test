package com.anormal.client.module.impl.client;

import com.anormal.client.gui.ProfilesScreen;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.Setting;
import net.minecraft.text.Text;

/**
 * Entry point for the profile system.
 *
 * <p>The module itself only owns the screen; the actual snapshot/restore logic lives in
 * {@link com.anormal.client.profiles.ProfileManager}. Opening the module (keybind or a click in
 * the ClickGUI) shows the profile browser, and pressing the keybind again closes it without
 * switching the feature off.</p>
 */
public class Profiles extends Module {

    public final ModeSetting preset = new ModeSetting("Preset", "Built-in gamemode preset", "None",
            "None", "Default", "BedWars", "SkyWars", "PvP", "LongRange");
    public final BooleanSetting applyPreset = new BooleanSetting("Apply Preset", "Apply the selected preset now", false);

    public Profiles() {
        super("Profiles", "Save, load, share and re-save complete client setups", Category.CLIENT);
        addSetting(preset);
        addSetting(applyPreset);
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen == null) {
            mc.setScreen(new ProfilesScreen(this));
        }
    }

    @Override
    public boolean onKeyPressed() {
        if (mc.currentScreen instanceof ProfilesScreen) {
            mc.setScreen(null);
            return true;
        }
        if (!isEnabled()) {
            setEnabled(true);
            return true;
        }
        mc.setScreen(new ProfilesScreen(this));
        return true;
    }

    @Override
    public void onTick() {
        if (applyPreset.isEnabled()) {
            applyPreset.setValue(false);
            applyPreset();
        }
    }

    /** Opens the browser without going through the module state. */
    public void openScreen() {
        if (mc.currentScreen == null) mc.setScreen(new ProfilesScreen(this));
    }

    // ------------------------------------------------------------------
    //  Built-in gamemode presets (low, anticheat-conscious values)
    // ------------------------------------------------------------------

    public void applyPreset() {
        String p = preset.getValue();
        if (p.equals("None")) return;
        try {
            // Baseline only: enables recommended modules + safe values.
            // Nothing is force-disabled - turn on whatever else you want.
            switch (p) {
                case "Default" -> disableAll();
                case "BedWars" -> presetBedWars();
                case "SkyWars" -> { presetBedWars(); presetSkyWars(); }
                case "PvP" -> presetPvP();
                case "LongRange" -> presetLongRange();
            }
            on("TextGUI");
            on("Keystrokes");
            on("FPS");
        } catch (Throwable ignored) {
        }
    }

    private void disableAll() {
        for (Module m : ModuleManager.getModules()) {
            if (m == this) continue;
            try {
                m.setEnabled(false);
            } catch (Throwable ignored) {
            }
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
                } catch (Throwable ignored) {
                }
                return;
            }
        }
    }

    private void on(String module) {
        Module m = find(module);
        if (m != null) {
            try {
                m.setEnabled(true);
            } catch (Throwable ignored) {
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
                } catch (Throwable ignored) {
                }
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
                } catch (Throwable ignored) {
                }
                return;
            }
        }
    }

    private Module find(String name) {
        for (Module m : ModuleManager.getModules()) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }
}
