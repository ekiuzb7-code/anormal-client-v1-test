package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class NoSlowdown extends Module {
    public final BooleanSetting limitItems = new BooleanSetting("Limit Items", "Only with swords held", false);

    public NoSlowdown() {
        super("NoSlowdown", "Prevents slowdown while using items", Category.UZNY11);
        addSetting(limitItems);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            boolean using = mc.player.isUsingItem() || mc.options.useKey.isPressed();
            if (!using) return;
            if (limitItems.getValue()) {
                String p = "";
                try { p = net.minecraft.registry.Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath(); } catch (Throwable t) { return; }
                if (!p.endsWith("sword")) return;
            }
            if (mc.player.forwardSpeed != 0) mc.player.setSprinting(true);
        } catch (Throwable ignored) {}
    }

    private boolean active = false;

    public boolean isActive() {
        return active;
    }

    @Override
    public void onDisable() {
        active = false;
    }
}
