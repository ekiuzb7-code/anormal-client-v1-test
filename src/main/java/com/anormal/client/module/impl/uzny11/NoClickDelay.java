package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoClickDelay extends Module {
    public NoClickDelay() {
        super("NoClickDelay", "Removes the click delay after missing an attack", Category.UZNY11);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            // Vanilla miss-swing gate lives on the client: zero it every tick
            mc.attackCooldown = 0;
        } catch (Throwable ignored) {}
    }
}
