package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class InvCleaner extends Module {
    public InvCleaner() {
        super("InvCleaner", "Automatically cleans and drops junk items from inventory", Category.INVENTORY);
    }
}
