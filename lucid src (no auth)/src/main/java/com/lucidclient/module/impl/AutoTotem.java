package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * AutoTotem — always keep a Totem of Undying in your offhand.
 *
 * Each tick, scans the offhand (slot 45 in PlayerScreenHandler). If it's
 * not a totem (or is empty), scans the inventory (slots 9–44) for a totem
 * and uses the classic 3-click PICKUP swap:
 *
 *   Click 1: PICKUP on the totem slot   → picks the totem onto the cursor
 *   Click 2: PICKUP on offhand (45)      → swaps cursor (totem) ↔ offhand
 *   Click 3: PICKUP on the now-empty slot → puts whatever was on the cursor back
 *
 * Each click is staggered `delay` ticks apart so the server has time to
 * process the slot-click packets in order (otherwise the swap can race
 * and put items in the wrong slot).
 *
 * Health Threshold: only auto-equip when health is at or below the threshold
 * (so you don't waste totems you might want for another situation). Set to
 * 0 to always equip.
 *
 * NOTE: Works WITHOUT a screen open — the player's PlayerScreenHandler is
 * always active via currentScreenHandler when no other screen is open. We
 * explicitly call interactionManager.clickSlot with syncId=0 (player
 * handler's syncId) so the server processes the clicks server-side.
 */
public class AutoTotem extends Module {

    private static final int OFFHAND_SLOT = 45;
    private static final int INV_START = 9;
    private static final int INV_END = 44; // inclusive (main inv 9–35 + hotbar 36–44)

    private final IntSetting delay = addSetting(
        new IntSetting("Delay", "Ticks between each click in the 3-click swap. 1 = instant, 2+ is safer.", 1, 5, 2, 1));
    private final FloatSetting healthThreshold = addSetting(
        new FloatSetting("Health Threshold", "Only auto-equip when health is at or below this. 0 = always.", 0f, 20f, 0f, 0));

    private enum State { IDLE, CLICK_TOTEM, CLICK_OFFHAND, CLICK_BACK }

    private State state = State.IDLE;
    private int ticksInState = 0;
    private int totemSlot = -1;   // the slot we picked the totem from

    public AutoTotem() {
        super("AutoTotem", "Keeps a Totem of Undying in your offhand at all times.",
            Category.PLAYER);
    }

    @Override
    protected void onEnable() { resetState(); }
    @Override
    protected void onDisable() { resetState(); }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        totemSlot = -1;
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        if (player == null || im == null) { resetState(); return; }

        // Don't run while a screen is open — the user might be deliberately
        // rearranging items and we don't want to fight their cursor.
        if (client.currentScreen != null) {
            resetState();
            return;
        }

        // Health gate (0 = always equip).
        if (healthThreshold.get() > 0.01f && player.getHealth() > healthThreshold.get()) {
            // Below threshold not yet reached — keep watching but don't equip.
            resetState();
            return;
        }

        // Advance the state machine if a swap is in progress.
        if (state != State.IDLE) {
            ticksInState++;
            if (ticksInState < delay.get()) return;
            ticksInState = 0;
            stepStateMachine(player, im);
            return;
        }

        // ── Idle: scan offhand. ───────────────────────────────────
        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof PlayerScreenHandler)) return;
        PlayerScreenHandler psh = (PlayerScreenHandler) handler;

        if (slotHasTotem(psh, OFFHAND_SLOT)) return; // already equipped

        // Find a totem in the inventory (9–44).
        int found = -1;
        for (int i = INV_START; i <= INV_END; i++) {
            if (slotHasTotem(psh, i)) { found = i; break; }
        }
        if (found < 0) return; // no totem available

        // ── Start the 3-click PICKUP swap. ───────────────────────
        totemSlot = found;
        int syncId = psh.syncId;
        // Click 1: PICKUP the totem onto the cursor.
        im.clickSlot(syncId, found, 0, SlotActionType.PICKUP, player);
        state = State.CLICK_TOTEM;
        ticksInState = 0;
    }

    private void stepStateMachine(ClientPlayerEntity player, ClientPlayerInteractionManager im) {
        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof PlayerScreenHandler)) { resetState(); return; }
        PlayerScreenHandler psh = (PlayerScreenHandler) handler;
        int syncId = psh.syncId;

        switch (state) {
            case CLICK_TOTEM:
                // Click 2: PICKUP on offhand → swaps cursor (totem) with offhand.
                im.clickSlot(syncId, OFFHAND_SLOT, 0, SlotActionType.PICKUP, player);
                state = State.CLICK_OFFHAND;
                break;
            case CLICK_OFFHAND:
                // Click 3: PICKUP back on the totem's original slot → puts whatever
                // was on the cursor (the item that was in offhand) back into the
                // now-empty slot. If the cursor is empty (offhand was empty), this
                // is a no-op server-side but still keeps the state machine clean.
                im.clickSlot(syncId, totemSlot, 0, SlotActionType.PICKUP, player);
                state = State.CLICK_BACK;
                break;
            case CLICK_BACK:
                resetState();
                break;
            default:
                resetState();
                break;
        }
    }

    private boolean slotHasTotem(PlayerScreenHandler psh, int slotId) {
        if (slotId < 0 || slotId >= psh.slots.size()) return false;
        ItemStack stack = psh.slots.get(slotId).getStack();
        return !stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING);
    }

    /** Static helper used by InventoryTotem (same swap logic, different trigger). */
    public static int findTotemSlot(PlayerScreenHandler psh) {
        for (int i = INV_START; i <= INV_END; i++) {
            if (i >= psh.slots.size()) break;
            ItemStack stack = psh.slots.get(i).getStack();
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    public static boolean offhandHasTotem(PlayerScreenHandler psh) {
        if (OFFHAND_SLOT >= psh.slots.size()) return false;
        ItemStack stack = psh.slots.get(OFFHAND_SLOT).getStack();
        return !stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING);
    }

    /** Performs one step of the 3-click PICKUP swap. Returns the next click
     *  index (0 = pickup totem, 1 = swap with offhand, 2 = put back, 3 = done).
     *  Used by InventoryTotem / HoverTotem so they share the same swap pattern. */
    public static void clickPickupSwapStep(ClientPlayerInteractionManager im, PlayerEntity player,
                                           int syncId, int totemSlot, int step) {
        switch (step) {
            case 0: // PICKUP the totem onto the cursor.
                im.clickSlot(syncId, totemSlot, 0, SlotActionType.PICKUP, player); break;
            case 1: // PICKUP on offhand → swaps cursor with offhand.
                im.clickSlot(syncId, OFFHAND_SLOT, 0, SlotActionType.PICKUP, player); break;
            case 2: // PICKUP back on the totem's original slot → puts cursor back.
                im.clickSlot(syncId, totemSlot, 0, SlotActionType.PICKUP, player); break;
            default: break;
        }
    }

    public static int getOffhandSlotId() { return OFFHAND_SLOT; }
}
