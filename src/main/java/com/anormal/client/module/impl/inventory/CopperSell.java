package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class CopperSell extends Module {
    public final BooleanSetting autoCraft = new BooleanSetting("AutoCraft", "Converts Waxed Copper Blocks into 9 ingots via crafting", true);
    public final BooleanSetting autoOpenSell = new BooleanSetting("AutoOpenSell", "Automatically sends /sell command when ingots are ready", true);
    public final BooleanSetting autoFillSell = new BooleanSetting("AutoFillSell", "Quick-moves copper ingots into the /sell container", true);
    public final BooleanSetting manualConfirm = new BooleanSetting("ManualConfirm", "Leaves final confirm/sell button for user to click manually", true);

    public final ModeSetting sellCommand = new ModeSetting("Sell Command", "Command used to open the sell interface", "/sell", "/sell", "/shop sell", "/sellgui", "/sellall");
    public final BooleanSetting includeNormalCopper = new BooleanSetting("Normal Copper", "Also uncrafts standard unwaxed copper blocks", true);
    public final BooleanSetting includeAllWaxed = new BooleanSetting("All Waxed", "Also uncrafts exposed, weathered and oxidized waxed copper", true);
    public final NumberSetting minIngotsToSell = new NumberSetting("Min Ingots", "Minimum ingots ready in inventory before opening /sell", 64.0, 9.0, 576.0, 9.0);
    public final NumberSetting craftDelay = new NumberSetting("Craft Delay", "Ticks between crafting clicks", 2.0, 0.0, 10.0, 1.0);
    public final NumberSetting fillDelay = new NumberSetting("Fill Delay", "Ticks between moving items to sell chest", 1.0, 0.0, 10.0, 1.0);
    public final BooleanSetting hudCounter = new BooleanSetting("HUD Counter", "Renders live copper status HUD on screen", true);
    public final BooleanSetting chatAlerts = new BooleanSetting("Chat Alerts", "Prints status notifications in chat", true);

    public enum State {
        IDLE,
        CRAFTING,
        WAITING_FOR_SELL_GUI,
        FILLING_SELL_GUI,
        READY_FOR_CONFIRM
    }

    private State state = State.IDLE;
    private int timer = 0;
    private int waitTimeout = 0;

    public CopperSell() {
        super("CopperSell", "Automates uncrafting Waxed Copper Blocks into 9x ingots and filling /sell", Category.INVENTORY);
        addSetting(autoCraft);
        addSetting(autoOpenSell);
        addSetting(autoFillSell);
        addSetting(manualConfirm);
        addSetting(sellCommand);
        addSetting(includeNormalCopper);
        addSetting(includeAllWaxed);
        addSetting(minIngotsToSell);
        addSetting(craftDelay);
        addSetting(fillDelay);
        addSetting(hudCounter);
        addSetting(chatAlerts);
    }

    @Override
    public void onEnable() {
        state = State.IDLE;
        timer = 0;
        waitTimeout = 0;
        log("§aEnabled! Ready to process copper blocks and /sell.");
    }

    @Override
    public void onDisable() {
        state = State.IDLE;
        timer = 0;
        waitTimeout = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        int blocks = countCopperBlocks();
        int ingots = countCopperIngots();

        switch (state) {
            case IDLE -> {
                if (autoCraft.isEnabled() && blocks > 0 && hasEmptyInventorySlot()) {
                    state = State.CRAFTING;
                    log("Found " + blocks + " copper blocks (" + (blocks * 9) + " ingots). Starting crafting...");
                } else if (autoOpenSell.isEnabled() && ingots >= minIngotsToSell.getValue()) {
                    openSellGui();
                }
            }

            case CRAFTING -> {
                // Done crafting blocks or inventory completely full
                if (blocks == 0 || !hasEmptyInventorySlot()) {
                    if (mc.currentScreen instanceof InventoryScreen) {
                        mc.setScreen(null);
                    } else if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                        mc.player.closeHandledScreen();
                    }
                    log("Crafting finished! Inventory has " + ingots + " copper ingots.");
                    if (autoOpenSell.isEnabled() && ingots >= minIngotsToSell.getValue()) {
                        openSellGui();
                    } else {
                        state = State.IDLE;
                    }
                    return;
                }

                // If not in inventory or crafting table, open inventory screen
                if (mc.currentScreen == null) {
                    mc.setScreen(new InventoryScreen(mc.player));
                    timer = craftDelay.getValue().intValue();
                    return;
                }

                ScreenHandler handler = mc.player.currentScreenHandler;
                if (handler instanceof PlayerScreenHandler playerHandler) {
                    processCrafting(playerHandler.syncId, 0, 1, 9, 44);
                } else if (handler instanceof CraftingScreenHandler craftHandler) {
                    processCrafting(craftHandler.syncId, 0, 1, 10, 45);
                }
            }

            case WAITING_FOR_SELL_GUI -> {
                // Detect container screen opened
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                    state = State.FILLING_SELL_GUI;
                    timer = fillDelay.getValue().intValue();
                    log("Sell container opened. Moving copper ingots...");
                    return;
                }
                waitTimeout--;
                if (waitTimeout <= 0) {
                    log("§cTimed out waiting for /sell container.");
                    state = State.IDLE;
                }
            }

            case FILLING_SELL_GUI -> {
                // If container closed early
                if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) {
                    state = State.IDLE;
                    return;
                }

                ScreenHandler handler = mc.player.currentScreenHandler;
                int totalSlots = handler.slots.size();
                int invStart = Math.max(0, totalSlots - 36);

                // Find first copper ingot in player inventory slots of the open container
                int ingotSlot = -1;
                for (int i = invStart; i < totalSlots; i++) {
                    ItemStack s = handler.getSlot(i).getStack();
                    if (!s.isEmpty() && s.isOf(Items.COPPER_INGOT)) {
                        ingotSlot = i;
                        break;
                    }
                }

                if (ingotSlot != -1) {
                    mc.interactionManager.clickSlot(handler.syncId, ingotSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
                    timer = fillDelay.getValue().intValue();
                } else {
                    // All ingots in inventory transferred to /sell
                    if (manualConfirm.isEnabled()) {
                        state = State.READY_FOR_CONFIRM;
                        log("§aAll copper ingots placed in /sell! Please click Confirm to finalize.");
                    } else {
                        state = State.IDLE;
                    }
                }
            }

            case READY_FOR_CONFIRM -> {
                // When container is closed by user confirming or exiting
                if (mc.player.currentScreenHandler == mc.player.playerScreenHandler || mc.currentScreen == null) {
                    state = State.IDLE;
                    if (countCopperBlocks() > 0 && hasEmptyInventorySlot()) {
                        log("Sale confirmed! Processing next batch of copper blocks...");
                        state = State.CRAFTING;
                    } else {
                        log("§aCopperSell operation finished successfully!");
                    }
                }
            }
        }
    }

    private void processCrafting(int syncId, int resultSlot, int craftGridSlot, int invStart, int invEnd) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        ItemStack resultStack = handler.getSlot(resultSlot).getStack();

        // 1. If result slot has crafted copper ingots, shift click to collect all into inventory
        if (!resultStack.isEmpty() && resultStack.isOf(Items.COPPER_INGOT)) {
            mc.interactionManager.clickSlot(syncId, resultSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
            timer = craftDelay.getValue().intValue();
            return;
        }

        // 2. Check crafting input slot
        ItemStack gridStack = handler.getSlot(craftGridSlot).getStack();
        if (gridStack.isEmpty()) {
            // Find a copper block in player inventory
            int blockSlot = findCopperBlockSlot(handler, invStart, invEnd);
            if (blockSlot != -1) {
                // Pick up copper block and put in crafting grid slot 1
                mc.interactionManager.clickSlot(syncId, blockSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, craftGridSlot, 0, SlotActionType.PICKUP, mc.player);
                if (!handler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(syncId, blockSlot, 0, SlotActionType.PICKUP, mc.player);
                }
                timer = craftDelay.getValue().intValue();
            }
        } else if (!isCopperBlock(gridStack)) {
            // Unexpected item in crafting slot, return it to inventory
            mc.interactionManager.clickSlot(syncId, craftGridSlot, 0, SlotActionType.QUICK_MOVE, mc.player);
            timer = craftDelay.getValue().intValue();
        }
    }

    private int findCopperBlockSlot(ScreenHandler handler, int start, int end) {
        for (int i = start; i <= end; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (isCopperBlock(stack)) {
                return i;
            }
        }
        return -1;
    }

    private void openSellGui() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        String cmd = sellCommand.getValue().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        mc.player.networkHandler.sendChatCommand(cmd);
        state = State.WAITING_FOR_SELL_GUI;
        waitTimeout = 60; // 3 seconds timeout
        log("Executing command /" + cmd + " to open sell menu...");
    }

    public boolean isCopperBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == Items.WAXED_COPPER_BLOCK) return true;
        if (includeAllWaxed.isEnabled()) {
            if (item == Items.WAXED_EXPOSED_COPPER || item == Items.WAXED_WEATHERED_COPPER || item == Items.WAXED_OXIDIZED_COPPER) {
                return true;
            }
        }
        if (includeNormalCopper.isEnabled()) {
            if (item == Items.COPPER_BLOCK || item == Items.EXPOSED_COPPER || item == Items.WEATHERED_COPPER || item == Items.OXIDIZED_COPPER) {
                return true;
            }
        }
        return false;
    }

    private int countCopperBlocks() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (isCopperBlock(s)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private int countCopperIngots() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.isOf(Items.COPPER_INGOT)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private boolean hasEmptyInventorySlot() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void log(String message) {
        if (chatAlerts.isEnabled() && mc.inGameHud != null) {
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal("§6[CopperSell] §f" + message));
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!hudCounter.isEnabled() || mc.player == null || mc.getWindow() == null) return;

        int blocks = countCopperBlocks();
        int ingots = countCopperIngots();
        if (blocks == 0 && ingots == 0 && state == State.IDLE) return;

        int x = 8;
        int y = mc.getWindow().getScaledHeight() - 56;

        int stateColor = switch (state) {
            case CRAFTING -> 0xFFFFAA00;
            case WAITING_FOR_SELL_GUI, FILLING_SELL_GUI -> 0xFF00E5FF;
            case READY_FOR_CONFIRM -> 0xFF55FF55;
            case IDLE -> 0xFFAAAAAA;
        };

        String stateName = switch (state) {
            case CRAFTING -> "CRAFTING";
            case WAITING_FOR_SELL_GUI -> "OPENING /SELL";
            case FILLING_SELL_GUI -> "FILLING /SELL";
            case READY_FOR_CONFIRM -> "READY FOR CONFIRM";
            case IDLE -> "IDLE";
        };

        String line1 = "§6CopperSell §8[§r" + stateName + "§8]";
        String line2 = "§e" + blocks + " §7Blocks (§a" + (blocks * 9) + "§7 ingots) | §b" + ingots + "§7 Ingot(s)";

        int w = Math.max(mc.textRenderer.getWidth(line1), mc.textRenderer.getWidth(line2)) + 12;
        int h = 26;

        RenderUtils.fill(context, x, y, x + w, y + h, ColorUtils.rgba(12, 14, 20, 220));
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, stateColor);

        RenderUtils.drawText(context, mc.textRenderer, line1, x + 6, y + 4, stateColor, true);
        RenderUtils.drawText(context, mc.textRenderer, line2, x + 6, y + 15, 0xFFDDDDDD, true);
    }
}
