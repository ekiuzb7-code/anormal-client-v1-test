package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoHurtCam extends Module {
    public NoHurtCam() {
        super("NoHurtCam", "Removes camera screen shake and tilt when taking damage", Category.RENDER);
        setEnabled(true);
    }
}
