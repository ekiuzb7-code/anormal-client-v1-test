package com.anormal.client.module.impl.combat;

import com.anormal.client.mixin.MinecraftClientAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.IntSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/**
 * LungeSwapper — one-shot Spear lunge macro (Category.COMBAT).
 *
 * <h3>The "attribute swap" technique (how it actually bypasses the charge gate)</h3>
 * The Spear's left-click STAB is gated by {@code PlayerEntity.isBelowMinimumAttackCharge},
 * which reads the {@code MINIMUM_ATTACK_CHARGE} component off the <b>currently-held
 * stack</b> and compares {@code ticksSinceLastAttack} against it. Two facts make the
 * bypass work:
 * <ol>
 *   <li>The gate is <b>per-stack</b>: a non-spear item (or empty fist) has no
 *       {@code MINIMUM_ATTACK_CHARGE} component, so the threshold defaults to 0 and
 *       the gate passes trivially — meaning {@code ticksSinceLastAttack} keeps
 *       climbing freely while you hold a non-spear item.</li>
 *   <li>The clock is only reset to 0 by {@code PlayerEntity.tick()} when it detects
 *       the held <b>item type</b> changed (via {@code areItemsEqual}). That reset
 *       happens on the NEXT tick after the swap — <b>not</b> the same tick.</li>
 * </ol>
 * So the bypass sequence is tight and same-tick:
 * <ol>
 *   <li>Hold a non-spear item for several ticks → {@code ticksSinceLastAttack}
 *       accumulates high (the gate is moot for non-spear stacks).</li>
 *   <li><b>Swap to the spear slot AND fire the STAB in the same tick</b> — the
 *       server processes the {@code UpdateSelectedSlotC2SPacket} and the STAB
 *       packet before the next {@code tick()} runs, so {@code ticksSinceLastAttack}
 *       is still large and the spear's {@code MINIMUM_ATTACK_CHARGE} gate
 *       passes.</li>
 *   <li><b>Swap back to the non-spear item the same/next tick</b> — so the clock
 *       keeps climbing for the next cycle (and the player isn't left holding the
 *       spear).</li>
 * </ol>
 * Crucially: we must <b>not</b> wait ticks between the swap-to-spear and the STAB
 * (the clock would reset and the gate would fail), and we must <b>not</b> gate on
 * {@code isBelowMinimumAttackCharge} ourselves (the whole point is the gate reads
 * the stale high clock from the non-spear phase).
 *
 * <h3>Sequence per cycle</h3>
 * {@code IDLE → SWAP_AND_STAB (swap to spear + STAB same tick) → SWAP_BACK (next
 * tick) → LOOP_WAIT (hold non-spear, let clock climb) → repeat}
 *
 * <h3>Real inputs only</h3>
 * The STAB fires through the real {@code interactionManager.attackWithPiercingWeapon}
 * path (the exact vanilla {@code doAttack} piercing branch), and slot swaps use
 * the real {@link UpdateSelectedSlotC2SPacket}. No packet spoofing, no forged
 * interactions. The cooldown reset ({@code itemUseCooldown} + {@code attackCooldown}
 * via {@link MinecraftClientAccessor}) is the legitimate effect of the item swap
 * plus the same accessor the existing AutoCrystal/CrystalOptimizer modules use.
 * The {@code MINIMUM_ATTACK_CHARGE} bypass is a vanilla quirk of the per-stack
 * threshold + the one-tick delay in {@code tick()} clock reset — it does not
 * evade any server-side per-item cooldown (the Spear STAB registers none).
 *
 * Settings:
 *   Trigger Key, Loop Delay, Humanize Delays, Require Lunge Enchant.
 */
public class LungeSwapper extends Module {

