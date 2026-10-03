package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

public class ESP extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "ESP mode", "Box", "Box", "Outline", "Skeleton");
    public final BooleanSetting players = new BooleanSetting("Players", "Show players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Show mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Show animals", false);
    public final BooleanSetting invisibles = new BooleanSetting("Invisibles", "Show invisibles", false);

    public ESP() {
        super("ESP", "Shows entities through walls", Category.UZNY11);
        addSetting(mode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(invisibles);
    }

    @Override
    public void onTick() {
        // ESP logic - would need mixin for rendering
    }
}