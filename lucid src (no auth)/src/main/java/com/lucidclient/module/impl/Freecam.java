package com.lucidclient.module.impl;

import com.lucidclient.freecam.FlightMode;
import com.lucidclient.freecam.FreeCamera;
import com.lucidclient.freecam.FreecamPosition;
import com.lucidclient.freecam.InitialPerspective;
import com.lucidclient.freecam.TripodSlot;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/**
 * Freecam — the full Freecam-mod implementation (Freecam-main / Argon port)
 * behind Lucid's module + ClickGUI system.
 *
 * While enabled, a clientside {@link FreeCamera} entity is spawned into the
 * world and made the camera entity, so the camera detaches completely from
 * the player: WASD + space/shift fly the camera (DEFAULT static-velocity or
 * CREATIVE flight), the mouse orbits the camera, and the body stays behind.
 * Disabling restores you to your original position.
 *
 * Feature set (all from the Freecam mod):
 *   Flight Mode / Horizontal + Vertical Speed, collision ignores (transparent /
 *   openable / all), Initial Perspective, Show Player (+ outline), Show Hand,
 *   Full Bright, submersion fog, Disable on Damage, Freeze Player, Allow
 *   Interaction (+ CAMERA/PLAYER ray mode), tripod cameras (freecam key +
 *   hotbar 1-9), and a Control Player key that hands input back to the body.
 */
