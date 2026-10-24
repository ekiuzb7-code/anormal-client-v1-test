package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * InventoryTotem — puts a totem in your offhand the moment you open your
 * inventory (E).
 *
 * ── How it works ───────────────────────────────────────────────
 * The module watches for the inventory screen OPENING. On that edge it
 * checks the offhand:
 *
 *  - Offhand already holds a totem → nothing happens, and nothing will
 *    happen for the rest of that open either.
 *  - Offhand is empty (the common case right after a pop): a single
 *    {@code clickSlot(syncId, containerSlot, 40, SWAP)} — vanilla's
 *    offhand-swap action (the F-key swap). One packet, totem lands in the
 *    offhand, nothing left on the cursor.
 *  - Offhand holds something else: the classic 3-click PICKUP dance —
 *    totem to cursor, cursor to offhand (container slot 45), whatever was
 *    there back to the totem's old slot.
 *
 * It is strictly ONE-SHOT PER OPEN: after a swap (or a decision not to
 * swap) the module stays quiet for the rest of that inventory session, so
 * sitting in the inventory never re-equips you over and over. Close and
 * reopen (E) to trigger it again.
 *
 * Totem search order matches glazed: main inventory (9–35) first, then the
 * hotbar (36–44) when {@code Move From Hotbar} is on — so it never steals
 * your hotbar totem while a main-inventory one is available.
 *
 * ── Auto Open Inv ──────────────────────────────────────────────
 * With {@code Open Inv} on, a totem pop auto-opens your inventory after
 * {@code Inv Open Delay} ticks (server sees only the swap clicks — opening
 * a client screen is invisible), the swap runs through the same one-shot
 * path, and the screen auto-closes after {@code Inv Close Delay} ticks.
 *
 * Settings:
 *   Delay            — ticks to wait after the inventory opens before swapping.
 *   Move From Hotbar — allow taking the totem from the hotbar (fallback only).
 *   Open Inv         — auto-open the inventory when a totem pops.
 *   Inv Open Delay   — ticks after the pop before the inventory opens.
 *   Inv Close Delay  — ticks after the swap before the auto-opened screen closes.
 */
public class InventoryTotem extends Module {

    /** Offhand container slot in {@link PlayerScreenHandler}. */
    private static final int OFFHAND_SLOT = 45;
    /** SWAP button that targets the offhand (hotbar 0–8, offhand 40). */
    private static final int OFFHAND_SWAP_BUTTON = 40;

    private final IntSetting delay = addSetting(
        new IntSetting("Delay", "Ticks to wait after the inventory opens before swapping the totem in (looks human).", 0, 20, 3, 1));
    private final BooleanSetting moveFromHotbar = addSetting(
        new BooleanSetting("Move From Hotbar", "Allow taking the totem from the hotbar when the main inventory has none.", true));
    private final BooleanSetting openInv = addSetting(
        new BooleanSetting("Open Inv", "Auto-open your inventory when a totem pops (the swap clicks are all the server sees).", false));
    private final IntSetting invOpenDelay = addSetting(
        new IntSetting("Inv Open Delay", "Ticks after a totem pop before the inventory auto-opens.", 1, 10, 2, 1));
    private final IntSetting invCloseDelay = addSetting(
        new IntSetting("Inv Close Delay", "Ticks after the swap before the auto-opened inventory closes.", 5, 20, 8, 1));

    // ── Per-open state ────────────────────────────────────────────
    /** Was the inventory screen open last tick? (open-edge detector) */
    private boolean invWasOpen = false;
    /** True once this inventory open has been acted on — the one-shot latch. */
    private boolean handledThisOpen = false;
    /** Ticks elapsed since the current inventory open. */
    private int openTicks = 0;

    // ── Auto-open state ───────────────────────────────────────────
    /** True while a totem pop is waiting for the auto-open delay. */
    private boolean autoOpenArmed = false;
    private int autoOpenTicks = 0;
    /** True while an inventory WE auto-opened is waiting to be closed. */
    private boolean closingAutoOpened = false;
    private int closeTicks = 0;
    /** True if this inventory session was opened by us (so we close it again). */
    private boolean weOpenedInv = false;

    // ── Pop detection ─────────────────────────────────────────────
    /** Offhand totem presence last tick — the falling edge is the pop. */
    private boolean hadTotemLastTick = false;
    private boolean worldInit = false;

