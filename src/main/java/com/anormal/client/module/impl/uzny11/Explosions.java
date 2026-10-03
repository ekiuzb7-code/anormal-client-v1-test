package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class Explosions extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Explosion render range", 50.0, 10.0, 200.0, 10.0);
    public final BooleanSetting tnt = new BooleanSetting("TNT", "Show TNT explosions", true);
    public final BooleanSetting respawnAnchor = new BooleanSetting("Respawn Anchor", "Show anchor explosions", true);
    public final BooleanSetting beds = new BooleanSetting("Beds", "Show bed explosions", true);
    public final BooleanSetting crystals = new BooleanSetting("Crystals", "Show crystal explosions", true);

    public Explosions() {
        super("Explosions", "Shows explosion radius", Category.UZNY11);
        addSetting(range);
        addSetting(tnt);
        addSetting(respawnAnchor);
        addSetting(beds);
        addSetting(crystals);
    }

    @Override
    public void onTick() {
        // Explosions logic
    }
}