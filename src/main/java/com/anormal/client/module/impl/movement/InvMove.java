package com.anormal.client.module.impl.movement;

import com.anormal.client.gui.ClickGuiScreen;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class InvMove extends Module {
    public InvMove() {
        super("InvMove", "Allows player movement (WASD/Jump) while in container/inventory screens", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen || mc.currentScreen instanceof ClickGuiScreen) {
            return;
        }

        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sprintKey
        };

        for (KeyBinding key : movementKeys) {
            int code = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()).getCode();
            if (code > 0) {
                key.setPressed(InputUtil.isKeyPressed(mc.getWindow(), code));
            }
        }
    }
}
