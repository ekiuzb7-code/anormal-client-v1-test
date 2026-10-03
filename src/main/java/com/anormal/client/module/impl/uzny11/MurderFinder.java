package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;

public class MurderFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Murder finder range", 50.0, 10.0, 100.0, 5.0);
    public final BooleanSetting chat = new BooleanSetting("Chat", "Announce in chat", false);

    public MurderFinder() {
        super("MurderFinder", "Finds murderer in murder mystery", Category.UZNY11);
        addSetting(range);
        addSetting(chat);
    }

    @Override
    public void onTick() {
        // MurderFinder logic
    }
}