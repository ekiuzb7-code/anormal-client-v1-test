package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class AutoArmor extends Module {
    public AutoArmor() {
        super("AutoArmor", "Automatically equips the highest protection armor available", Category.INVENTORY);
    }
}
