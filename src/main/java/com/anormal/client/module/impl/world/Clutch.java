package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class Clutch extends Module {
    public final BooleanSetting onVoid = new BooleanSetting("On Void", "Saves from falling into void", true);
    public final BooleanSetting onLethal = new BooleanSetting("On Lethal Fall", "Saves from lethal fall damage", true);

    public Clutch() {
        super("Clutch", "Automatically places blocks to catch and prevent fatal falls", Category.WORLD);
        addSetting(onVoid);
        addSetting(onLethal);
    }
}
