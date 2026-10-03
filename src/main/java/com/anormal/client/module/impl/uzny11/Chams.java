package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

public class Chams extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Chams mode", "Normal", "Normal", "Wireframe", "Flat");
    public final BooleanSetting players = new BooleanSetting("Players", "Chams on players", true);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Show invisible entities", false);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", "Show through walls", true);

    public Chams() {
        super("Chams", "Shows entity models through walls", Category.UZNY11);
        addSetting(mode);
        addSetting(players);
        addSetting(invisibles);
        addSetting(throughWalls);
    }

    @Override
    public void onTick() {
        // Chams logic - rendering
    }
}