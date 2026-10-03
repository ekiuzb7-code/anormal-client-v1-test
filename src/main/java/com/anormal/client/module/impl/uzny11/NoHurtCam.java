package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoHurtCam extends Module {
    public NoHurtCam() {
        super("NoHurtCam", "Disables hurt camera shake", Category.UZNY11);
    }

    @Override
    public void onTick() {
        // NoHurtCam logic - would need mixin
    }
}