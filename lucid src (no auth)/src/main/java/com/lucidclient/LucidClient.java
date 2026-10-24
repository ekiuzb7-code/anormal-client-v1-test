package com.lucidclient;

import com.lucidclient.config.ModuleConfig;
import com.lucidclient.keybind.KeybindManager;
import com.lucidclient.module.Module;
import com.lucidclient.module.ModuleManager;
import com.lucidclient.module.impl.AimAssist;
import com.lucidclient.module.impl.AnchorMacro;
import com.lucidclient.module.impl.ArraylistModule;
import com.lucidclient.module.impl.AccuratePlacement;
import com.lucidclient.module.impl.AutoClicker;
import com.lucidclient.module.impl.AutoJumpReset;
import com.lucidclient.module.impl.AutoLoot;
import com.lucidclient.module.impl.AutoLog;
import com.lucidclient.module.impl.AutoSell;
import com.lucidclient.module.impl.DoubleAnchor;
import com.lucidclient.module.impl.AutoCrystal;
import com.lucidclient.module.impl.AutoTotem;
import com.lucidclient.module.impl.AutoEat;
import com.lucidclient.module.impl.AutoFirework;
import com.lucidclient.module.impl.CrystalOptimizer;
import com.lucidclient.module.impl.EntityESP;
import com.lucidclient.module.impl.AutoTool;
import com.lucidclient.module.impl.AutoMine;
import com.lucidclient.module.impl.Freecam;
import com.lucidclient.module.impl.Freelook;
import com.lucidclient.module.impl.HoleESP;
import com.lucidclient.module.impl.HoverTotem;
import com.lucidclient.module.impl.Hitboxes;
import com.lucidclient.module.impl.InventoryTotem;
import com.lucidclient.module.impl.JumpCircles;
import com.lucidclient.module.impl.ElytraSwap;
import com.lucidclient.module.impl.MaceBomber;
import com.lucidclient.module.impl.KeyPearl;
import com.lucidclient.module.impl.KeyWindCharge;
import com.lucidclient.module.impl.LightFinder;
import com.lucidclient.module.impl.LungeSwapper;
import com.lucidclient.module.impl.OverworldRay;
import com.lucidclient.module.impl.PearlTrajectory;
import com.lucidclient.module.impl.RtpBaseFinder;
import com.lucidclient.module.impl.ShieldBreaker;
import com.lucidclient.module.impl.SpawnerESP;
import com.lucidclient.module.impl.StorageESP;
import com.lucidclient.module.impl.SusChunkESP;
import com.lucidclient.module.impl.SusChunkFinder;
import com.lucidclient.module.impl.Sprint;
import com.lucidclient.module.impl.AutoWalk;
import com.lucidclient.module.impl.FakeItem;
import com.lucidclient.module.impl.FakePay;
import com.lucidclient.module.impl.FakeStats;
import com.lucidclient.module.impl.NameProtect;
import com.lucidclient.module.impl.WorthDisplay;
import com.lucidclient.module.impl.SkinProtect;
import com.lucidclient.module.impl.Tracers;
import com.lucidclient.module.impl.TriggerBot;
import com.lucidclient.ui.component.Icons;
import com.lucidclient.ui.screen.ClickGUIScreen;
import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.theme.LucidTheme;
import com.lucidclient.ui.util.RenderUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lucid Client — a premium Minecraft client for Java Edition 1.21.11 Fabric.
 *
 * Entry point: registers keybinds, the module manager, and the HUD / world
 * render callbacks that drive per-frame module hooks
 * (AimAssist smoothing, ESP projection, tracers, etc.).
 *
 *   Right Shift → ClickGUI
 */
public class LucidClient implements ClientModInitializer {
    public static final String MOD_ID = "lucidclient";
    public static final String MC_VERSION = "1.21.11";
    public static final String CLIENT_VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("Lucid Client");

    private static LucidClient instance;

    private ModuleManager moduleManager;
    private KeybindManager keybindManager;

    private long lastRenderMs;

    /** Persisted-config staging: enabled states applied on first world join. */
    private boolean configApplied = false;
    private int configSaveCounter = 0;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Initializing Lucid Client v{} for Minecraft {} (Fabric)...", CLIENT_VERSION, MC_VERSION);

        this.moduleManager = new ModuleManager();
        this.keybindManager = new KeybindManager();

        // ── Module registration ───────────────────────────────────
        // Modules are registered unconditionally; this build has no
        // activation gate.
        registerModules();

        // Register the keybinds + tick/HUD/world callbacks.
        KeyBindingHelper.registerKeyBinding(keybindManager.clickGuiKey);

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudRenderCallback.EVENT.register(this::onHudRender);
        WorldRenderEvents.END_MAIN.register(this::onWorldRender);

