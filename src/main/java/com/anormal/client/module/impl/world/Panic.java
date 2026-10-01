package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

public class Panic extends Module {
    private final List<Module> previouslyEnabled = new ArrayList<>();

    public Panic() {
        super("Panic", "Instantly disables all active modules with one key", Category.WORLD);
    }

    @Override
    public void onEnable() {
        if (previouslyEnabled.isEmpty()) {
            for (Module m : ModuleManager.getModules()) {
                if (m != this && m.isEnabled()) {
                    previouslyEnabled.add(m);
                    m.setEnabled(false);
                }
            }
        } else {
            for (Module m : previouslyEnabled) {
                m.setEnabled(true);
            }
            previouslyEnabled.clear();
            setEnabled(false);
        }
    }
}
