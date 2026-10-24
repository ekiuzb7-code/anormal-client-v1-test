package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * ElytraSwap — packet-based chestplate ↔ elytra auto-swapper.
 *
 * Monitors the player's vertical motion and swaps the CHESTPLATE armor slot
 * with an Elytra (or back) using synthetic inventory packets — no inventory
 * screen, no cursor, instant.
 *
 * ── Swap mechanism ───────────────────────────────────────────────
 * Everything goes through {@code interactionManager.clickSlot} on the
 * player screen handler (syncId 0), which BOTH sends the ClickSlotC2SPacket
 * AND applies the predicted inventory change client-side. The one-packet
 * armor swap uses {@code SlotActionType.SWAP} with the chest slot (6) and a
 * hotbar button — byte-for-byte the packet vanilla sends when you press
 * 1-9 over an item in the inventory grid, so servers see a completely
 * ordinary hotkey swap (the same trust level as AutoTotem's PICKUP swap).
 * The server processes slot-click packets in order within a tick, so the
 * two-packet path for an inventory-stored elytra (inv → hotbar → chest) is
 * race-free.
 *
 * ── Triggers ─────────────────────────────────────────────────────
 *   Takeoff: airborne, falling at least `Min Fall` blocks, not already
 *   gliding — Mode Auto/Both additionally requires the JUMP key to be
 *   held (the vanilla mid-air "start flying" gesture — walking off a
 *   ledge alone never triggers it); Mode Key/Both fires on the bound key
 *   press. Vanilla's glide check runs every tick the jump key is held, so
 *   the glide engages on its own the tick after the swap lands.
 *
 *   Landing: touching ground / water / a ladder while wearing the elytra
 *   swaps back to the BEST chestplate available (netherite > diamond >
 *   iron > chain > gold > leather; highest remaining durability wins
 *   within a tier). If no chestplate exists, the elytra stays on.
 *
 *   Auto Firework: while gliding and losing altitude, selects a firework
 *   from the hotbar, uses it, and restores the previously held hotbar
 *   slot — the KeyPearl select → use → restore dance with a configurable
 *   cooldown between boosts.
 *
 * ── Safety ───────────────────────────────────────────────────────
 *   Durability Guard never equips an elytra with ≤ 2 durability left
 *   (a mid-flight break is fatal). All logic is skipped while a screen is
 *   open. The swap never sends movement packets — anti-cheat sees normal
 *   inventory interaction only.
 *
 * Settings:
 *   Mode, Swap Key, Min Fall, Auto Firework, Firework Cooldown,
 *   Restore On Land, Durability Guard
 */
public class ElytraSwap extends Module {

    private final EnumSetting mode = addSetting(
        new EnumSetting("Mode", "Auto = swap when jump is pressed mid-air while falling. Key = swap only on the bound key. Both = either.",
            List.of("Auto", "Key", "Both"), "Auto"));
    private final KeybindSetting swapKey = addSetting(
        new KeybindSetting("Swap Key", "Press this key mid-air to swap to the elytra (Key/Both modes). Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final FloatSetting minFall = addSetting(
        new FloatSetting("Min Fall", "Blocks of fall distance required before the mid-air elytra swap can fire.",
            0.5f, 10f, 1.5f, 1));
    private final BooleanSetting autoFirework = addSetting(
        new BooleanSetting("Auto Firework", "While gliding and losing altitude, use a firework from the hotbar to boost.",
            true));
    private final IntSetting fireworkCooldown = addSetting(
        new IntSetting("Firework Cooldown", "Ticks between automatic firework boosts.", 10, 200, 60, 1));
    private final BooleanSetting restoreOnLand = addSetting(
        new BooleanSetting("Restore On Land", "Swap the elytra back to your best chestplate when you land / touch water or a ladder.",
            true));
    private final BooleanSetting durabilityGuard = addSetting(
        new BooleanSetting("Durability Guard", "Never equip an elytra with 2 or less durability left (mid-flight break protection).",
            true));

    /** Player screen-handler slot index of the chest armor slot. */
    private static final int CHEST_SLOT = 6;
    /** Elytra durability that must remain for Durability Guard to allow equipping. */
    private static final int MIN_ELYTRA_DURABILITY = 2;

    // ── Key edge detection (bound swap key) ──────────────────
    private boolean wasKeyDown = false;
    /** Set by onTickAlways on the bound key's press edge; consumed by onTick. */
    private boolean keyTriggerPending = false;

    // ── Auto-firework mini state machine ─────────────────────
    private enum FwState { NONE, SELECTED, USED }
    private FwState fwState = FwState.NONE;
    private int fwTicks = 0;
    private int fwCooldown = 0;
    private int fwOrigSlot = -1;

    public ElytraSwap() {
        super("ElytraSwap", "Auto-swaps chestplate ↔ elytra with packets: elytra on when you jump mid-air, best chestplate back on landing.",
            Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        resetFirework();
        wasKeyDown = false;
        keyTriggerPending = false;
    }

    @Override
    protected void onDisable() {
        resetFirework();
    }

    private void resetFirework() {
        // Release the borrowed hotbar selection if we're mid-sequence.
        if (fwState != FwState.NONE && fwOrigSlot >= 0 && fwOrigSlot <= 8) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) selectSlot(mc.player, fwOrigSlot);
        }
        fwState = FwState.NONE;
        fwTicks = 0;
        fwOrigSlot = -1;
    }

    // ════════════════════════════════════════════════════════════════
    //  Bound-key polling — onTickAlways so the key works even while the
    //  module is disabled in ClickGUI (matches KeyPearl / Freecam).
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTickAlways(MinecraftClient client) {
        if (client.player == null || client.getWindow() == null) return;
        int key = swapKey.getKey();
        if (key < 0) return;

        long handle = client.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }
        if (isDown && !wasKeyDown) {
            keyTriggerPending = true;
        }
        wasKeyDown = isDown;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — swap triggers + firework sequencing (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        // A screen open (chat/inventory) freezes the auto logic — swapping
        // while the user is rearranging their inventory would fight them.
        if (client.currentScreen != null) { keyTriggerPending = false; return; }

        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        PlayerInventory inv = player.getInventory();

        boolean wearingElytra = player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;

        // ── Landing: elytra → best chestplate ────────────────────
        if (restoreOnLand.isEnabled() && wearingElytra
                && (player.isOnGround() || player.isTouchingWater() || player.isClimbing())) {
            swapBackToChestplate(player, im);
        }

        // ── Takeoff: chestplate → elytra ─────────────────────────
        if (!wearingElytra && !player.isOnGround() && !player.isTouchingWater()
                && !player.isClimbing() && !player.isGliding()
                && player.fallDistance >= minFall.get()) {
            String m = mode.get();
            boolean jumpGesture = client.options != null && client.options.jumpKey.isPressed();
            boolean autoFired = (m.equals("Auto") || m.equals("Both")) && jumpGesture;
            boolean keyFired = (m.equals("Key") || m.equals("Both")) && keyTriggerPending;
            if (autoFired || keyFired) {
                equipElytra(player, im);
            }
        }
        keyTriggerPending = false;

        // ── Auto firework while gliding ──────────────────────────
        tickFirework(client, player, im);
    }

    // ════════════════════════════════════════════════════════════════
    //  Swap helpers — all via SlotActionType.SWAP on the player screen
    //  (syncId 0, chest slot 6). clickSlot sends the packet AND predicts
    //  the inventory change client-side.
    // ════════════════════════════════════════════════════════════════
    private void equipElytra(ClientPlayerEntity player, ClientPlayerInteractionManager im) {
        PlayerInventory inv = player.getInventory();
        int syncId = player.currentScreenHandler.syncId;

        int hotbarIdx = findElytra(inv, 0, 9);
        if (hotbarIdx >= 0) {
            im.clickSlot(syncId, CHEST_SLOT, hotbarIdx, SlotActionType.SWAP, player);
            return;
        }
        int invIdx = findElytra(inv, 9, 36);
        if (invIdx >= 0) {
            // Two ordered packets (the server processes them in order within
            // a tick): inv → selected hotbar slot, then hotbar → chest. The
            // chestplate lands in the selected hotbar slot; the previously
            // held item moves to where the elytra was.
            int sel = inv.getSelectedSlot();
            im.clickSlot(syncId, invIdx, sel, SlotActionType.SWAP, player);
            im.clickSlot(syncId, CHEST_SLOT, sel, SlotActionType.SWAP, player);
        }
    }

    private void swapBackToChestplate(ClientPlayerEntity player, ClientPlayerInteractionManager im) {
        PlayerInventory inv = player.getInventory();
        int syncId = player.currentScreenHandler.syncId;

        int best = findBestChestplate(inv);
        if (best < 0) return;
    }

    /** Find an elytra in PlayerInventory index range [from, to) that passes the durability guard. */
    private int findElytra(PlayerInventory inv, int from, int to) {
        for (int i = from; i < to; i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == Items.ELYTRA && elytraUsable(s)) return i;
        }
        return -1;
    }

    private boolean elytraUsable(ItemStack s) {
        if (!durabilityGuard.isEnabled()) return true;
        return s.getMaxDamage() - s.getDamage() > MIN_ELYTRA_DURABILITY;
    }

    /** Best chestplate anywhere in the inventory (0-35) — highest tier, then most durability. */
    private int findBestChestplate(PlayerInventory inv) {
        int best = -1;
        int bestTier = 0;
        int bestDur = -1;
        for (int i = 0; i < 36; i++) {
            int tier = chestTier(inv.getStack(i).getItem());
            if (tier <= 0) continue;
            int dur = inv.getStack(i).getMaxDamage() - inv.getStack(i).getDamage();
            if (tier > bestTier || (tier == bestTier && dur > bestDur)) {
                best = i;
                bestTier = tier;
                bestDur = dur;
            }
        }
        return best;
    }

    private static int chestTier(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 6;
        if (item == Items.DIAMOND_CHESTPLATE) return 5;
        if (item == Items.IRON_CHESTPLATE) return 4;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 3;
        if (item == Items.GOLDEN_CHESTPLATE) return 2;
        if (item == Items.LEATHER_CHESTPLATE) return 1;
        return 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  Auto firework — select → use → restore (KeyPearl dance)
    // ════════════════════════════════════════════════════════════════
    private void tickFirework(MinecraftClient client, ClientPlayerEntity player,
                              ClientPlayerInteractionManager im) {
        if (fwCooldown > 0) fwCooldown--;
        PlayerInventory inv = player.getInventory();

        switch (fwState) {
            case NONE: {
                if (!autoFirework.isEnabled() || fwCooldown > 0) return;
                // Boost only while actually gliding and losing altitude —
                // never while climbing from the previous boost.
                if (!player.isGliding() || player.getVelocity().y >= 0.05) return;
                int fw = -1;
                for (int i = 0; i < 9; i++) {
                    if (inv.getStack(i).getItem() == Items.FIREWORK_ROCKET) { fw = i; break; }
                }
                if (fw < 0) return;
                fwOrigSlot = inv.getSelectedSlot();
                selectSlot(player, fw);
                fwState = FwState.SELECTED;
                fwTicks = 0;
                break;
            }
            case SELECTED: {
                fwTicks++;
                // Wait one tick for the slot-change to sync, then use.
                if (player.getMainHandStack().getItem() == Items.FIREWORK_ROCKET) {
                    if (fwTicks >= 1) {
                        im.interactItem(player, Hand.MAIN_HAND);
                        player.swingHand(Hand.MAIN_HAND);
                        fwState = FwState.USED;
                        fwTicks = 0;
                    }
                } else if (fwTicks > 10) {
                    // Firework moved/used elsewhere — abort the sequence.
                    restoreFwSlot(player);
                    fwState = FwState.NONE;
                    fwCooldown = fireworkCooldown.get();
                }
                break;
            }
            case USED: {
                fwTicks++;
                if (fwTicks >= 2) {
                    restoreFwSlot(player);
                    fwState = FwState.NONE;
                    fwCooldown = fireworkCooldown.get();
                }
                break;
            }
        }
    }

    private void restoreFwSlot(ClientPlayerEntity player) {
        if (fwOrigSlot >= 0 && fwOrigSlot <= 8) {
            selectSlot(player, fwOrigSlot);
        }
        fwOrigSlot = -1;
    }

    /** Hotbar select with the explicit slot packet (KeyPearl pattern). */
    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
    }
}
