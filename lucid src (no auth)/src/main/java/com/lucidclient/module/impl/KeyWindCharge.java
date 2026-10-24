package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * KeyWindCharge — wind-charge vertical boost on a hotkey.
 *
 * On the bound key press, the module:
 *   1. Scans the hotbar for a Wind Charge. If none, do nothing (no spam,
 *      no rotation — the whole activation is gated on having one).
 *   2. ROTATE: smoothly pitches the view down toward straight-down (90°)
 *      EVERY RENDER FRAME with real elapsed time (`Look Speed` degrees per
 *      tick × 20 = degrees per second) — frame-rate-independent, butter-
 *      smooth camera motion (same timing pattern as Freecam). The server
 *      still sees a gradual, human-looking rotation because the per-tick
 *      look packets sample the smoothly-moving pitch.
 *   3. SWAP: switches to the wind charge slot and waits `Delay` ticks for
 *      the server to process the slot-change packet (race-fix, same
 *      pattern as KeyPearl — without this wait the use-item packet can
 *      arrive before the slot-change and you'd use whatever you were
 *      holding before).
 *   4. FIRE: right-clicks the wind charge and jumps at the same time —
 *      {@code interactionManager.interactItem} fires the charge at your
 *      feet while a real simulated jump key press + {@code player.jump()}
 *      add the jump impulse on the exact same tick, stacking into the
 *      signature boosted launch. {@code player.swingHand} plays the
 *      throw animation ({@code interactItem} alone never swings — the
 *      same reason ShieldBreaker/TriggerBot swing explicitly).
 *   5. RECOVER: after the use packet flushes, restores the original
 *      hotbar slot, then glides the view back up to its original pitch
 *      (per-frame, same smooth motion — no snap).
 *
 * ── Jump injection ───────────────────────────────────────────────
 * The jump is simulated the same way AutoJumpReset does it: a REAL key
 * press via {@code options.jumpKey.setPressed(true)} (so the vanilla input
 * path registers it), a direct {@code player.jump()} impulse for
 * same-tick accuracy, and a 2-tick hold before releasing (held jump would
 * otherwise auto-bounce). The injected press is always released — on
 * completion, on abort, and on module disable.
 *
 * ── Safety guards ────────────────────────────────────────────────
 *   - Never triggers while a GUI/screen is open.
 *   - Aborts (restores slot + releases jump) if a screen opens, the
 *     player leaves the world, or the swap doesn't sync in time.
 *   - Rotation has a timeout — if the view hasn't reached straight-down
 *     after ~2s (e.g. the mouse fights it), the sequence aborts cleanly.
 *
 * Settings:
 *   Key, Look Speed, Delay, Restore Slot, Restore Look
 */
