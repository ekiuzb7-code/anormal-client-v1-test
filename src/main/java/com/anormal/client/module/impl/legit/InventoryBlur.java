package com.anormal.client.module.impl.legit;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class InventoryBlur extends Module {
    public InventoryBlur() {
        super("InventoryBlur", "Adds a background blur effect when opening inventories", Category.LEGIT);
        setEnabled(true);
    }
}