    private final KeybindSetting triggerKey = addSetting(
        new KeybindSetting("Trigger Key", "Press to lunge-swap: swap to a hotbar Spear, left-click to lunge, swap back to your original slot. Hold to repeat.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final IntSetting loopDelay = addSetting(
        new IntSetting("Loop Delay", "Ticks to hold the non-spear item between lunges. Lets the server-side attack-charge clock climb so the next STAB passes the MINIMUM_ATTACK_CHARGE gate. 5+ is safe; too low and lunges get dropped.",
            1, 20, 6, 1));
    private final BooleanSetting humanize = addSetting(
        new BooleanSetting("Humanize Delays", "Jitter the loop delay ±1-2 ticks each cycle so the timing looks less automated.",
            true));
    private final BooleanSetting requireLunge = addSetting(
        new BooleanSetting("Require Lunge Enchant", "Only activate if the hotbar Spear actually has the Lunge enchantment (otherwise the swap is pointless).",
            true));

    // ── State machine ──────────────────────────────────────────────
    // Each phase advances one per mc tick (onTick). The STAB must fire
    // the SAME tick as the swap-to-spear (before tick() resets the charge
    // clock), and the swap-back happens the next tick.
    private enum Phase {
        IDLE,            // waiting for a trigger-key press
        SWAP_AND_STAB,   // swap to the spear + fire the STAB same tick
        SWAP_BACK,       // swap back to the original (non-spear) slot next tick
        LOOP_WAIT        // hold non-spear; let ticksSinceLastAttack climb
    }

    private Phase phase = Phase.IDLE;
    private int originalSlot = -1;   // slot the player held when trigger fired
    private int spearSlot = -1;      // resolved spear slot for this cycle
    private int waitTicks = 0;       // ticks remaining in LOOP_WAIT

    // Edge detection for the trigger key (polled in onTickAlways).
    private boolean wasTriggerDown = false;

    public LungeSwapper() {
        super("LungeSwapper",
            "One-shot Spear lunge macro using attribute swapping: swap to a hotbar Spear, STAB to lunge, swap back to your original slot — bypasses the Spear's attack-charge gate. Hold the trigger key to repeat.",
            Category.COMBAT);
    }

    @Override
    public void onEnable() {
        phase = Phase.IDLE;
        originalSlot = -1;
        spearSlot = -1;
        waitTicks = 0;
        wasTriggerDown = false;
    }

    @Override
    public void onDisable() {
        // If we left the player on the spear mid-cycle, swap back to the
        // original slot so we don't strand them.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null && originalSlot >= 0) {
            selectSlot(mc.player, originalSlot);
        }
        phase = Phase.IDLE;
    }

