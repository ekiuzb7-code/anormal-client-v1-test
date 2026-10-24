package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/**
 * KeyPearl — throw an ender pearl on a hotkey, smoothly.
 *
 * On the bound key press, the module:
 *   1. Scans the hotbar for an Ender Pearl. If none, do nothing (no spam).
 *   2. If the pearl is ALREADY the held item, it throws next tick with zero
 *      slot packets at all — no swap, no flicker, instant.
 *   3. Otherwise SWAP: selects the pearl slot and waits `Delay` ticks for
 *      the server to process the slot-change packet (race-fix — without
 *      this wait the use-item packet can arrive first and you'd throw
 *      whatever you were holding).
 *   4. THROW: {@code interactionManager.interactItem} sends the use packet,
 *      and {@code player.swingHand} plays the REAL throw animation —
 *      first-person arm swing AND the full third-person (F5) swing, so the
 *      throw reads naturally from outside.
 *   5. RESTORE: one tick later (enough — the connection delivers the use
 *      packet before the slot-change, and the server processes packets in
 *      order) the original hotbar slot is re-selected and the sequence
 *      ends.
 *
 * ── The moving-stutter fix (v1.14.12) ─────────────────────────────
 * The old version's stutter while moving was the vanilla held-item
 * "equip" animation: every selected-slot change dips the held item down
 * and back up, and the old flow did TWO rapid swaps (pearl in, pearl out
 * ~5 ticks apart) — two dips back-to-back reads as the view/hand
 * stuttering, especially while sprinting. Every slot select now calls
 * {@link HeldItemRenderer#resetEquipProgress} right after the change,
 * which snaps the equip animation to its settled state — no dip, no
 * flicker, the pearl is simply in hand for a moment and then gone. The
 * restore was also tightened from a `Delay`-gated wait to a fixed single
 * tick, so the whole pearl-in-hand window is as short as the server
 * race-safety allows.
 *
 * Settings:
 *   Toggle Key, Delay
 */
public class KeyPearl extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key",
            "Press this key to throw an ender pearl from your hotbar. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final IntSetting delay = addSetting(
        new IntSetting("Delay",
            "Ticks to wait after swapping to the pearl before throwing (server race-fix). "
            + "1 = fastest (50ms). 2+ is safer vs anti-cheat.",
            1, 5, 2, 1));

    // ── State machine ────────────────────────────────────────
    private enum State { IDLE, SWAP, THROW, RESTORE }

    private State state = State.IDLE;
    private int ticksInState = 0;

    // Inventory state (captured at trigger).
    private int originalSlot = -1;
    private int pearlSlot = -1;
    /** True if WE changed the selected slot (pearl wasn't already held). */
    private boolean weSwapped = false;

    // Key edge detection.
    private boolean wasKeyDown = false;

    public KeyPearl() {
        super("KeyPearl", "Throw an ender pearl from your hotbar on a hotkey — instant when already held, no swap-stutter, full throw animation.",
            Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        resetState();
        wasKeyDown = false;
    }

    @Override
    protected void onDisable() {
        // If we were mid-sequence, snap back to the original slot.
        restoreOriginalSlot();
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        originalSlot = -1;
        pearlSlot = -1;
        weSwapped = false;
    }

    // ════════════════════════════════════════════════════════════════
    //  Keybind polling — onTickAlways so the key works even while the
    //  module is disabled in ClickGUI (matches AutoClicker / Freecam).
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
    //  TRIGGER — find a pearl, save state, start the sequence
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

        // Scan the hotbar for an Ender Pearl.
        pearlSlot = findPearlSlot(inv);
        if (pearlSlot < 0) {
            resetState();
            return;
        }

        originalSlot = inv.getSelectedSlot();

        if (pearlSlot == originalSlot) {
            // Already holding the pearl — no slot packets needed at all.
            // THROW fires on this same tick's onTick pass: instant, zero
            // swap flicker.
            weSwapped = false;
            state = State.THROW;
            ticksInState = 0;
            return;
        }

        // Select the pearl slot now (smooth — no equip dip); SWAP waits
        // `Delay` ticks before sending the use packet so the server
        // processes the slot-change first (race-fix).
        selectSlotSmooth(player, pearlSlot);
        weSwapped = true;
        state = State.SWAP;
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — advance the state machine (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (state == State.IDLE) return;
        if (client.player == null || client.interactionManager == null) {
            restoreOriginalSlot();
            resetState();
            return;
        }

        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        ticksInState++;

        switch (state) {
            case SWAP: {
                // Wait `Delay` ticks for the server to process the slot swap.
                if (ticksInState < delay.get()) return;
                // Guard: pearl must still be in hand on the client.
                if (player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
                    if (ticksInState > delay.get() + 10) {
                        restoreOriginalSlot();
                        resetState();
                    }
                    return;
                }
                // Ready — throw on this same tick so the swap→use gap is
                // exactly `Delay` ticks.
                doThrow(player, im);
                break;
            }
            case THROW: {
                // Pearl was already held at trigger — throw now (this is
                // the same tick as the key press, or the tick after).
                if (player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
                    // The pearl left the hand somehow — nothing to throw.
                    restoreOriginalSlot();
                    resetState();
                    return;
                }
                doThrow(player, im);
                break;
            }
            case RESTORE: {
                // One tick for the use packet to flush (the connection
                // delivers packets in order, so the use-item lands before
                // any subsequent slot-change — no extra wait needed).
                if (ticksInState < 1) return;
                restoreOriginalSlot();
                resetState();
                break;
            }
            default: resetState();
        }
    }

    /**
     * Fire the pearl: interactItem sends the use-item packet (for a
     * throw-on-use item like the ender pearl that's all it takes), and
     * swingHand plays the REAL throw animation in both first and third
     * person (interactItem alone never swings — the same reason
     * ShieldBreaker / TriggerBot swing explicitly).
     */
    private void doThrow(ClientPlayerEntity player, ClientPlayerInteractionManager im) {
        im.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        state = State.RESTORE;
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════
    private int findPearlSlot(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == Items.ENDER_PEARL) return i;
        }
        return -1;
    }

    /**
     * Hotbar select + explicit slot packet, then reset the first-person
     * equip animation. Without the reset, every slot change dips the held
     * item down-and-up — two rapid swaps read as view stutter while
     * moving. With it, the item simply appears in hand (and vanishes on
     * the swap-back) with no dip at all.
     */
    private void selectSlotSmooth(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.gameRenderer != null && mc.gameRenderer.firstPersonRenderer != null) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND);
            }
        } catch (Throwable ignored) {
            // Purely cosmetic — never let it break the throw.
        }
    }

    private void restoreOriginalSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (weSwapped && originalSlot >= 0 && originalSlot <= 8) {
            selectSlotSmooth(mc.player, originalSlot);
        }
        originalSlot = -1;
    }
}
