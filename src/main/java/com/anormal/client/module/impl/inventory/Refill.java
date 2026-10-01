package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class Refill extends Module {
    public Refill() {
        super("Refill", "Refills hotbar with healing potions and soups from inventory", Category.INVENTORY);
    }
}