public class KeyWindCharge extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Key",
            "Press this key to look down and wind-charge jump. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    /** Downward pitch rate in degrees per tick (×20 = deg/sec). 90 = near-instant. */
    private final FloatSetting lookSpeed = addSetting(
        new FloatSetting("Look Speed", "How fast the view rotates, in degrees per tick (90 = instant). Applies to both the look-down and the restore.",
            5f, 90f, 22.5f, 2));
    private final IntSetting delay = addSetting(
        new IntSetting("Delay",
            "Ticks to wait after swapping to the wind charge before firing (server race-fix). "
            + "1 = fastest (50ms). 2+ is safer vs anti-cheat.",
            1, 5, 2, 1));
    private final BooleanSetting restoreSlot = addSetting(
        new BooleanSetting("Restore Slot", "Switch back to your original hotbar slot after firing.", true));
    private final BooleanSetting restoreLook = addSetting(
        new BooleanSetting("Restore Look", "Return the view to its original pitch after firing.", true));

    // ── State machine ────────────────────────────────────────
    private enum State { IDLE, ROTATE, SWAP, FIRE, RECOVER, RESTORE_LOOK }

    private State state = State.IDLE;
    private int ticksInState = 0;

    // Inventory state (captured at trigger).
    private int originalSlot = -1;
    private int windChargeSlot = -1;

    // Look state (captured at trigger).
    private float originalPitch = 0f;
    /** True once the wind charge has been used — controls what onDisable restores. */
    private boolean fired = false;

    // Injected jump-key hold, in ticks (AutoJumpReset pattern).
    private int jumpHeldTicks = 0;

    // Per-frame rotation timing — nanoseconds of the previous onRender call.
    // 0 = "next call is the first frame" (zero delta, so triggering never
    // produces a rotation jump from a stale timestamp).
    private long lastFrameNanos = 0L;

    // Hard cap on the ROTATE phase — if the mouse fights the rotation for
    // ~2s (40 ticks) without reaching straight-down, abort cleanly.
    private static final int MAX_ROTATE_TICKS = 40;

    // Key edge detection.
    private boolean wasKeyDown = false;

    public KeyWindCharge() {
        super("KeyWindCharge", "Look down and wind-charge jump on a hotkey for a vertical boost.",
            Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        resetState();
        releaseJump();
        wasKeyDown = false;
    }

    @Override
    protected void onDisable() {
        // Mid-sequence disable: snap the hotbar back and release the
        // injected jump. The view pitch is only restored if we hadn't
        // fired yet — after the launch the player is mid-air and an
        // instant view snap is disorienting rather than helpful.
        if (!fired && restoreLook.isEnabled()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && originalPitch != 0f) {
                mc.player.setPitch(originalPitch);
            }
        }
        restoreOriginalSlot();
        releaseJump();
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        originalSlot = -1;
        windChargeSlot = -1;
        originalPitch = 0f;
        fired = false;
        lastFrameNanos = 0L;
    }

    /** Always release the injected jump press — never stick the key. */
    private void releaseJump() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            mc.options.jumpKey.setPressed(false);
        }
        jumpHeldTicks = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  Keybind polling — onTickAlways so the key works even while the
    //  module is disabled in ClickGUI (matches KeyPearl / Freecam).
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTickAlways(MinecraftClient client) {
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

        // Fire on the press edge.
        if (isDown && !wasKeyDown) {
            trigger(client);
        }
        wasKeyDown = isDown;
    }

    // ════════════════════════════════════════════════════════════════
    //  TRIGGER — find a wind charge, save state, start the sequence
    // ════════════════════════════════════════════════════════════════
    private void trigger(MinecraftClient client) {
        // Force-reset if a previous sequence somehow stalled.
        if (state != State.IDLE) {
            restoreOriginalSlot();
            resetState();
        }
        if (client.player == null || client.interactionManager == null) return;
        if (client.currentScreen != null) return; // don't fire while in menus

        ClientPlayerEntity player = client.player;
        PlayerInventory inv = player.getInventory();

        // Scan the hotbar for a Wind Charge. No charge → no activation at
        // all (no rotation, no slot swap, no jump).
        windChargeSlot = findWindChargeSlot(inv);
        if (windChargeSlot < 0) {
            resetState();
            return;
        }

        originalSlot = inv.getSelectedSlot();
        originalPitch = player.getPitch();
        lastFrameNanos = 0L;  // first render frame gets a zero delta

        state = State.ROTATE;
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — advance the state machine (20Hz; rotation packets only
    //  transmit at tick rate anyway, so per-tick stepping IS what the
    //  server sees).
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        // ── Release any previously-injected jump key ───────────────
        // Hold the press for a couple ticks so the vanilla input path
        // definitely registers it, then release to avoid the vanilla
        // "held jump = auto-bounce" behaviour (AutoJumpReset pattern).
        if (jumpHeldTicks > 0) {
            jumpHeldTicks--;
            if (jumpHeldTicks == 0 && client.options != null) {
                client.options.jumpKey.setPressed(false);
            }
        }

        if (state == State.IDLE) return;
        if (client.player == null || client.interactionManager == null || client.world == null) {
            abort(null);
            return;
        }
        // A screen opening mid-sequence (chat, inventory) cancels the fire.
        if (client.currentScreen != null) {
            abort("Cancelled — screen opened");
            return;
        }

        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        ticksInState++;

        switch (state) {
            case ROTATE: {
                // The per-frame mover in onRender drives the pitch down;
                // this tick check only watches for completion + timeout.
                if (player.getPitch() >= 90f) {
                    // View is down — swap to the wind charge slot and give
                    // the server `Delay` ticks to process the change.
                    selectSlot(player, windChargeSlot);
                    state = State.SWAP;
                    ticksInState = 0;
                } else if (ticksInState > MAX_ROTATE_TICKS) {
                    abort("Rotation stalled — aborting");
                }
                break;
            }
            case SWAP: {
                // Wait `Delay` ticks for the server to process the slot swap.
                if (ticksInState < delay.get()) return;
                // Guard: the wind charge must actually be in hand on the
                // client before we dare to use it.
                if (player.getMainHandStack().getItem() != Items.WIND_CHARGE) {
                    if (ticksInState > delay.get() + 10) {
                        abort("Swap didn't sync — aborting");
                    }
                    return;
                }
                state = State.FIRE;
                ticksInState = 0;
                // Fall through — fire on this same tick so the swap→use gap
                // is exactly `Delay` ticks, not Delay+1.
                fire(client, player, im);
                break;
            }
            case FIRE: {
                // (Firing happens inline from SWAP; this state exists only
                // if fire() was somehow skipped. Recover immediately.)
                state = State.RECOVER;
                ticksInState = 0;
                break;
            }
            case RECOVER: {
                // Wait `Delay` ticks for the use packet to flush, then
                // restore the original hotbar slot; the view glides back
                // up smoothly (RESTORE_LOOK) rather than snapping.
                if (ticksInState < delay.get()) return;
                if (restoreSlot.isEnabled()) {
                    restoreOriginalSlot();
                } else {
                    originalSlot = -1; // consumed — nothing to restore later
                }
                if (restoreLook.isEnabled()) {
                    state = State.RESTORE_LOOK;
                    ticksInState = 0;
                } else {
                    resetState();
                }
                break;
            }
            case RESTORE_LOOK: {
                // onRender's per-frame mover glides the pitch back toward
                // the original; this tick check watches for completion +
                // timeout (the mouse can fight it, same as ROTATE).
                float diff = Math.abs(player.getPitch() - originalPitch);
                if (diff <= 0.5f) {
                    player.setPitch(originalPitch);
                    resetState();
                } else if (ticksInState > MAX_ROTATE_TICKS) {
                    resetState();
                }
                break;
            }
            default: abort(null);
        }
    }

    /**
     * FIRE — right-click the wind charge and jump on the SAME tick:
     * the injected jump key press + direct {@code player.jump()} impulse
     * stack with the wind charge's ground burst into the boosted launch.
     */
    private void fire(MinecraftClient client, ClientPlayerEntity player,
                      ClientPlayerInteractionManager im) {
        // Last-moment guard: charge still in hand, no screen.
        if (player.getMainHandStack().getItem() != Items.WIND_CHARGE) {
            abort("Wind charge no longer in hand");
            return;
        }

        // Inject the jump first (real key press + same-tick impulse), then
        // right-click the charge — both land in the same tick's packets.
        if (client.options != null) {
            client.options.jumpKey.setPressed(true);
        }
        player.jump();
        jumpHeldTicks = 2;

        im.interactItem(player, Hand.MAIN_HAND);
        // interactItem alone never swings — play the throw animation the
        // way vanilla's doItemUse does (same reason ShieldBreaker /
        // TriggerBot swing explicitly).
        player.swingHand(Hand.MAIN_HAND);
        fired = true;
        state = State.RECOVER;
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  onRender — per-frame, time-scaled camera rotation (60fps+).
    //  The tick machine only sequences phases; the actual pitch motion
    //  lives here so it's frame-rate-independent and smooth (the same
    //  timing pattern Freecam uses: speed × 20 × frameDeltaSeconds,
    //  delta clamped to 100ms so a tab-out can't whip the camera).
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onRender(MinecraftClient client, float tickDelta) {
        if (client.player == null) return;
        if (state != State.ROTATE && state != State.RESTORE_LOOK) return;

        // ── Frame timing ───────────────────────────────────────
        long now = System.nanoTime();
        float frameDelta;
        if (lastFrameNanos == 0L) {
            frameDelta = 0f;  // first frame after trigger — no rotation jump
        } else {
            frameDelta = (now - lastFrameNanos) / 1_000_000_000f;
            if (frameDelta < 0f) frameDelta = 0f;
            if (frameDelta > 0.1f) frameDelta = 0.1f;
        }
        lastFrameNanos = now;
        if (frameDelta <= 0f) return;

        // Degrees available this frame (Look Speed is per tick → ×20/sec).
        float step = lookSpeed.get() * 20f * frameDelta;

        float pitch = client.player.getPitch();
        if (state == State.ROTATE) {
            // Glide down toward straight-down (90°), never overshooting.
            if (pitch < 90f) {
                client.player.setPitch(Math.min(pitch + step, 90f));
            }
        } else { // RESTORE_LOOK — glide back toward the original pitch.
            float diff = originalPitch - pitch;
            if (Math.abs(diff) > 0.01f) {
                float applied = MathHelper.clamp(diff, -step, step);
                client.player.setPitch(pitch + applied);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════
    private void abort(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && !fired && restoreLook.isEnabled() && originalPitch != 0f) {
            mc.player.setPitch(originalPitch);
        }
        restoreOriginalSlot();
        releaseJump();
        resetState();
    }

    private int findWindChargeSlot(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == Items.WIND_CHARGE) return i;
        }
        return -1;
    }

    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
    }

    private void restoreOriginalSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (restoreSlot.isEnabled() && originalSlot >= 0 && originalSlot <= 8) {
            selectSlot(mc.player, originalSlot);
        }
        originalSlot = -1;
    }
}
