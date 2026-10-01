package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class ArmorSwitch extends Module {
    public ArmorSwitch() {
        super("ArmorSwitch", "Switches between two configured sets of armor on keypress", Category.INVENTORY);
    }
}
