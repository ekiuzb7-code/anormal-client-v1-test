package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class Tracers extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Tracer range", 100.0, 10.0, 500.0, 10.0);
    public final BooleanSetting players = new BooleanSetting("Players", "Trace players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Trace mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Trace animals", false);

    public Tracers() {
        super("Tracers", "Draws lines to entities", Category.UZNY11);
        addSetting(range);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
    }

    @Override
    public void onTick() {
        // Tracers logic - rendering
    }
}