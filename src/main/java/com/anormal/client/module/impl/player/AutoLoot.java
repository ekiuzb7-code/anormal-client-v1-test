package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.IntSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoLoot — when you open a storage block (chest, barrel, shulker, etc.),
 * automatically shift-clicks every item from the container into your
 * inventory.
 *
 * <h3>How it works</h3>
 * Each mc tick, if a {@link HandledScreen} is open and it's not the
 * player's own inventory screen, the module walks the handler's slots and
 * shift-clicks ({@link SlotActionType#QUICK_MOVE}) every container slot that
 * has an item — one per {@link #lootDelay} ticks so the server's slot-sync
 * keeps up. QUICK_MOVE on a container slot transfers the item into the
 * player's inventory, exactly like a manual shift-click. The player's own
 * inventory slots are skipped (identified by {@code slot.inventory instanceof
 * PlayerInventory}, the HoverTotem pattern).
 *
 * <h3>Triggering</h3>
 * The macro fires the moment a storage GUI opens. A one-shot guard per
 * container-open cycle prevents re-looting the same container if you close
 * and reopen quickly (or if items keep flowing in). The guard resets when
 * the screen closes, so opening a new container always triggers fresh.
 *
 * <h3>Real actions only</h3>
 * Uses {@code interactionManager.clickSlot(..., QUICK_MOVE, ...)} — a real
 * shift-click, the same input a player uses. No forged packets, no
 * container-stack spoofing. Same category as {@link AutoEat} / {@link AutoTotem}.
 *
 * Settings:
 *   Toggle Key, Loot Delay, Stop When Full.
 */
public class AutoLoot extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable auto-loot. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final IntSetting lootDelay = addSetting(
        new IntSetting("Loot Delay", "Ticks to wait between shift-clicks (keep the server's slot-sync happy).",
            1, 40, 2, 1));
    private final BooleanSetting stopWhenFull = addSetting(
        new BooleanSetting("Stop When Full", "Stop looting once your inventory can't hold any more.",
            true));

    /** Toggle key edge detection. */
    private boolean wasKeyDown = false;

    /** Slots we still plan to loot from the current open container. */
    private final List<Integer> pendingSlots = new ArrayList<>();
    private int pendingIndex = 0;
    private int ticksInState = 0;

    /** Tracks whether we've already looted the current open container, so we
     *  don't re-trigger every tick on the same GUI. Reset on screen close. */
    private boolean lootedCurrent = false;
    private boolean hadScreenLastTick = false;

    public AutoLoot() {
        super("AutoLoot",
            "Automatically moves all items from an opened storage block into your inventory.",
            Category.PLAYER);
    }

    @Override
    public void onEnable() {
        wasKeyDown = false;
        resetLoot();
        hadScreenLastTick = false;
    }

    @Override
    public void onDisable() {
        resetLoot();
    }

    @Override
    public void onTickAlways() {
        if (mc.player == null || mc.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = mc.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }
        if (isDown && !wasKeyDown) {
            setEnabled(!isEnabled());
        }
        wasKeyDown = isDown;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) return;

        boolean hasScreen = mc.currentScreen instanceof HandledScreen<?>;

        // Reset the one-shot guard when the screen closes, so the next open
        // container triggers a fresh loot pass.
        if (!hasScreen && hadScreenLastTick) {
            resetLoot();
        }
        hadScreenLastTick = hasScreen;

        if (!hasScreen) return;
        if (lootedCurrent) return;

        // Drive the per-click pacing.
        ticksInState++;
        if (pendingSlots.isEmpty()) {
            // First tick for this container — collect container-side slots
            // (non-player-inventory) that currently hold an item.
            ScreenHandler handler = player.currentScreenHandler;
            for (Slot slot : handler.slots) {
                if (slot.inventory instanceof PlayerInventory) continue;
                if (!slot.hasStack()) continue;
                pendingSlots.add(slot.id);
            }
            pendingIndex = 0;
            if (pendingSlots.isEmpty()) {
                // Empty container — mark looted so we don't rescan every tick.
                lootedCurrent = true;
                return;
            }
        }

        int delay = lootDelay.get();
        if (ticksInState < delay) return;
        ticksInState = 0;

        // Loot one slot per delay tick, skipping any that emptied (already
        // moved, or the player took it manually).
        while (pendingIndex < pendingSlots.size()) {
            int slotId = pendingSlots.get(pendingIndex);
            pendingIndex++;
            ScreenHandler handler = player.currentScreenHandler;
            if (slotId < 0 || slotId >= handler.slots.size()) continue;
            Slot slot = handler.slots.get(slotId);
            if (slot.inventory instanceof PlayerInventory) continue; // safety
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            // If the player's inventory is full and Stop When Full is on, bail
            // out of the whole pass (no point clicking — the server would just
            // bounce the item back).
            if (stopWhenFull.isEnabled() && inventoryFull(player)) {
                lootedCurrent = true;

                return;
            }

            int syncId = handler.syncId;
            mc.interactionManager.clickSlot(syncId, slotId, 0, SlotActionType.QUICK_MOVE, player);
            return;
        }

        // All slots processed.
        lootedCurrent = true;
    }

    /** True if the player has no empty, stackable slot for a typical item. */
    private static boolean inventoryFull(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) { // 0–8 hotbar, 9–35 main
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) return false;
            if (stack.isStackable() && stack.getCount() < stack.getMaxCount()) return false;
        }
        return true;
    }

    private void resetLoot() {
        pendingSlots.clear();
        pendingIndex = 0;
        ticksInState = 0;
        lootedCurrent = false;
    }
}