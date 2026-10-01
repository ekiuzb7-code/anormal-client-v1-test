package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;

public class ItemESP extends Module {
    public ItemESP() {
        super("ItemESP", "Renders glowing highlights and tags on dropped items", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity item) {
                item.setGlowing(true);
            }
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity item) {
                item.setGlowing(false);
            }
        }
    }
}