        // ── Fake Pay: intercept /pay before it reaches the server ──
        // Registered once at init (not per-module-enable) — the handler
        // is a static no-op pass-through while FakePay.isActive() is false,
        // so it costs nothing on servers where the module is disabled.
        // Returning false cancels the command; FakePay.onSendCommand handles
        // DonutSMP detection + fake-message injection internally.
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND
            .register(com.lucidclient.module.impl.FakePay::onSendCommand);

        // ── Fake Stats: intercept /balance (/bal, /money) before it reaches
        // the server — show the fake money instead of the real balance. Same
        // static-gated no-op pattern as FakePay; registered once at init.
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND
            .register(com.lucidclient.module.impl.FakeStats::onSendCommand);

        // ── Fake Item: intercept the ";spawn <item>" / ";clear" chat trigger ──
        // These are chat-prefixed (not slash commands) so they ride
        // ClientSendMessageEvents.ALLOW_CHAT (same fabric-message-api-v1
        // package). The handler is a static no-op pass-through while
        // FakeItem.isActive() is false, so it costs nothing when the
        // module is disabled. Returning false consumes the line so the
        // ";spawn"/";clear" instruction never reaches the server.
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT
            .register(com.lucidclient.module.impl.FakeItem::onSendChat);

        lastRenderMs = System.currentTimeMillis();

