package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class MLG extends Module {
    public final BooleanSetting useBuckets = new BooleanSetting("Use Buckets", "Places water bucket to break fall", true);
    public final BooleanSetting pickUp = new BooleanSetting("Pick Up Water", "Picks water back up after landing", true);

    public MLG() {
        super("MLG", "Automatically uses water buckets or cobwebs to prevent fall damage", Category.WORLD);
        addSetting(useBuckets);
        addSetting(pickUp);
    }
}
