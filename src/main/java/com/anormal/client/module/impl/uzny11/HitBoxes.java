package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class HitBoxes extends Module {
    public final NumberSetting expand = new NumberSetting("Expand", "Hitbox expansion", 0.5, 0.0, 2.0, 0.1);
    public final BooleanSetting players = new BooleanSetting("Players", "Expand player hitboxes", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Expand mob hitboxes", false);

    public HitBoxes() {
        super("HitBoxes", "Expands entity hitboxes", Category.UZNY11);
        addSetting(expand);
        addSetting(players);
        addSetting(mobs);
    }

    @Override
    public void onTick() {
        // HitBoxes logic - would need mixin
    }
}