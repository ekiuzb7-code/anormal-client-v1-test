package com.anormal.client;

import com.anormal.client.gui.ClickGuiScreen;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class AnormalClient implements ClientModInitializer {
    public static final String MOD_ID = "anormalclient";
    public static final String CLIENT_NAME = "Anormal Client";
    public static final String VERSION = "1.0.0";

    private static final boolean[] KEY_STATE = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private static final boolean[] MOUSE_STATE = new boolean[8];
    private static boolean rightShiftWasDown = false;

    @Override
    public void onInitializeClient() {
        System.out.println("[" + CLIENT_NAME + "] Initializing Anormal Client v" + VERSION + " for Fabric...");

        // Initialize all client modules and settings
        ModuleManager.init();

        // Restore HUD layout from previous sessions
        try {
            com.anormal.client.gui.HudEditorScreen.loadPositions();
        } catch (Throwable ignored) {}
        try {
            com.anormal.client.module.impl.render.Waypoints.loadWaypoints();
        } catch (Throwable ignored) {}

        // Register Fabric HUD Render Callback for 2D HUD overlays
        try {
            HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> {
                ModuleManager.onRender2D(drawContext, renderTickCounter.getTickProgress(false));
            });
        } catch (Throwable t) {
            System.out.println("[" + CLIENT_NAME + "] HudRenderCallback note: " + t.getMessage());
        }

        // Register universal client tick event handler (robust GLFW polling across all MC versions)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (client.getWindow() != null) {
                    long window = client.getWindow().getHandle();
                    if (window != 0) {
                        boolean rightShiftDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                        if (rightShiftDown && !rightShiftWasDown) {
                            if (client.currentScreen == null) {
                                client.setScreen(new ClickGuiScreen());
                            }
                        }
                        rightShiftWasDown = rightShiftDown;

                        if (client.currentScreen == null) {
                            for (Module module : ModuleManager.getModules()) {
                                int key = module.getKey();
                                if (key > 0 && key <= GLFW.GLFW_KEY_LAST) {
                                    boolean isDown = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
                                    if (isDown && !KEY_STATE[key]) {
                                        module.toggle();
                                    }
                                    KEY_STATE[key] = isDown;
                                } else if (com.anormal.client.setting.KeybindSetting.isMouseCode(key)) {
                                    int btn = com.anormal.client.setting.KeybindSetting.mouseButton(key);
                                    if (btn <= 0 || btn >= MOUSE_STATE.length) continue; // left click never toggles
                                    {
                                        boolean isDown = GLFW.glfwGetMouseButton(window, btn) == GLFW.GLFW_PRESS;
                                        if (isDown && !MOUSE_STATE[btn]) {
                                            module.toggle();
                                        }
                                        MOUSE_STATE[btn] = isDown;
                                    }
                                }
                            }
                        }
                    }
                }

                if (client.player != null && client.world != null) {
                    ModuleManager.onTick();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("[" + CLIENT_NAME + "] Initialization complete.");
    }
}
