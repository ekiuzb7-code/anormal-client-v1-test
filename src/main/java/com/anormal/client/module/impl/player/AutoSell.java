package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.EnumSetting;
import com.anormal.client.lucid.IntSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.lucid.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.dialog.DialogScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * AutoSell — DonutSMP {@code /sell} GUI automation (glazed design).
 *
 * <h3>How it works</h3>
 * Reworked after the open-source glazed mc's AutoSell/GlazedSell design.
 * While a sell run is active the module:
 *   1. sends {@code /sell} in chat (the server opens a chest-style sell GUI),
 *   2. waits for the player's {@code currentScreenHandler} to become a
 *      {@link GenericContainerScreenHandler} (a generic 9xN chest menu — the
 *      same detection glazed uses; any chest the /sell command opens matches),
 *   3. shift-clicks ({@link SlotActionType#QUICK_MOVE}) every stack matching
 *      the Mode/Items filter from the player-inventory portion of the menu
 *      into the GUI — one click per Delay ticks so the server's slot-sync
 *      keeps up,
 *   4. reserves the GUI's bottom 9-slot row for buttons: deposits only go
 *      into the "usable" area ({@code rows*9 - 9}), and when that area fills
 *      up the module closes the GUI and re-opens {@code /sell} for the next
 *      batch (glazed's needsReopen batching),
 *   5. once nothing matching is left, clicks the GUI's confirm/sell button
 *      if one is present (green-pane heuristics below), then closes the GUI
 *      — DonutSMP finalises the sale on close,
 *   6. watches for the 1.21.6+ server confirmation dialog: if the server
 *      pops a {@link DialogScreen} ("Sell these items?" Yes/No), the module
 *      programmatically presses the yes/confirm button widget — see below.
 *
 * <h3>The screen-button pressing mechanism (the glazed trick)</h3>
 * Server dialogs can't be clicked with container slot packets — their Yes/No
 * buttons are GUI widgets, not inventory slots. {@link #pressYes(List)}
 * walks the dialog screen's element tree ({@link ParentElement#children()}),
 * finds any {@link PressableWidget} whose label matches yes/confirm/accept/
 * sell/ok (skipping any labelled exactly "no" or containing cancel/decline —
 * the exact-"no" match avoids false hits like "not enough"), and invokes the
 * vanilla press handler {@code onPress} on it directly. Real client-side
 * press: the widget's own action runs exactly as if the user clicked it.
 *
 * <h3>Confirm-button heuristics (glazed's GlazedSell)</h3>
 * A GUI stack counts as the sell/confirm button when it is a lime/green
 * stained glass pane or lime/green dye, or its display name contains
 * yes/confirm/accept/sell — unless it is a decline button in disguise
 * (name exactly "no" or containing cancel/decline). Clicked with a plain
 * {@link SlotActionType#PICKUP} left-click, exactly like a player would.
 *
 * <h3>Real actions only</h3>
 * Everything is what a player could do: {@code sendChatCommand("sell")},
 * {@code clickSlot} QUICK_MOVE/PICKUP clicks, {@code closeScreen()}, and
 * vanilla widget presses. No forged packets.
 *
 * <h3>Trigger conditions</h3>
 * Default (glazed semantics): enabling the module performs ONE sell run and
 * disables itself when finished ("All items sold"). With <b>Auto Trigger</b>
 * (a lucid-only extra) the module instead stays enabled and watches the
 * inventory, starting a run whenever at least <b>Trigger Stacks</b> matching
 * stacks have accumulated.
 *
 * <h3>State machine</h3>
 * <pre>
 *   WATCH/START ──► OPENING ──► DEPOSIT ──(everything deposited)──► CONFIRM ──► DIALOG ──► done
 *                      ▲            │
 *                      └── REOPEN ◄─┘  (usable area full, or GUI closed while matching
 *                                      items remain: close + /sell for the next batch)
 * </pre>
 * Each waiting state has a timeout that aborts with a chat line so the
 * module never gets stuck (glazed would retry forever; lucid adds the caps).
 *
 * Settings:
 *   Toggle Key, Mode, Items, Delay, Auto Trigger, Trigger Stacks,
 *   Only While Idle.
 */
public class AutoSell extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable auto-sell. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final EnumSetting mode = addSetting(
        new EnumSetting("Mode", "Whether the Items list is a whitelist (sell only these) or a blacklist (sell everything else).",
            new String[]{"Whitelist", "Blacklist"}, "Whitelist"));
    private final StringSetting items = addSetting(
        new StringSetting("Items", "Comma-separated item ids to sell, e.g. \"sea_pickle, minecraft:cobblestone\". Read per the Mode setting.",
            "sea_pickle", 512));
    private final IntSetting delay = addSetting(
        new IntSetting("Delay", "Ticks to wait between actions (deposits and re-opens) so the server's slot-sync keeps up.",
            0, 20, 1, 1));
    private final BooleanSetting autoTrigger = addSetting(
        new BooleanSetting("Auto Trigger", "Off: enabling performs one sell run and disables itself when done (glazed semantics). On: stay enabled and auto-start a run once enough matching stacks pile up.",
            false));
    private final IntSetting triggerStacks = addSetting(
        new IntSetting("Trigger Stacks", "Matching stacks that must be in the inventory before an Auto Trigger run fires.",
            1, 36, 1, 1));
    private final BooleanSetting onlyWhileIdle = addSetting(
        new BooleanSetting("Only While Idle", "Only auto-trigger when no screen is already open (anti-conflict).",
            true));

    // ── State machine ────────────────────────────────────────────
    private enum State { WATCH, START, OPENING, DEPOSIT, REOPEN, CONFIRM, DIALOG }
    private State state = State.WATCH;
    private int ticksInState = 0;

    /** Per-run flags. */
    private boolean confirmClicked = false;
    private boolean dialogPressed = false;
    private int reopenCount = 0;

    /** Cached parse of the Items setting (rebuilt when the string changes). */
    private String itemFilterRaw = null;
    private Set<Item> itemFilter = new HashSet<>();

    /** Initial cooldown after enabling, before the first run (glazed: 20). */
    private static final int START_COOLDOWN = 20;
    /** Ticks to wait for the sell GUI to open after sending /sell. */
    private static final int GUI_OPEN_TIMEOUT = 40;
    /** Extra settle ticks after the GUI opens, so the server syncs its slots. */
    private static final int GUI_SETTLE = 10;
    /** Ticks to wait after clicking a confirm button before closing the GUI. */
    private static final int AFTER_CONFIRM_WAIT = 5;
    /** Ticks to wait for a confirmation dialog after the GUI closes. */
    private static final int DIALOG_SETTLE = 10;
    /** Hard cap before giving up on a dialog we can't press (leave it to the user). */
    private static final int DIALOG_TIMEOUT = 60;
    /** Safety cap on close/re-open batches (lucid guard; glazed retries forever). */
    private static final int MAX_REOPENS = 8;

    /** Toggle key edge detection. */
    private boolean wasKeyDown = false;

    public AutoSell() {
        super("AutoSell",
            "Automatically sells whitelisted items through the DonutSMP /sell GUI — deposits, clicks the confirm button and answers the server dialog.",
            Category.MISC);
    }

    @Override
    public void onEnable() {
        resetRun();
        wasKeyDown = false;
        // Glazed semantics: a manual enable starts one run after a short
        // cooldown. Auto Trigger instead idles in WATCH until the inventory
        // fills up with matching stacks.
        state = autoTrigger.isEnabled() ? State.WATCH : State.START;
        ticksInState = 0;
    }

    @Override
    public void onDisable() {
        // If we left a sell GUI open, close it (which also finalizes the sale
        // on DonutSMP — same as the CONFIRM → close step of a finished run).
        
        if (mc != null && mc.player != null && mc.currentScreen instanceof HandledScreen) {
            mc.player.closeScreen();
        }
        state = State.WATCH;
        resetRun();
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

        ticksInState++;

        switch (state) {
            case WATCH -> tickWatch(mc, player);
            case START -> tickStart(mc, player);
            case OPENING -> tickOpening(player);
            case DEPOSIT -> tickDeposit(mc, player);
            case REOPEN -> tickReopen(player);
            case CONFIRM -> tickConfirm(mc, player);
            case DIALOG -> tickDialog();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  State: WATCH — auto-trigger idle (lucid extra, glazed default is one-shot)
    // ════════════════════════════════════════════════════════════
    private void tickWatch(MinecraftClient mc, ClientPlayerEntity player) {
        // Auto Trigger flipped off while watching → behave like a manual enable.
        if (!autoTrigger.isEnabled()) {
            beginRun(player);
            return;
        }
        // Don't trigger while a screen is already open if the user wants to
        // avoid conflicts with manual inventory work.
        if (onlyWhileIdle.isEnabled() && mc.currentScreen != null) return;

        int matching = countMatchingStacks(player);
        if (matching >= triggerStacks.get()) {

            beginRun(player);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  State: START — glazed's 20-tick activation cooldown, then one run.
    // ════════════════════════════════════════════════════════════
    private void tickStart(MinecraftClient mc, ClientPlayerEntity player) {
        if (ticksInState < START_COOLDOWN) return;
        beginRun(player);
    }

    /** Begin a sell run: use the GUI if one is already open, else send /sell. */
    private void beginRun(ClientPlayerEntity player) {
        if (sellGuiOpen(player)) {
            state = State.DEPOSIT;
        } else {
            player.networkHandler.sendChatCommand("sell");
            state = State.OPENING;
        }
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════
    //  State: OPENING — wait for the sell GUI (glazed polls the same way,
    //  but retries forever; lucid adds an abort timeout).
    // ════════════════════════════════════════════════════════════
    private void tickOpening(ClientPlayerEntity player) {
        if (sellGuiOpen(player) && ticksInState >= GUI_SETTLE) {
            state = State.DEPOSIT;
            ticksInState = 0;
            return;
        }
        if (ticksInState >= GUI_OPEN_TIMEOUT) {
            abort("Sell GUI didn't open");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  State: REOPEN — the GUI closed while matching items remain
    //  (batch full / closed externally). Wait Delay ticks, then /sell again.
    // ════════════════════════════════════════════════════════════
    private void tickReopen(ClientPlayerEntity player) {
        if (ticksInState <= delay.get()) return;
        player.networkHandler.sendChatCommand("sell");
        state = State.OPENING;
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════
    //  State: DEPOSIT — glazed's core loop: one QUICK_MOVE deposit per Delay
    //  ticks; when the usable area fills, close + reopen for the next batch.
    // ════════════════════════════════════════════════════════════
    private void tickDeposit(MinecraftClient mc, ClientPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            // GUI vanished mid-run (closed by server or player). If matching
            // items remain, glazed simply re-opens /sell — do the same.
            if (countMatchingStacks(player) > 0) {
                if (reopenCount < MAX_REOPENS) {
                    reopenCount++;
                    state = State.REOPEN;
                    ticksInState = 0;
                } else {
                    abort("Sell GUI kept closing");
                }
            } else {
                // Sale already finalised by the close — go watch for a dialog.
                enterDialog(player);
            }
            return;
        }

        // One action per Delay+1 ticks (glazed's delayCounter cadence).
        if (ticksInState <= delay.get()) return;
        ticksInState = 0;

        // Usable area full → glazed's needsReopen: close the GUI (this batch
        // sells on close) and /sell again for the next one — unless nothing
        // matching is left, in which case the run is finishing instead.
        if (firstEmptyUsableSlot(handler) < 0) {
            boolean moreLeft = countMatchingStacks(player) > 0;
            if (moreLeft && reopenCount >= MAX_REOPENS) {
                abort("Sell GUI kept filling up");
            } else if (moreLeft) {
                reopenCount++;
                player.closeScreen();
                state = State.REOPEN;
                ticksInState = 0;
            } else {
                state = State.CONFIRM; // everything deposited — finish the sale
            }
            return;
        }

        // Shift-click the next matching stack from the player-inventory
        // portion of the menu (slots after the container's own rows).
        int top = containerSlots(handler);
        for (int slot = top; slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.slots.get(slot).getStack();
            if (stack.isEmpty() || !shouldSell(stack.getItem())) continue;

            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, player);
            return; // one deposit per delay period
        }

        // Nothing matching left to deposit — confirm & finish.
        state = State.CONFIRM;
    }

    // ════════════════════════════════════════════════════════════
    //  State: CONFIRM — glazed's GlazedSell clickConfirm: if the GUI has a
    //  confirm/sell button stack, PICKUP-click it once, wait a beat, close.
    //  GUIs without a button sell on close, so closing is enough for them.
    // ════════════════════════════════════════════════════════════
    private void tickConfirm(MinecraftClient mc, ClientPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
            enterDialog(player); // GUI already gone — sale finalised on close
            return;
        }

        if (!confirmClicked) {
            int slot = findConfirmSlot(handler);
            if (slot >= 0) {
                mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, player);
                confirmClicked = true;
                return;
            }
            // No confirm button — sell-on-close GUI.
            enterDialog(player);
            return;
        }

        if (ticksInState >= AFTER_CONFIRM_WAIT) {
            enterDialog(player);
        }
    }

    /** Close the GUI and start watching for the server confirmation dialog. */
    private void enterDialog(ClientPlayerEntity player) {
        player.closeScreen();
        state = State.DIALOG;
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════
    //  State: DIALOG — glazed's clickDialogYes: press the yes/confirm
    //  button widget on any 1.21.6+ server dialog that pops up.
    // ════════════════════════════════════════════════════════════
    private void tickDialog() {
        if (mc.currentScreen instanceof DialogScreen<?> dialog) {
            if (ticksInState >= DIALOG_TIMEOUT) {
                // A dialog we can't press (no matching label) — end the run
                // and leave answering it to the user.
                soldDone();
                return;
            }
            if (pressYes(dialog.children())) {
                dialogPressed = true;
            }
            return; // keep pressing until the dialog is dismissed
        }
        // Done once any dialog has been answered, or when none appears
        // within the settle window (not every sale pops one).
        if (dialogPressed || ticksInState >= DIALOG_SETTLE) {
            soldDone();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  GlazedSell helpers (ported to Yarn)
    // ════════════════════════════════════════════════════════════

    /** True while a generic chest menu (the /sell GUI) is the open handler. */
    private static boolean sellGuiOpen(ClientPlayerEntity player) {
        return player.currentScreenHandler instanceof GenericContainerScreenHandler;
    }

    /** Top-container slot count of the GUI (its own rows; player inv excluded). */
    private static int containerSlots(GenericContainerScreenHandler handler) {
        return handler.getRows() * 9;
    }

    /**
     * First empty slot in the GUI's usable deposit area — the container rows
     * minus the bottom 9-slot button row (glazed reserves it for buttons).
     * -1 when the usable area is full.
     */
    private static int firstEmptyUsableSlot(GenericContainerScreenHandler handler) {
        int limit = Math.min(Math.max(0, containerSlots(handler) - 9), handler.slots.size());
        for (int slot = 0; slot < limit; slot++) {
            if (handler.slots.get(slot).getStack().isEmpty()) return slot;
        }
        return -1;
    }

    /**
     * GlazedSell's confirm-button heuristic: green panes/dyes, or a display
     * name containing yes/confirm/accept/sell — but never a decline button
     * (name exactly "no" — exact so "not enough" doesn't match — or
     * cancel/decline).
     */
    private static boolean isConfirmButton(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.isOf(Items.LIME_STAINED_GLASS_PANE) || stack.isOf(Items.GREEN_STAINED_GLASS_PANE)
            || stack.isOf(Items.LIME_DYE) || stack.isOf(Items.GREEN_DYE)) {
            return true;
        }
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        if (name.equals("no") || name.contains("cancel") || name.contains("decline")) return false;
        return name.contains("yes") || name.contains("confirm")
            || name.contains("accept") || name.contains("sell");
    }

    /** Find the GUI's confirm/sell button slot in the container's own rows. */
    private static int findConfirmSlot(GenericContainerScreenHandler handler) {
        int top = Math.min(containerSlots(handler), handler.slots.size());
        for (int slot = 0; slot < top; slot++) {
            if (isConfirmButton(handler.slots.get(slot).getStack())) return slot;
        }
        return -1;
    }

    /**
     * GlazedSell's pressMatching, in Yarn: recursively walk a dialog screen's
     * element tree and press the first widget labelled as a confirmation.
     * {@code onPress(null)} — vanilla buttons ignore the input argument, and
     * the occasional button that doesn't is swallowed by the catch.
     */
    private static boolean pressYes(List<? extends Element> elements) {
        for (Element element : elements) {
            if (element instanceof PressableWidget widget) {
                String label = widget.getMessage().getString().toLowerCase(Locale.ROOT);
                if (label.equals("no") || label.contains("cancel") || label.contains("decline")) continue;
                if (label.contains("yes") || label.contains("confirm") || label.contains("accept")
                    || label.contains("sell") || label.contains("ok")) {
                    try {
                        widget.onPress(null);
                        return true;
                    } catch (Throwable ignored) {
                        return false;
                    }
                }
            }
            if (element instanceof ParentElement parent && pressYes(parent.children())) {
                return true;
            }
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════
    //  Item filter (glazed's ItemListSetting adapted to a string setting)
    // ════════════════════════════════════════════════════════════

    /** Whitelist/blacklist test per the Mode setting (glazed's shouldSellItem). */
    private boolean shouldSell(Item item) {
        boolean listed = getItemFilter().contains(item);
        return "Whitelist".equals(mode.get()) ? listed : !listed;
    }

    /** Number of matching stacks currently in the player inventory (0–35). */
    private int countMatchingStacks(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && shouldSell(stack.getItem())) count++;
        }
        return count;
    }

    /** Parse the Items setting into an Item set (cached until the string changes). */
    private Set<Item> getItemFilter() {
        String raw = items.get();
        if (itemFilterRaw == null || !raw.equals(itemFilterRaw)) {
            Set<Item> parsed = new HashSet<>();
            for (String token : raw.split("[,\\s]+")) {
                if (token.isEmpty()) continue;
                Identifier id = parseItemId(token);
                if (id == null) continue;
                Item item = Registries.ITEM.get(id);
                if (item != null && item != Items.AIR) parsed.add(item);
            }
            itemFilterRaw = raw;
            itemFilter = parsed;
        }
        return itemFilter;
    }

    /** FakeItem's id-parse idiom: optional namespace, null when unparseable. */
    private static Identifier parseItemId(String token) {
        try {
            int colon = token.indexOf(':');
            if (colon >= 0) {
                return Identifier.of(token.substring(0, colon), token.substring(colon + 1));
            }
            return Identifier.of(token);
        } catch (Throwable t) {
            return null;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Run lifecycle
    // ════════════════════════════════════════════════════════════

    /** glazed's "All items sold." + toggle(): auto-trigger keeps the module on. */
    private void soldDone() {

        if (autoTrigger.isEnabled()) {
            resetRun();
            state = State.WATCH;
            ticksInState = 0;
        } else {
            setEnabled(false);
        }
    }

    private void abort(String reason) {

        if (autoTrigger.isEnabled()) {
            resetRun();
            state = State.WATCH;
            ticksInState = 0;
        } else {
            setEnabled(false);
        }
    }

    private void resetRun() {
        confirmClicked = false;
        dialogPressed = false;
        reopenCount = 0;
    }
}
