package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoClickDelay extends Module {
    public NoClickDelay() {
        super("NoClickDelay", "Removes the attack delay caused by missing a hit", Category.PLAYER);
        setEnabled(true);
    }
}
