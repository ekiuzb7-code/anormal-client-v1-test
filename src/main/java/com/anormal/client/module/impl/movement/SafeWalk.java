package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class SafeWalk extends Module {
    public SafeWalk() {
        super("SafeWalk", "Prevents walking off block edges without slowing down", Category.MOVEMENT);
    }
}
