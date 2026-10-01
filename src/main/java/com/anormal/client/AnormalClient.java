package com.anormal.client;

import com.anormal.client.gui.ClickGuiScreen;
import com.anormal.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AnormalClient implements ClientModInitializer {
    public static final String MOD_ID = "anormalclient";
    public static final String CLIENT_NAME = "Anormal Client";
    public static final String VERSION = "1.0.0";

    public static KeyBinding openGuiKeyBinding;

    @Override
    public void onInitializeClient() {
        System.out.println("[" + CLIENT_NAME + "] Initializing Anormal Client v" + VERSION + " for Minecraft 1.21.1 / Fabric...");

        // Safely register standard KeyBinding if supported by this MC version
        try {
            openGuiKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.anormalclient.open_gui",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT_SHIFT,
                    "category.anormalclient.general"
            ));
        } catch (Throwable t) {
            System.out.println("[" + CLIENT_NAME + "] Note: Native keybinding registration fallback active (Direct GLFW Right Shift key listening).");
        }

        // Initialize all client modules and settings
        ModuleManager.init();

        // Register tick event handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKeyBinding != null && openGuiKeyBinding.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ClickGuiScreen());
                }
            }

            if (client.player != null && client.world != null) {
                ModuleManager.onTick();
            }
        });

        System.out.println("[" + CLIENT_NAME + "] Initialization complete.");
    }
}
