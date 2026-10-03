package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class NameTags extends Module {
    public final NumberSetting range = new NumberSetting("Range", "NameTag range", 100.0, 10.0, 500.0, 10.0);
    public final BooleanSetting players = new BooleanSetting("Players", "Show player names", true);
    public final BooleanSetting health = new BooleanSetting("Health", "Show health", true);
    public final BooleanSetting distance = new BooleanSetting("Distance", "Show distance", true);
    public final BooleanSetting armor = new BooleanSetting("Armor", "Show armor", false);

    public NameTags() {
        super("NameTags", "Shows entity names through walls", Category.UZNY11);
        addSetting(range);
        addSetting(players);
        addSetting(health);
        addSetting(distance);
        addSetting(armor);
    }

    @Override
    public void onTick() {
        // NameTags logic - rendering
    }
}