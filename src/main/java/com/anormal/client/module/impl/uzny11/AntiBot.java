package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;

public class AntiBot extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "AntiBot mode", "Basic", "Basic", "Advanced", "Hypixel");
    public final BooleanSetting hitbox = new BooleanSetting("Hitbox", "Check hitbox size", true);
    public final BooleanSetting movement = new BooleanSetting("Movement", "Check movement patterns", true);

    public AntiBot() {
        super("AntiBot", "Ignores server anti-cheat bots", Category.UZNY11);
        addSetting(mode);
        addSetting(hitbox);
        addSetting(movement);
    }

    @Override
    public void onTick() {
        // AntiBot logic
    }
}