package dev.autocc.client;

import dev.autocc.client.build.BuildEngine;
import dev.autocc.client.build.BuildTracker;
import dev.autocc.client.gui.AutoCcScreen;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class AutoCcClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(
            Identifier.of("auto-cc", "general")
    );

    private static final KeyBinding OPEN_MENU = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                    "key.auto-cc.open",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT_SHIFT,
                    CATEGORY
            )
    );

    @Override
    public void onInitializeClient() {
        // Shows build progress and completion at the top right while the menu is closed.
        new BuildTracker(BuildEngine.get()).register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.wasPressed()) {
                if (client.currentScreen == null) {
                    MinecraftClient.getInstance().setScreen(new AutoCcScreen(Text.literal("Auto cc")));
                }
            }
            // Schematic images are rendered and uploaded between frames, never during a render pass.
            if (client.currentScreen instanceof AutoCcScreen screen) {
                screen.tickPreview();
            }
            // Auto build advances on the client tick, also outside any render pass.
            BuildEngine.get().tick(client);
        });
    }
}
