package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoTotem extends Module {
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", "Opens inventory to grab a totem", true);
    public final BooleanSetting silentOpen = new BooleanSetting("Silent Open", "Moves the totem without showing the GUI", true);
    public final NumberSetting silentMoveDelay = new NumberSetting("Silent Move Delay", "Ticks waited before a silent move", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting closeInventory = new BooleanSetting("Close Inventory", "Closes inventory opened by AutoTotem", true);
    public final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", "Only equips while inventory is open", false);
    public final BooleanSetting combatOnly = new BooleanSetting("Combat Only", "Only equips after recent danger", true);
    public final NumberSetting activationDelay = new NumberSetting("Activation Delay", "Danger ticks before equipping", 10.0, 0.0, 100.0, 1.0);
    public final BooleanSetting refill = new BooleanSetting("Refill", "Refills a consumed main-hand totem", true);
    public final BooleanSetting randomSlot = new BooleanSetting("Random Slot", "Picks a random totem instead of the first", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks before equipping a totem", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting extraRandomization = new BooleanSetting("Extra Randomization", "Adds human-like timing variance", true);
    public final BooleanSetting showTotemCount = new BooleanSetting("Show Totem Count", "Shows remaining totems on screen", true);

    private final Random random = new Random();
    private int cooldown = 0;
    private int danger = 0;
    private int silentWait = 0;
    private boolean openedByUs = false;

    public AutoTotem() {
        super("AutoTotem", "Automatically replaces depleted Totems of Undying in offhand", Category.UZNY11);
        addSetting(openInventory);
        addSetting(silentOpen);
        addSetting(silentMoveDelay);
        addSetting(closeInventory);
        addSetting(inventoryOnly);
        addSetting(combatOnly);
        addSetting(activationDelay);
        addSetting(refill);
        addSetting(randomSlot);
        addSetting(delay);
        addSetting(extraRandomization);
        addSetting(showTotemCount);

    }

    @Override
    public void onDisable() {
        cooldown = 0;
        danger = 0;
        silentWait = 0;
        openedByUs = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.hurtTime > 0 || mc.player.getAttacker() != null) danger++;
        else if (danger > 0) danger--;
        if (combatOnly.isEnabled() && danger < activationDelay.getValue().intValue()) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        boolean offhandTotem = mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
        if (!offhandTotem) {
            moveTotem();
            return;
        }
        if (openedByUs && closeInventory.isEnabled() && mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
            openedByUs = false;
        }
        if (refill.isEnabled()) refillMainHand();
    }

    private void moveTotem() {
        List<Integer> slots = totemSlots();
        if (slots.isEmpty()) return;
        if (inventoryOnly.isEnabled() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (openInventory.isEnabled() && mc.currentScreen == null && !silentOpen.isEnabled()) {
            mc.setScreen(new InventoryScreen(mc.player));
            openedByUs = true;
            return;
        }
        if (silentOpen.isEnabled() && mc.currentScreen == null) {
            if (silentWait < silentMoveDelay.getValue().intValue()) {
                silentWait++;
                return;
            }
            silentWait = 0;
        }
        int src = randomSlot.isEnabled() ? slots.get(random.nextInt(slots.size())) : slots.get(0);
        int sync = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(sync, src, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 45, 0, SlotActionType.PICKUP, mc.player);
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(sync, src, 0, SlotActionType.PICKUP, mc.player);
        }
        int jitter = extraRandomization.isEnabled() ? random.nextInt(6) : random.nextInt(2);
        cooldown = delay.getValue().intValue() + jitter;
    }

    private void refillMainHand() {
        if (!mc.player.getMainHandStack().isEmpty()) return;
        List<Integer> slots = totemSlots();
        if (slots.isEmpty()) return;
        int src = randomSlot.isEnabled() ? slots.get(random.nextInt(slots.size())) : slots.get(0);
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, src,
                mc.player.getInventory().getSelectedSlot(), SlotActionType.SWAP, mc.player);
        cooldown = delay.getValue().intValue();
    }

    private List<Integer> totemSlots() {
        List<Integer> out = new ArrayList<>();
        for (int slot = 9; slot < 45; slot++) {
            int idx = slot < 36 ? slot : slot - 36;
            ItemStack s = mc.player.getInventory().getStack(idx);
            if (!s.isEmpty() && s.getItem() == Items.TOTEM_OF_UNDYING) out.add(slot);
        }
        return out;
    }

    private int totemCount() {
        int n = mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING ? 1 : 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) n++;
        }
        return n;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!showTotemCount.isEnabled() || mc.player == null) return;
        String text = "Totems: " + totemCount();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        context.drawText(mc.textRenderer, text, w / 2 - mc.textRenderer.getWidth(text) / 2, h / 2 + 12, 0xFFFF5555, true);
    }
}