    @Override
    public void onTickAlways() {
        if (mc.player == null || mc.getWindow() == null) return;
        if (!isEnabled()) return; // parity with AutoClicker — macro only fires when the module is on
        int key = triggerKey.getKey();
        if (key < 0) return;

        long handle = mc.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        // Trigger-key press starts a cycle. Release stops the loop and
        // restores the original slot.
        if (isDown && !wasTriggerDown) {
            if (phase == Phase.IDLE) {
                int spear = findSpearSlot();
                if (spear >= 0) {
                    originalSlot = mc.player.getInventory().getSelectedSlot();
                    spearSlot = spear;
                    phase = Phase.SWAP_AND_STAB;
                }
            }
        } else if (!isDown && phase != Phase.IDLE) {
            // Key released mid-cycle — stop and restore the original slot.
            if (mc.player != null && originalSlot >= 0) {
                selectSlot(mc.player, originalSlot);
            }
            phase = Phase.IDLE;
        }
        wasTriggerDown = isDown;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return; // never fire while a GUI is open
        if (phase == Phase.IDLE) return;

        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;

        switch (phase) {
            case SWAP_AND_STAB -> {
                // ── The bypass, in one tick ───────────────────────────
                // The player has been holding the original (non-spear) item,
                // so ticksSinceLastAttack has climbed freely (the non-spear
                // stack has no MINIMUM_ATTACK_CHARGE, threshold = 0). We swap
                // to the spear AND fire the STAB in the SAME tick — the
                // server processes both packets before the next PlayerEntity
                // .tick() runs the areItemsEqual() clock-reset, so the gate
                // reads the stale HIGH clock and passes.
                selectSlot(player, spearSlot);

                // Zero the client-side gates so nothing locally blocks the
                // STAB (legitimate swap-cancel effect, same accessor
                // AutoCrystal uses). The server-side MINIMUM_ATTACK_CHARGE
                // gate is bypassed by the swap timing above, NOT by this.
                MinecraftClientAccessor acc = (MinecraftClientAccessor) mc;
                acc.lucid$setItemUseCooldown(0);
                acc.lucid$setAttackCooldown(0);

                // Fire the STAB via the REAL vanilla Spear left-click path:
                // doAttack() on a stack with PIERCING_WEAPON calls
                // interactionManager.attackWithPiercingWeapon(component),
                // which sends the STAB packet and runs
                // useAttackEnchantmentEffects() client-side — that's what
                // fires the Lunge post_piercing_attack apply_impulse forward
                // dash. We deliberately do NOT gate on
                // isBelowMinimumAttackCharge here — the whole technique is
                // that the gate reads the stale high clock from the non-spear
                // phase. Gating would block the lunge.
                ItemStack spear = player.getMainHandStack();
                PiercingWeaponComponent pwc = spear.get(DataComponentTypes.PIERCING_WEAPON);
                if (pwc != null) {
                    im.attackWithPiercingWeapon(pwc);
                    player.swingHand(Hand.MAIN_HAND);
                }
                // Move to SWAP_BACK — handled next tick (so the STAB packet
                // is fully sent first and the swap-back doesn't race it).
                phase = Phase.SWAP_BACK;
            }
            case SWAP_BACK -> {
                // Swap back to the original (non-spear) slot. This returns
                // the player to their item AND starts the
                // ticksSinceLastAttack climb for the next cycle (the
                // non-spear stack has threshold 0, so the clock accumulates
                // freely while we wait in LOOP_WAIT).
                selectSlot(player, originalSlot);
                waitTicks = computeLoopDelay();
                phase = Phase.LOOP_WAIT;
            }
            case LOOP_WAIT -> {
                if (waitTicks > 0) { waitTicks--; return; }
                // Re-arm — if the key is still physically held, start a fresh
                // cycle (hold-to-repeat). Otherwise go idle.
                if (isTriggerHeld()) {
                    int spear = findSpearSlot();
                    if (spear >= 0) {
                        originalSlot = player.getInventory().getSelectedSlot();
                        spearSlot = spear;
                        phase = Phase.SWAP_AND_STAB;
                    } else {
                        phase = Phase.IDLE;
                    }
                } else {
                    phase = Phase.IDLE;
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    /**
     * Scan the hotbar (slots 0-8) for a Spear. If {@code requireLunge} is on,
     * the spear must have the Lunge enchantment (level &gt; 0). Returns the
     * slot index, or -1 if none found.
     */
    private int findSpearSlot() {
        if (mc.player == null || mc.world == null) return -1;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isIn(ItemTags.SPEARS)) continue;
            if (requireLunge.isEnabled() && !hasLunge(mc, stack)) continue;
            return i;
        }
        return -1;
    }

    /** True if the stack has the Lunge enchantment (any level > 0). */
    private boolean hasLunge(MinecraftClient mc, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            Registry<net.minecraft.enchantment.Enchantment> reg =
                mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            RegistryEntry<net.minecraft.enchantment.Enchantment> lunge =
                reg.getOrThrow(net.minecraft.enchantment.Enchantments.LUNGE);
            return EnchantmentHelper.getLevel(lunge, stack) > 0;
        } catch (Throwable t) {
            return false; // registry not ready — assume no lunge
        }
    }

    /** True if the trigger key is currently physically held. */
    private boolean isTriggerHeld() {
        if (mc.getWindow() == null) return false;
        int key = triggerKey.getKey();
        if (key < 0) return false;
        long handle = mc.getWindow().getHandle();
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            return GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }

    /** Compute the loop-wait ticks, applying humanization jitter if enabled. */
    private int computeLoopDelay() {
        int base = loopDelay.get();
        if (!humanize.isEnabled()) return Math.max(1, base);
        int jitter = ThreadLocalRandom.current().nextInt(-2, 3); // -2..2 inclusive
        return Math.max(1, base + jitter);
    }

    /** Set the player's selected hotbar slot and sync it to the server. */
    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() != slot) {
            inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
        }
    }
}