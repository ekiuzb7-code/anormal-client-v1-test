package com.lucidclient.module.impl;

import com.lucidclient.mixin.HandledScreenAccessor;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * HoverTotem — hover a Totem of Undying in any open container and it swaps
 * straight into your offhand (glazed-style).
 *
 * ── How it works ───────────────────────────────────────────────
 * A single inventory click does the whole move:
 *
 *   clickSlot(syncId, hoveredSlot.id, 40, SWAP)
 *
 * SWAP with button 40 is vanilla's offhand-swap action (the same thing the
 * client sends when you press F over an item in your own inventory) — it
 * exchanges the clicked handler slot with the offhand in ONE packet. The
 * hovered totem goes to the offhand and whatever the offhand held (sword,
 * crystal, gap) lands in the hovered slot. Works in ANY container — chests,
 * shulkers, your own inventory — because it addresses the hovered slot by
 * its handler id, not a hardcoded player-screen slot.
 *
 * The old 3-click PICKUP dance (totem → cursor → destination → put back,
 * ~6 ticks) is gone: one click, zero cursor juggling, nothing on the cursor
 * if you close the screen mid-swap.
 *
 * ── Guards ─────────────────────────────────────────────────────
 *  - Requires the hovered stack to actually be a totem.
 *  - Skips when the offhand already holds a totem (swapping two totems is
 *    a no-op that would re-trigger every tick while hovering).
 *  - Optional sneak gate (default on, matching glazed): only fire while the
 *    sneak key is held, so browsing a chest never moves items by accident.
 *
 * ── The mixin, not reflection ──────────────────────────────────
 * HandledScreen.focusedSlot is PROTECTED. In a dev environment reflection
 * works, but production Fabric remaps field names to intermediary, so
 * `getDeclaredField("focusedSlot")` throws NoSuchFieldException. We use the
 * HandledScreenAccessor mixin (@Accessor) which generates a proper getter
 * that works in both dev and production.
 *
 * Settings:
 *   Require Sneak — only swap while sneaking.
 */
public class HoverTotem extends Module {

    private final BooleanSetting requireSneak = addSetting(
        new BooleanSetting("Require Sneak", "Only swap while the sneak key is held, so browsing containers never moves items by accident.", true));

    public HoverTotem() {
        super("HoverTotem", "Hover a totem in any container to swap it into your offhand with a single click.",
            Category.PLAYER);
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        if (player == null || im == null) return;

        // Only meaningful while a container/inventory screen is open.
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return;
        Slot focused = ((HandledScreenAccessor) screen).lucid$getFocusedSlot();
        if (focused == null || !focused.hasStack()) return;
        if (!focused.getStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        // Offhand already armed — swapping two totems is a no-op loop.
        ItemStack offhand = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.OFFHAND);
        if (offhand.isOf(Items.TOTEM_OF_UNDYING)) return;

        if (requireSneak.isEnabled() && !client.options.sneakKey.isPressed()) return;

        // One packet: swap the hovered handler slot with the offhand
        // (SWAP button 40 = vanilla's offhand-swap action, same as F).
        im.clickSlot(player.currentScreenHandler.syncId, focused.id, 40,
            SlotActionType.SWAP, (PlayerEntity) player);

    }
}
