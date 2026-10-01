package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class Blink extends Module {
    public Blink() {
        super("Blink", "Chokes movement packets to simulate instantaneous teleportation", Category.WORLD);
    }
}
