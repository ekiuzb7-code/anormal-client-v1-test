package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class AutoTool extends Module {
    public final BooleanSetting weapon = new BooleanSetting("Weapon", "Auto swap to best weapon", true);
    public final BooleanSetting tool = new BooleanSetting("Tool", "Auto swap to best tool", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Swap delay (ticks)", 2, 0, 20, 1);
    public final BooleanSetting sword = new BooleanSetting("Sword", "Prefer sword over axe", true);

    private int delayTicks = 0;

    public AutoTool() {
        super("AutoTool", "Automatically selects best tool/weapon", Category.UZNY11);
        addSetting(weapon);
        addSetting(tool);
        addSetting(delay);
        addSetting(sword);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (delayTicks > 0) { delayTicks--; return; }

        // Auto tool logic
    }
}