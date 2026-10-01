package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoJumpDelay extends Module {
    public NoJumpDelay() {
        super("NoJumpDelay", "Removes jump cooldown delay when continuously holding space", Category.MOVEMENT);
    }
}
