package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NameTags extends Module {
    public NameTags() {
        super("NameTags", "Renders enhanced player nametags, health, and distance through walls", Category.RENDER);
        setEnabled(true);
    }
}
