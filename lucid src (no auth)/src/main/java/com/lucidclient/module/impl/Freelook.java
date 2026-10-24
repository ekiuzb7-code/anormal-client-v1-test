package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Freelook — glazed-style "look around without turning the way you walk".
 *
 * The camera is DECOUPLED from the body: while active, mouse motion orbits a
 * module-held (cameraYaw, cameraPitch) pair while the player entity keeps the
 * exact rotation it had at activation. The body never turns, so the movement
 * keys keep steering the original direction, and — because the body's yaw and
 * pitch are simply never mutated — the move packets the server receives keep
 * reporting the frozen rotation. No rotation-packet suppression is needed at
 * all; the body freezes itself because the only mouse-driven writer of the
 * player's rotation is intercepted upstream.
 *
 * ── Mechanism (ported from glazed's GlazedFreelook) ───────────────
 *   1. INPUT FREEZE — {@code MouseMixin} redirects the single
 *      {@code player.changeLookDirection(dx, dy)} call inside
 *      {@code Mouse.updateMouse}: while Freelook is active the raw cursor
 *      delta is handed to {@link #applyMouseDelta} instead, which accumulates
 *      it into the camera angles (glazed does the equivalent by cancelling
 *      {@code Entity.turn} at HEAD and calling
 *      {@code updateRotation(dx * 0.15, dy * 0.15)} — same freeze, lucid just
 *      intercepts at the call site so Freecam shares the hook).
 *      {@link #onTick} re-asserts the frozen body yaw as a safety net against
 *      the few non-mouse yaw writers (vehicle look, knockback).
 *   2. ROTATION-ONLY CAMERA OVERRIDE — {@code CameraMixin} uses
 *      {@code @ModifyArgs} on the {@code setRotation(FF)} invocation(s) inside
 *      {@code Camera.update} that carry the focused entity's
 *      {@code getYaw(tickDelta)} / {@code getPitch(tickDelta)}, replacing them
 *      with the camera angles. This happens BEFORE vanilla positions the
 *      third-person camera, so VANILLA does the orbit: it pulls the camera
 *      back ~4 blocks along the overridden rotation and clips it against
 *      walls via {@code clipToSpace} (the 1.21.11 rename of getMaxZoom).
 *      There is no module-side position math, no raycast, no smoothing —
 *      glazed deliberately lets vanilla's third-person code do the orbit,
 *      which makes the camera behave exactly like vanilla F5 in feel and
 *      collision. The overridden branch set reads the live camera angles
 *      every frame, so no prev-yaw/prev-pitch interpolation state is needed
 *      (glazed tracks previousYaw/previousPitch but its getters return the
 *      raw values for the same reason).
 *   3. WALL NO-CLIP — glazed's only setting, see-through-walls, maps to a
 *      HEAD-inject on {@code clipToSpace}: when enabled, the clip is skipped
 *      by returning the desired distance directly, letting the camera pass
 *      through blocks instead of pulling in against them.
 *   4. PERSPECTIVE — on activation the current perspective is saved and
 *      THIRD_PERSON_BACK is forced (the orbit only reads as a freelook camera
 *      from third person); on deactivation — or when the player leaves the
 *      world, glazed's GameLeft equivalent, watched from
 *      {@link #onTickAlways} — the saved perspective is restored, falling
 *      back to FIRST_PERSON if none was captured (glazed's restoreView).
 *
 * ── Rotation math (glazed's updateRotation semantics) ─────────────
 *   yaw   += deltaYaw   * 0.15, then wrapped to [-180, 180)
 *   pitch += deltaPitch * 0.15, then clamped to [-90, 90]
 * Lucid extras layered on top: a sensitivity multiplier, optional pitch
 * inversion, and an optional yaw leash that limits how far the camera may
 * orbit from the frozen body yaw (default 180° = glazed's unlimited orbit).
 *
 * ── Kept lucid-only improvements ──────────────────────────────────
 *   Perspective handling is OPTIONAL (the "Third Person" toggle — with it
 *   off, the first-person view rotates instead, still without turning the
 *   body); a rebindable Toggle Key with Toggle/Hold activation modes polled
 *   in {@link #onTickAlways}; Sensitivity / Invert Pitch / Angle Limit
 *   refinement settings. The scroll-zoom orbit
 *   distance, position smoothing and module-side collision raycast of the
 *   old lucid implementation were dropped: under glazed's mechanism the
 *   camera position belongs to vanilla, which owns pullback distance and
 *   wall clipping.
 *
 * Settings:
 *   Toggle Key, Activation Mode, Sensitivity, Invert Pitch, Angle Limit,
 *   See Through Walls (glazed's see-through-walls, default off), Third
 *   Person
 */
public class Freelook extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to start/stop freelook. Click here to rebind.",
            GLFW.GLFW_KEY_LEFT_ALT));
    private final EnumSetting activationMode = addSetting(
        new EnumSetting("Activation Mode", "Toggle = press once to start/stop. Hold = active only while the key is held.",
            List.of("Toggle", "Hold"), "Toggle"));
    private final FloatSetting sensitivity = addSetting(
        new FloatSetting("Sensitivity", "Multiplier on the mouse delta applied to the camera angles. 1.0 = vanilla feel.",
            0.1f, 4f, 1.0f, 1));
    private final BooleanSetting invertPitch = addSetting(
        new BooleanSetting("Invert Pitch", "Invert vertical mouse (push forward → camera looks up).", false));
    private final FloatSetting angleLimit = addSetting(
        new FloatSetting("Angle Limit", "Max degrees the camera yaw can deviate from the body yaw. 180 = unlimited (glazed default).",
            0f, 180f, 180f, 1));
    private final BooleanSetting seeThroughWalls = addSetting(
        new BooleanSetting("See Through Walls", "Let the camera pass through blocks instead of pulling in against them (glazed's see-through-walls).",
            false));
    private final BooleanSetting thirdPerson = addSetting(
        new BooleanSetting("Third Person", "Force third-person back view while freelook is active.", true));

    // ── Static state (read by MouseMixin + CameraMixin) ─────────
    // Volatile because the mixins run on the render/input thread while the
    // module's onTick/onTickAlways run on the client tick thread.
    private static volatile boolean active = false;
    private static volatile float cameraYaw = 0f;   // freelook camera yaw
    private static volatile float cameraPitch = 0f; // freelook camera pitch
    private static volatile float bodyYaw = 0f;     // frozen body yaw (angle-limit reference)

    // Tick-thread state.
    private Perspective savedPerspective;     // restored on stop if we forced 3rd person
    private boolean forcedPerspective = false; // did we change options.perspective?
    private boolean wasKeyDown = false;

    public Freelook() {
        super("Freelook", "Look around freely without turning the way you walk.",
            Category.MOVEMENT);
    }

    // ════════════════════════════════════════════════════════════════
    //  Lifecycle — active is tied to enabled.
    // ════════════════════════════════════════════════════════════════
    @Override
    protected void onEnable() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            // glazed's onActivate guard: no player -> bounce straight back off.
            setEnabled(false);
            return;
        }
        start(player);
        wasKeyDown = false;
    }

    @Override
    protected void onDisable() {
        stop();
        wasKeyDown = false;
    }

    /**
     * Capture the player's current look (BOTH yaw and pitch — glazed's
     * onActivate) as the starting camera angles and freeze the body yaw,
     * then force third-person back so the freelook reads as an orbit.
     */
    private void start(ClientPlayerEntity player) {
        bodyYaw = player.getYaw();
        cameraYaw = player.getYaw();
        cameraPitch = player.getPitch();
        active = true;
    }

    /**
     * Release — restore the saved perspective (falling back to first person
     * if none was captured, glazed's restoreView) and let vanilla re-attach
     * the camera to the body rotation.
     */
    private void stop() {
        active = false;

        if (forcedPerspective) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options != null) {
                mc.options.setPerspective(
                    savedPerspective != null ? savedPerspective : Perspective.FIRST_PERSON);
            }
            forcedPerspective = false;
        }
        savedPerspective = null;
    }

    // ════════════════════════════════════════════════════════════════
    //  Keybind polling + disconnect guard — onTickAlways so a disabled
    //  freelook can be re-armed by its key, and so a world exit while
    //  active restores the perspective (glazed's GameLeft handler).
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTickAlways(MinecraftClient client) {
        // GameLeft equivalent: leaving the world while enabled must restore
        // the perspective and spin the module down cleanly.
        if (isEnabled() && client.player == null) {
            setEnabled(false);
            return;
        }

        if (client.player == null || client.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = client.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        boolean holdMode = "Hold".equals(activationMode.get());

        if (holdMode) {
            if (isDown != isEnabled()) {
                setEnabled(isDown);
            }
        } else {
            if (isDown && !wasKeyDown) {
                setEnabled(!isEnabled());
            }
        }
        wasKeyDown = isDown;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — body-yaw safety net (20Hz, tick-synchronised)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (!active || client.player == null) return;

        // The mouse redirect prevents the mouse from turning the body, but
        // other vanilla code (vehicle look, knockback) might. Re-assert each
        // tick so the body keeps facing its frozen direction. Pitch is left
        // alone (body pitch doesn't affect movement).
        client.player.setYaw(bodyYaw);
        client.player.lastYaw = bodyYaw;
    }

    // ════════════════════════════════════════════════════════════════
    //  Mouse delta → camera angles  (called by MouseMixin on the input
    //  thread; glazed's updateRotation with lucid's extras)
    // ════════════════════════════════════════════════════════════════
    /**
     * Apply a raw mouse delta to the freelook camera angles. Called by
     * {@code MouseMixin} in place of {@code player.changeLookDirection}
     * while Freelook is active — the body never turns, which is exactly why
     * no rotation-packet suppression is required.
     *
     * Arg order is {@code (yawDeltaRaw, pitchDeltaRaw)} — YAW first, PITCH
     * second — matching {@code Entity.changeLookDirection}'s bytecode (the
     * first double feeds yaw, the second pitch) and the
     * {@code (cursorDeltaX, cursorDeltaY)} values {@code Mouse.updateMouse}
     * passes into it. Vanilla multiplies each raw delta by 0.15 inside
     * {@code changeLookDirection} (glazed's EntityMixin does the same before
     * calling updateRotation); we apply the same 0.15 scale here, plus the
     * user's sensitivity multiplier.
     */
    public static void applyMouseDelta(double yawDeltaRaw, double pitchDeltaRaw) {
        if (!active) return;

        Freelook inst = getInstance();
        float sens = inst != null ? inst.sensitivity.get() : 1.0f;
        float limit = inst != null ? inst.angleLimit.get() : 180f;
        boolean invert = inst != null && inst.invertPitch.isEnabled();

        // Vanilla scale: 0.15 per unit of raw delta, times sensitivity.
        float yawDelta = (float) (yawDeltaRaw * 0.15 * sens);
        float pitchDelta = (float) (pitchDeltaRaw * 0.15 * sens);
        if (invert) pitchDelta = -pitchDelta;

        // glazed's updateRotation semantics: wrap the yaw into [-180, 180)
        // and clamp the pitch to the vanilla poles.
        float newYaw = MathHelper.wrapDegrees(cameraYaw + yawDelta);
        float newPitch = MathHelper.clamp(cameraPitch + pitchDelta, -90f, 90f);

        // Angle-limit clamp: how far has the camera yaw drifted from the
        // frozen body? (Lucid extra; 180 = glazed's unlimited orbit.)
        if (limit < 180f) {
            float delta = MathHelper.wrapDegrees(newYaw - bodyYaw);
            if (delta > limit)  delta = limit;
            if (delta < -limit) delta = -limit;
            newYaw = bodyYaw + delta;
        }

        cameraYaw = newYaw;
        cameraPitch = newPitch;
    }

    /** Look up the registered Freelook module instance (for reading settings). */
    private static Freelook getInstance() {
        LucidClient instance = LucidClient.getInstance();
        if (instance == null || instance.getModuleManager() == null) return null;
        return instance.getModuleManager().getModule(Freelook.class);
    }

    // ── Static accessors for the mixins ──────────────────────────
    public static boolean isActive() { return active; }
    public static float getCameraYaw() { return cameraYaw; }
    public static float getCameraPitch() { return cameraPitch; }

    /**
     * glazed's allowCameraNoClip — read by {@code CameraMixin}'s
     * {@code clipToSpace} HEAD-inject to decide whether the vanilla
     * wall-clip should be skipped (camera passes through blocks).
     */
    public static boolean allowCameraNoClip() {
        Freelook inst = getInstance();
        return active && inst != null && inst.seeThroughWalls.isEnabled();
    }
}
