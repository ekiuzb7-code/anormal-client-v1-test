package dev.zaza.client;

import dev.zaza.client.config.ConfigStore;
import dev.zaza.client.input.Keybinds;
import dev.zaza.client.module.ModuleManager;
import dev.zaza.client.render.hud.ZazaHud;
import dev.zaza.client.rotation.RotationManager;
import dev.zaza.client.rotation.ZazaRotations;
import dev.zaza.client.ui.Theme;
import dev.zaza.client.ui.ThemeAdjust;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class ZazaClient implements ClientModInitializer {
    public static final ModuleManager MODULES = new ModuleManager();
    public static final RotationManager ROTATIONS = new RotationManager();

    @Override
    public void onInitializeClient() {
        ZazaRotations.setManager(ROTATIONS);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ConfigStore.load());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigStore.save());
        Keybinds.register();
        ZazaHud.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Theme.update();
            ThemeAdjust.update();
            Keybinds.tick(client);
            MODULES.tick();
            ROTATIONS.tick(client);
        });
    }
}