public class Freecam extends Module {
    private static Freecam INSTANCE;

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to toggle Freecam on/off. Click here to rebind.",
            GLFW.GLFW_KEY_F4));

    private final EnumSetting flightMode = addSetting(
        new EnumSetting("Flight Mode", "DEFAULT is static speed. CREATIVE matches vanilla fly.",
            new String[]{"Default", "Creative"}, "Default"));
    private final FloatSetting horizontalSpeed = addSetting(
        new FloatSetting("Horizontal Speed", "Horizontal camera speed.", 0.1f, 10f, 1.0f, 1));
    private final FloatSetting verticalSpeed = addSetting(
        new FloatSetting("Vertical Speed", "Vertical camera speed.", 0.1f, 10f, 1.0f, 1));

    private final BooleanSetting ignoreTransparent = addSetting(
        new BooleanSetting("Ignore Transparent", "Fly through glass, bars and barriers.", true));
    private final BooleanSetting ignoreOpenable = addSetting(
        new BooleanSetting("Ignore Openable", "Fly through doors, trapdoors and gates.", true));
    private final BooleanSetting ignoreAll = addSetting(
        new BooleanSetting("Ignore All Collision", "Fly through every block.", true));
    private final BooleanSetting alwaysCheck = addSetting(
        new BooleanSetting("Check Initial Collision", "Still clip the starting camera if Ignore All is on.", false));

    private final EnumSetting perspective = addSetting(
        new EnumSetting("Initial Perspective", "Where the camera starts relative to you.",
            new String[]{"Inside", "First Person", "Third Person", "Third Person Mirror"}, "Inside"));
    private final BooleanSetting showPlayer = addSetting(
        new BooleanSetting("Show Player", "Render your body at the frozen position.", true));
    private final BooleanSetting showHand = addSetting(
        new BooleanSetting("Show Hand", "Show the first-person hand while flying.", false));
    private final BooleanSetting fullBright = addSetting(
        new BooleanSetting("Full Bright", "Max brightness while Freecam is on.", false));
    private final BooleanSetting showSubmersion = addSetting(
        new BooleanSetting("Show Submersion Fog", "Water / lava / powder snow overlay.", false));
    private final BooleanSetting outlinePlayer = addSetting(
        new BooleanSetting("Outline Player", "Glow outline on your frozen body.", false));

    private final BooleanSetting disableOnDamage = addSetting(
        new BooleanSetting("Disable on Damage", "Turn Freecam off when you take damage.", true));
    private final BooleanSetting freezePlayer = addSetting(
        new BooleanSetting("Freeze Player", "Hard-lock your body in place. Multiplayer not advised.", false));
    private final BooleanSetting allowInteract = addSetting(
        new BooleanSetting("Allow Interaction", "Attack and use from Freecam. Multiplayer not advised.", false));
    private final EnumSetting interactionMode = addSetting(
        new EnumSetting("Interaction Mode", "CAMERA uses the camera ray, PLAYER uses your body.",
            new String[]{"Camera", "Player"}, "Camera"));

    private final KeybindSetting playerControlKey = addSetting(
        new KeybindSetting("Control Player", "While Freecam is on, move your body instead of the camera.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final KeybindSetting resetTripodKey = addSetting(
        new KeybindSetting("Reset Tripod", "Hold with 1-9 to clear that tripod camera.",
            GLFW.GLFW_KEY_UNKNOWN));

    private boolean tripodEnabled;
    private boolean playerControlEnabled;
    private boolean disableNextTick;
    private final Map<TripodSlot, FreecamPosition> tripods = new EnumMap<>(TripodSlot.class);
    private TripodSlot activeTripod = TripodSlot.NONE;
    private FreeCamera freeCamera;
    private Perspective rememberedPerspective;
    private boolean playerControlWasDown;
    private boolean outlineEnabled;

    // Toggle-key edge state (works from onTickAlways even when disabled)
    private boolean toggleWasDown = false;
    private boolean settingToggleWasDown = false;

    public Freecam() {
        super("Freecam",
            "Detach the camera and fly around while your body stays in place.",
            Category.MOVEMENT);
        INSTANCE = this;
    }

    // ── Static accessors used by the freecam mixins ──────────────

    public static Freecam instance() {
        return INSTANCE;
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }

    public static boolean isPlayerControlEnabled() {
        return INSTANCE != null && INSTANCE.playerControlEnabled;
    }

    public static FreeCamera getFreeCamera() {
        return INSTANCE == null ? null : INSTANCE.freeCamera;
    }

    public static boolean ignoreAllCollision() {
        return INSTANCE != null && INSTANCE.ignoreAll.isEnabled();
    }

    public static boolean shouldCheckInitialCollision() {
        return INSTANCE != null && (INSTANCE.alwaysCheck.isEnabled() || !INSTANCE.ignoreAll.isEnabled());
    }

    public static boolean ignoreCollisionWith(Block block) {
        if (INSTANCE == null) return false;
        if (INSTANCE.ignoreAll.isEnabled()) return true;
        if (INSTANCE.ignoreTransparent.isEnabled()
                && (block instanceof TransparentBlock || block instanceof PaneBlock || block instanceof BarrierBlock)) {
            return true;
        }
        return INSTANCE.ignoreOpenable.isEnabled()
                && (block instanceof FenceGateBlock || block instanceof DoorBlock || block instanceof TrapdoorBlock);
    }

    public static boolean shouldShowPlayer() {
        return INSTANCE != null && INSTANCE.showPlayer.isEnabled();
    }

    public static boolean shouldHideHand() {
        return INSTANCE != null && !INSTANCE.showHand.isEnabled();
    }

    public static boolean isFullBright() {
        return INSTANCE != null && INSTANCE.fullBright.isEnabled();
    }

    public static boolean shouldHideSubmersionFog() {
        return INSTANCE != null && !INSTANCE.showSubmersion.isEnabled();
    }

    public static boolean shouldDisableOnDamage() {
        return INSTANCE != null && INSTANCE.disableOnDamage.isEnabled();
    }

    public static boolean shouldFreezePlayer() {
        return INSTANCE != null && INSTANCE.freezePlayer.isEnabled();
    }

    public static boolean shouldPreventInteractions() {
        return INSTANCE != null && !INSTANCE.allowInteract.isEnabled();
    }

    public static boolean allowInteractionsFromPlayer() {
        return INSTANCE != null && "Player".equals(INSTANCE.interactionMode.get());
    }

    public static boolean isOutlineEnabled() {
        return INSTANCE != null && INSTANCE.outlineEnabled;
    }

    public static void disableNextTick() {
        if (INSTANCE != null) INSTANCE.disableNextTick = true;
    }

    /** True while the freecam toggle key (module bind OR Toggle Key setting) or reset-tripod key is held (blocks hotbar). */
    public static boolean shouldBlockHotbar() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        int toggle = INSTANCE.getKeybind();
        int toggleSetting = INSTANCE.toggleKey.getKey();
        int reset = INSTANCE.resetTripodKey.getKey();
        return (toggle >= 0 && isKeyPressed(toggle))
                || (toggleSetting >= 0 && isKeyPressed(toggleSetting))
                || (reset >= 0 && isKeyPressed(reset));
    }

    /** Freecam key + a hotbar number opens/returns to that tripod camera. */
    public static void onHotbarSlot(int index) {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return;
        TripodSlot slot = TripodSlot.ofHotbarIndex(index);
        if (slot == TripodSlot.NONE) return;
        int reset = INSTANCE.resetTripodKey.getKey();
        if (reset >= 0 && isKeyPressed(reset)) {
            INSTANCE.resetTripod(slot);
        } else {
            INSTANCE.toggleTripod(slot);
        }
    }

    // ── Module lifecycle ─────────────────────────────────────────

    @Override
    protected void onEnable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            setEnabled(false);
            return;
        }
        onEnableFreecam();
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (tripodEnabled) {
            onDisableTripod(false);
            tripodEnabled = false;
        } else if (freeCamera != null) {
            onDisableFreecam(false);
        }
        restorePerspective();
        playerControlEnabled = false;
        playerControlWasDown = false;
        toggleWasDown = false;
        settingToggleWasDown = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;

        if (disableNextTick) {
            disableNextTick = false;
            setEnabled(false);
            return;
        }

        // Block player input while freecam is active and the player isn't
        // controlling their body (sneak state preserved).
        if (mc.player.input instanceof KeyboardInput && !playerControlEnabled) {
            PlayerInput previous = mc.player.input.playerInput;
            Input blank = new Input();
            blank.playerInput = new PlayerInput(false, false, false, false, false, previous.sneak(), false);
            mc.player.input = blank;
        }

        // Handle switching between controlling the camera and the player.
        int controlKey = playerControlKey.getKey();
        if (controlKey >= 0) {
            boolean down = isKeyPressed(controlKey);
            if (down && !playerControlWasDown) {
                switchControls();
            }
            playerControlWasDown = down;
        }
    }

    /**
     * onTickAlways — toggle key handling (also works while the module is
     * disabled, so it can re-enable freecam). Both the module keybind
     * (middle-click on the module) and the Toggle Key setting work.
     */
    @Override
    public void onTickAlways(MinecraftClient client) {
        if (client.player == null && !isEnabled()) {
            toggleWasDown = false;
            settingToggleWasDown = false;
            return;
        }

        // Module keybind (set via middle-click in the ClickGUI).
        int key = getKeybind();
        if (key >= 0) {
            boolean down = isKeyPressed(key);
            if (down && !toggleWasDown) {
                toggle();
            }
            toggleWasDown = down;
        } else {
            toggleWasDown = false;
        }

        // Toggle Key setting from the module's settings card.
        int settingKey = toggleKey.getKey();
        if (settingKey >= 0) {
            boolean down = isKeyPressed(settingKey);
            if (down && !settingToggleWasDown) {
                toggle();
            }
            settingToggleWasDown = down;
        } else {
            settingToggleWasDown = false;
        }
    }

    /** Toggle via the module keybind (or ClickGUI). */
    public void toggle() {
        setEnabled(!isEnabled());
    }

    public void onDisconnect() {
        if (isEnabled()) {
            setEnabled(false);
        }
        tripods.clear();
    }

    public void onRespawn() {
        if (isEnabled()) {
            setEnabled(false);
        }
    }

    // ── Settings → typed enum accessors (EnumSetting stores strings) ──

    public FlightMode flightMode() {
        return "Creative".equals(flightMode.get()) ? FlightMode.CREATIVE : FlightMode.DEFAULT;
    }

    public double horizontalSpeed() {
        return horizontalSpeed.get();
    }

    public double verticalSpeed() {
        return verticalSpeed.get();
    }

    public InitialPerspective initialPerspective() {
        switch (perspective.get()) {
            case "First Person": return InitialPerspective.FIRST_PERSON;
            case "Third Person": return InitialPerspective.THIRD_PERSON;
            case "Third Person Mirror": return InitialPerspective.THIRD_PERSON_MIRROR;
            default: return InitialPerspective.INSIDE;
        }
    }

    // ── Enable / disable internals (Freecam-mod flow) ────────────

    private void onEnableFreecam() {
        beginSession();
        freeCamera = new FreeCamera(-420);
        moveToPlayer();
        freeCamera.spawn();
        mc().setCameraEntity(freeCamera);
        overlay("Freecam enabled");
    }

    private void onDisableFreecam(boolean notify) {
        endSession();
        if (notify && mc().player != null) {
            overlay("Freecam disabled");
        }
    }

    private void toggleTripod(TripodSlot tripod) {
        if (tripodEnabled) {
            if (activeTripod == tripod) {
                onDisableTripod(true);
                tripodEnabled = false;
                restorePerspective();
                onEnableFreecam();
            } else {
                onDisableTripod(false);
                onEnableTripod(tripod);
            }
        } else {
            onDisableFreecam(false);
            onEnableTripod(tripod);
            tripodEnabled = true;
        }
    }

    private void onEnableTripod(TripodSlot tripod) {
        beginSession();
        FreecamPosition position = tripods.get(tripod);
        boolean chunkLoaded = false;
        if (position != null && mc().world != null) {
            ChunkPos chunkPos = position.getChunkPos();
            chunkLoaded = mc().world.isChunkLoaded(chunkPos.x, chunkPos.z);
        }
        if (!chunkLoaded) {
            tripods.put(tripod, null);
            position = null;
        }
        freeCamera = new FreeCamera(-420 - tripod.ordinal());
        if (position == null) {
            moveToPlayer();
        } else {
            freeCamera.applyPosition(position);
        }
        freeCamera.spawn();
        mc().setCameraEntity(freeCamera);
        activeTripod = tripod;
        overlay("Tripod " + tripod + " open");
    }

    private void onDisableTripod(boolean notify) {
        if (freeCamera != null) {
            tripods.put(activeTripod, new FreecamPosition(freeCamera));
        }
        endSession();
        if (notify && mc().player != null) {
            overlay("Tripod " + activeTripod + " saved");
        }
        activeTripod = TripodSlot.NONE;
    }

    private void resetTripod(TripodSlot tripod) {
        if (tripodEnabled && activeTripod == tripod && freeCamera != null) {
            moveToPlayer();
        } else {
            tripods.put(tripod, null);
        }
        overlay("Tripod " + tripod + " reset");
    }

    private void beginSession() {
        mc().chunkCullingEnabled = false;
        outlineEnabled = outlinePlayer.isEnabled();
        rememberedPerspective = mc().options.getPerspective();
        if (mc().gameRenderer.getCamera().isThirdPerson()) {
            mc().options.setPerspective(Perspective.FIRST_PERSON);
        }
    }

    private void endSession() {
        mc().chunkCullingEnabled = true;
        mc().setCameraEntity(mc().player);
        playerControlEnabled = false;
        if (freeCamera != null) {
            freeCamera.despawn();
            freeCamera.input = new Input();
            freeCamera = null;
        }
        if (mc().player != null) {
            mc().player.input = new KeyboardInput(mc().options);
        }
    }

    private void restorePerspective() {
        if (rememberedPerspective != null) {
            mc().options.setPerspective(rememberedPerspective);
            rememberedPerspective = null;
        }
    }

    private void switchControls() {
        if (freeCamera == null || mc().player == null) return;
        if (playerControlEnabled) {
            freeCamera.input = new KeyboardInput(mc().options);
        } else {
            mc().player.input = new KeyboardInput(mc().options);
            freeCamera.input = new Input();
        }
        playerControlEnabled = !playerControlEnabled;
        overlay(playerControlEnabled ? "Controlling player" : "Controlling camera");
    }

    private void moveToPlayer() {
        if (freeCamera == null || mc().player == null) return;
        freeCamera.copyPositionAndRotation(mc().player);
        freeCamera.applyPerspective(initialPerspective(), alwaysCheck.isEnabled() || !ignoreAll.isEnabled());
    }

    // ── Small helpers ────────────────────────────────────────────

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private static boolean isKeyPressed(int keyCode) {
        long handle = mc().getWindow().getHandle();
        if (keyCode <= 8) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, keyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        return org.lwjgl.glfw.GLFW.glfwGetKey(handle, keyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /** Send a message over the action bar. */
    private void overlay(String message) {
        if (mc().player != null) {
            mc().player.sendMessage(Text.literal(message), true);
        }
    }
}
