package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.setting.BooleanSetting;

public class Panic extends Module {
    public final BooleanSetting reenable = new BooleanSetting("Re-enable", "Re-enable disabled modules", true);

    private boolean wasEnabled = false;

    public Panic() {
        super("Panic", "Disables all cheat modules instantly", Category.UZNY11);
        addSetting(reenable);
    }

    @Override
    public void onEnable() {
        for (var module : ModuleManager.getModules()) {
            if (module.isEnabled() && module.getCategory() != Category.CLIENT && module != this) {
                module.setEnabled(false);
            }
        }
    }

    @Override
    public void onDisable() {
        if (reenable.isEnabled()) {
            for (var module : ModuleManager.getModules()) {
                if (!module.isEnabled() && module.getCategory() != Category.CLIENT && module != this) {
                    module.setEnabled(true);
                }
            }
        }
    }
}