    public InventoryTotem() {
        super("InventoryTotem", "Puts a totem in your offhand when you open your inventory (E) — once per open, silent if one is already there.",
            Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        // Never leave an auto-opened screen behind.
        if (weOpenedInv && MinecraftClient.getInstance().currentScreen instanceof InventoryScreen) {
            MinecraftClient.getInstance().setScreen(null);
        }
        resetState();
    }

    private void resetState() {
        invWasOpen = false;
        handledThisOpen = false;
        openTicks = 0;
        autoOpenArmed = false;
        autoOpenTicks = 0;
        closingAutoOpened = false;
        closeTicks = 0;
        weOpenedInv = false;
        worldInit = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        if (player == null || im == null) {
            resetState();
            return;
        }

        boolean hasTotem = player.getEquippedStack(EquipmentSlot.OFFHAND).isOf(Items.TOTEM_OF_UNDYING);
        boolean invOpen = client.currentScreen instanceof InventoryScreen;

        // ── Pop detection: offhand totem gone ⇒ arm the auto-open ──
        if (!worldInit) {
            hadTotemLastTick = hasTotem;   // seed the edge detector, don't arm
            worldInit = true;
        } else if (openInv.isEnabled() && hadTotemLastTick && !hasTotem && !invOpen) {
            autoOpenArmed = true;
            autoOpenTicks = 0;
        }
        hadTotemLastTick = hasTotem;

        // ── Close the inventory we auto-opened ─────────────────────
        if (closingAutoOpened) {
            if (!invOpen) {
                resetState();              // player closed it themselves
                return;
            }
            if (++closeTicks >= invCloseDelay.get()) {
                client.setScreen(null);
                resetState();
                return;
            }
        } else if (weOpenedInv && !invOpen) {
            weOpenedInv = false;           // auto-opened screen dismissed by hand
        }

        // ── Inventory open edge: arm the one-shot swap ─────────────
        if (invOpen && !invWasOpen) {
            handledThisOpen = false;
            openTicks = 0;
        }
        invWasOpen = invOpen;

        if (invOpen && !handledThisOpen) {
            if (hasTotem) {
                // Spec: a totem is already offhanded — do nothing, and don't
                // reconsider for the rest of this open.
                handledThisOpen = true;
            } else if (++openTicks >= delay.get()) {
                handledThisOpen = true;    // one-shot, acted on or not
                moveTotemToOffhand(player, im);
                if (weOpenedInv) {
                    closingAutoOpened = true;
                    closeTicks = 0;
                }
            }
        }

        // ── Auto-open last, so it never masks this tick's screen state ──
        if (autoOpenArmed) {
            if (invOpen || client.currentScreen != null) {
                autoOpenArmed = false;     // some screen is up — don't intrude
            } else if (++autoOpenTicks >= invOpenDelay.get()) {
                autoOpenArmed = false;
                client.setScreen(new InventoryScreen(player));
                weOpenedInv = true;
                invWasOpen = false;        // next tick registers the open edge
            }
        }
    }

    /**
     * Perform the swap (glazed-style).
     *
     * @return true if a totem was moved into the offhand.
     */
    private boolean moveTotemToOffhand(ClientPlayerEntity player, ClientPlayerInteractionManager im) {
        if (!(player.currentScreenHandler instanceof PlayerScreenHandler)) return false;
        PlayerScreenHandler psh = (PlayerScreenHandler) player.currentScreenHandler;
        int syncId = psh.syncId;

        // Main inventory (container slots 9–35) first, then hotbar (36–44).
        int containerSlot = findTotemContainerSlot(player, moveFromHotbar.isEnabled());
        if (containerSlot < 0) return false;

        PlayerEntity playerEntity = (PlayerEntity) player;
        if (player.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty()) {
            // Offhand empty (post-pop): the one-packet offhand swap.
            im.clickSlot(syncId, containerSlot, OFFHAND_SWAP_BUTTON, SlotActionType.SWAP, playerEntity);
        } else {
            // Offhand occupied: 3-click PICKUP swap through the cursor.
            im.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, playerEntity); // totem → cursor
            im.clickSlot(syncId, OFFHAND_SLOT, 0, SlotActionType.PICKUP, playerEntity);  // cursor ↔ offhand
            im.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, playerEntity); // put back
        }
        return true;
    }

    /**
     * Find a totem as a PlayerScreenHandler container slot id: main
     * inventory (9–35) first; hotbar (36–44) as fallback.
     */
    private int findTotemContainerSlot(ClientPlayerEntity player, boolean includeHotbar) {
        for (int i = 9; i <= 35; i++) {
            if (player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        if (includeHotbar) {
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i + 36;
            }
        }
        return -1;
    }
}
