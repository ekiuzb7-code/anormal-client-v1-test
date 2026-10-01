package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import org.lwjgl.glfw.GLFW;

public class FreeLook extends Module {
    public FreeLook() {
        super("FreeLook", "Detached 3rd-person camera allowing free view rotations", Category.RENDER, GLFW.GLFW_KEY_V);
    }
}