        LOGGER.info("Lucid Client ready. Press Right Shift for the ClickGUI.");
    }

    private void onClientTick(MinecraftClient client) {
        // ── Open the ClickGUI on keybind press (only when in-game) ──
        while (keybindManager.clickGuiKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new ClickGUIScreen(this));
            }
        }

        if (moduleManager == null || moduleManager.isEmpty()) return;

        // ── Persisted module config ─────────────────────────────────
        // Apply staged enabled states the first time the player is in a
        // world (module onEnable hooks need a live client), then autosave
        // the config every ~10 seconds so no more than a moment of changes
        // is ever lost.
        if (!configApplied && client.player != null) {
            configApplied = true;
            ModuleConfig.applyPendingEnabled(moduleManager);
        }
        if (++configSaveCounter >= 200) {
            configSaveCounter = 0;
            ModuleConfig.save(moduleManager);
        }

        // Phase 1: onTickAlways() — fires every tick for ALL modules
        // regardless of enabled state (used for toggle keys, auto-triggers,
        // and similar "always-listening" behaviour).
        for (Module module : moduleManager.getAll()) {
            try {
                module.onTickAlways(client);
            } catch (Throwable t) {
                LOGGER.error("Module {} threw during onTickAlways", module.getName(), t);
            }
        }

        // Phase 2: onTick() — fires every tick ONLY for enabled modules
        // (the main per-tick work — target acquisition, state machines, etc.).
        for (Module module : moduleManager.getAll()) {
            if (!module.isEnabled()) continue;
            try {
                module.onTick(client);
            } catch (Throwable t) {
                LOGGER.error("Module {} threw during onTick", module.getName(), t);
            }
        }
    }

    /** Register all built-in Lucid Client modules. */
    private void registerModules() {
        moduleManager.register(new AimAssist());
        moduleManager.register(new AnchorMacro());
        moduleManager.register(new DoubleAnchor());
        moduleManager.register(new AutoCrystal());
        moduleManager.register(new ShieldBreaker());
        moduleManager.register(new TriggerBot());
        moduleManager.register(new EntityESP());
        moduleManager.register(new AutoTotem());
        moduleManager.register(new AutoEat());
        moduleManager.register(new AutoFirework());
        moduleManager.register(new CrystalOptimizer());
        moduleManager.register(new InventoryTotem());
        moduleManager.register(new HoverTotem());
        moduleManager.register(new KeyPearl());
        moduleManager.register(new KeyWindCharge());
        moduleManager.register(new ElytraSwap());
        moduleManager.register(new MaceBomber());
        moduleManager.register(new PearlTrajectory());
        moduleManager.register(new AutoTool());
        moduleManager.register(new AutoMine());
        moduleManager.register(new AutoClicker());
        moduleManager.register(new AutoJumpReset());
        moduleManager.register(new AutoSell());
        moduleManager.register(new AutoLoot());
        moduleManager.register(new AutoLog());
        moduleManager.register(new ArraylistModule());
        moduleManager.register(new Hitboxes());
        moduleManager.register(new Tracers());
        moduleManager.register(new StorageESP());
        moduleManager.register(new SusChunkESP());
        moduleManager.register(new SusChunkFinder());
        moduleManager.register(new SpawnerESP());
        moduleManager.register(new HoleESP());
        moduleManager.register(new OverworldRay());
        moduleManager.register(new Freecam());
        moduleManager.register(new Freelook());
        moduleManager.register(new Sprint());
        moduleManager.register(new AccuratePlacement());
        moduleManager.register(new LungeSwapper());
        moduleManager.register(new RtpBaseFinder());
        moduleManager.register(new JumpCircles());
        moduleManager.register(new SkinProtect());
        moduleManager.register(new AutoWalk());
        moduleManager.register(new LightFinder());
        moduleManager.register(new FakePay());
        moduleManager.register(new FakeItem());
        moduleManager.register(new NameProtect());
        moduleManager.register(new FakeStats());
        moduleManager.register(new WorthDisplay());
        LOGGER.info("Registered {} module(s).", moduleManager.totalCount());

        // ── Persisted module config ─────────────────────────────────
        // Restore saved settings/keybinds now; enabled flags are staged and
        // applied on the first world join (module onEnable needs a live
        // client). Autosaves every 10s from the tick + on client shutdown.
        ModuleConfig.registerShutdownSave(this);
        ModuleConfig.load(moduleManager);
    }

    private void onHudRender(DrawContext ctx, RenderTickCounter counter) {
        long now = System.currentTimeMillis();
        long dt = Math.min(50L, now - lastRenderMs);
        lastRenderMs = now;

        if (moduleManager == null || moduleManager.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        // tickDelta for entity interpolation (LerpedPos, etc.)
        float tickDelta = 1.0f;
        try {
            tickDelta = counter.getTickProgress(false);
        } catch (Throwable ignored) {}

        // Phase 1: onRender() for every ENABLED module — smooth, frame-rate-driven
        // updates (e.g. AimAssist rotation smoothing, Freecam camera maths).
        for (Module module : moduleManager.getAll()) {
            if (!module.isEnabled()) continue;
            try {
                module.onRender(client, tickDelta);
            } catch (Throwable t) {
                LOGGER.error("Module {} threw during onRender", module.getName(), t);
            }
        }

        // Phase 2: draw() — 2D screen-space overlays drawn AFTER module.onRender
        // so ESP boxes/tracers sit on top of any in-flight rotation smoothing.
        for (Module module : moduleManager.getAll()) {
            if (!module.isEnabled()) continue;
            try {
                if (module instanceof EntityESP) {
                    ((EntityESP) module).draw(ctx, tickDelta);
                } else if (module instanceof ArraylistModule) {
                    ((ArraylistModule) module).draw(ctx, tickDelta);
                } else if (module instanceof SusChunkFinder) {
                    ((SusChunkFinder) module).draw(ctx, tickDelta);
                } else if (module instanceof SpawnerESP) {
                    ((SpawnerESP) module).draw(ctx, tickDelta);
                } else if (module instanceof Tracers) {
                    ((Tracers) module).draw(ctx, tickDelta);
                }
            } catch (Throwable t) {
                LOGGER.error("Module {} threw during draw()", module.getName(), t);
            }
        }
    }

    /**
     * World render pass (AFTER_TRANSLUCENT). Dispatches to modules that draw
     * depth-tested 3D geometry (SusChunkESP chunk slabs, SpawnerESP boxes).
     * Runs inside the world render pass so depth/state is correct.
     */
    private void onWorldRender(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext wrc) {
        if (moduleManager == null || moduleManager.isEmpty()) return;
        for (Module module : moduleManager.getAll()) {
            if (!module.isEnabled()) continue;
            try {
                if (module instanceof SusChunkESP) {
                    ((SusChunkESP) module).renderWorld(wrc);
                } else if (module instanceof SpawnerESP) {
                    ((SpawnerESP) module).renderWorld(wrc);
                } else if (module instanceof StorageESP) {
                    ((StorageESP) module).renderWorld(wrc);
                } else if (module instanceof HoleESP) {
                    ((HoleESP) module).renderWorld(wrc);
                } else if (module instanceof OverworldRay) {
                    ((OverworldRay) module).renderWorld(wrc);
                } else if (module instanceof PearlTrajectory) {
                    ((PearlTrajectory) module).renderWorld(wrc);
                } else if (module instanceof JumpCircles) {
                    ((JumpCircles) module).renderWorld(wrc);
                } else if (module instanceof LightFinder) {
                    ((LightFinder) module).renderWorld(wrc);
                }
            } catch (Throwable t) {
                LOGGER.error("Module {} threw during renderWorld()", module.getName(), t);
            }
        }
    }

    public static LucidClient getInstance() { return instance; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public KeybindManager getKeybindManager() { return keybindManager; }
}
