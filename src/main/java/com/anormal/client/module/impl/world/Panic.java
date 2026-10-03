package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.List;

public class Panic extends Module {
    public final BooleanSetting reEnable = new BooleanSetting("Re-enable", "Second press restores disabled modules", true);
    private final List<Module> previouslyEnabled = new ArrayList<>();

    public Panic() {
        super("Panic", "Instantly disables all active modules with one key", Category.WORLD);
        addSetting(reEnable);
    }

    @Override
    public void onEnable() {
        if (previouslyEnabled.isEmpty()) {
            for (Module m : ModuleManager.getModules()) {
                if (m == this || m instanceof Panic) continue;
                if (m.isEnabled()) {
                    previouslyEnabled.add(m);
                    try {
                        m.setEnabled(false);
                    } catch (Throwable ignored) {}
                }
            }
            // Auto-off so next press restores (2-press flow instead of 3)
            setEnabled(false);
        } else if (reEnable.isEnabled()) {
            for (Module m : previouslyEnabled) {
                try {
                    m.setEnabled(true);
                } catch (Throwable ignored) {}
            }
            previouslyEnabled.clear();
            setEnabled(false);
        } else {
            previouslyEnabled.clear();
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
    }
}
