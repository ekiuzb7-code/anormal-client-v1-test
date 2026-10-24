package dev.zaza.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zaza.ZazaHook;
import dev.zaza.client.ui.ZazaGui;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZazaHook.id("keybinds"));

    public static final KeyMapping OPEN_GUI = register("open_gui", GLFW.GLFW_KEY_RIGHT_SHIFT);

    private Keybinds() {}

    public static void register() {
        // Registration happens via the static initializer; nothing else needed here.
    }

    public static void tick(Minecraft client) {
        boolean pressed = false;
        while (OPEN_GUI.consumeClick()) pressed = true;
        if (pressed && client.gui.screen() == null && client.player != null) {
            client.gui.setScreen(new ZazaGui());
        }
    }

    private static KeyMapping register(String name, int key) {
        var mapping = new KeyMapping("key." + ZazaHook.MOD_ID + "." + name, InputConstants.Type.KEYSYM, key, CATEGORY);
        return KeyMappingHelper.registerKeyMapping(mapping);
    }
}
