package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ghost-slot tracking + container tracking for the Fake Item module.
 *
 * <p>The local {@link ScreenHandler#onSlotClick} performs the predicted slot
 * move BEFORE a {@code ClickSlotC2SPacket} goes out. We hook at its TAIL to
 * rescan the player's own inventory + cursor + open container and update the
 * module's ghost slot/state accordingly:
 * <ul>
 *   <li>If the cursor now holds the fake → {@code cursorIsGhost=true},
 *       {@code ghostSlot=-1} (drag in progress; fake is in transit).</li>
 *   <li>Else if the player's inventory contains the fake at some slot
 *       {@code i} → {@code ghostSlot=i} (real local move done; container
 *       tracking is cleared).</li>
 *   <li>Else if the OPEN container (chest / shulker / hopper GUI) contains the
 *       fake at some non-player slot {@code s} → record the container slot +
 *       syncId via {@link FakeItem#setGhostContainerLocation}; the outgoing
 *       click was killed by {@link ClientConnectionMixin} so the server has
 *       no record of the move (the chest truthfully shows empty for that slot),
 *       but {@link FakeItem#reassertGhost()} re-places the fake there every
 *       tick so the user sees it in the chest GUI while it's open.</li>
 *   <li>Else (cursor / player-inv / container all empty) → the fake was
 *       dropped outside the GUI or thrown. Per the user's earlier "snap-back"
 *       choice we restore the fake to its {@code lastGhostSlot} via
 *       {@link FakeItem#snapBackGhost()}.</li>
 * </ul>
 *
 * <p>{@link PlayerScreenHandler} does NOT override {@code onSlotClick} (it only
 * overrides {@code onContentChanged}/{@code quickMove}/{@code onClosed}), so a
 * single {@link Inject} on {@link ScreenHandler#onSlotClick} catches every
 * player-inventory click. Other screen handlers' clicks also pass through this
 * base method, so the same hook tracks cursor state across chest/etc screens
 * too (necessary so the outgoing-click suppressor in {@code ClientConnectionMixin}
 * knows when the cursor carries the fake).
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Inject(
        method = "onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V",
        at = @At("RETURN")
    )
    private void lucid$trackGhostAfterClick(int slot, int button,
                                            SlotActionType actionType,
                                            PlayerEntity player,
                                            CallbackInfo ci) {
        try {
            // FIRST THING: log unconditionally so the next test run proves
            // whether this Mixin fires at all. Earlier instrumenting gated
            // the ENTER log behind `fake.isEmpty()`; since a decremented
            // fakeStack was isEmpty()-true, the gate swallowed the log and
            // convinced us the Mixin wasn't applying. With that fixed we
            // expect this line to log on every click while Fake Item is
            // active. The unconditional log is cheap (single String concat
            // per click) and gated behind FakeItem.DEBUG.
            if (FakeItem.DEBUG) {
                FakeItem.dbgLight("onSlotClick-RAW",
                    "slot=" + slot + " btn=" + button + " action=" + actionType
                    + " handler=" + ((ScreenHandler) (Object) this).getClass().getSimpleName()
                    + " syncId=" + ((ScreenHandler) (Object) this).syncId
                    + " isActive=" + FakeItem.isActive()
                    + " isDedicated=" + FakeItem.isDedicatedServer()
                    + " fakeIsEmpty=" + FakeItem.getFakeStack().isEmpty());
            }

            if (!FakeItem.isActive()) return;
            // Tracking the ghost slot is pure DonutSMP-simulation state — on
            // singleplayer everything is real and we don't want a fake "ghost
            // slot" tracker messing with the legit clicks.
            if (!FakeItem.isDedicatedServer()) return;
            ItemStack fake = FakeItem.getFakeStack();
            if (fake.isEmpty()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null || player == null) return;
            if (client.player != player) return;

            // Trace every click that runs while the module is active on a
            // dedicated server — the snapshot line shows the prior state, the
            // post-click branches below show the new state. This is the
            // primary diagnostic for the "can't move items in inventory /
            // chest" symptom: we want to see whether the click-results scan
            // finds the fake on cursor / in player inv / in chest / nowhere.
            FakeItem.dbgLight("onSlotClick-ENTER",
                "slot=" + slot + " btn=" + button + " action=" + actionType
                + " handler=" + ((ScreenHandler) (Object) this).getClass().getSimpleName()
                + " syncId=" + ((ScreenHandler) (Object) this).syncId);

            // 1) Read the cursor of the OPEN screen handler. When the survival
            //    inventory is open this is playerScreenHandler; when a chest is
            //    open it's the chest handler. The cursor is per-handler.
            ItemStack cursor = ItemStack.EMPTY;
            try {
                cursor = ((ScreenHandler) (Object) this).getCursorStack();
            } catch (Throwable ignored) {}

            boolean cursorHoldsFake = FakeItem.stackEquals(cursor, fake);

            // A grant (bone / arrow / xp) on the cursor is a fake in transit too —
            // flag it so the outgoing-click suppressor (Case C) keeps the drag
            // clientside and the server can't reject-and-wipe it.
            boolean cursorHoldsAnyFake = cursorHoldsFake || FakeItem.isAnyFakeStack(cursor);

            // 2) Scan the player's PlayerInventory for the fake stack. Scan for
            //    the PRIMARY fake first (stackEquals — count-sensitive, matches
            //    the old behaviour exactly); separately note whether ANY tracked
            //    fake (a primary copy OR a storage-GUI grant like bones) lives in
            //    the grid, so the snap-back branch below never re-creates the
            //    primary while a grant is present.
            PlayerInventory inv = client.player.getInventory();
            int foundSlot = -1;
            boolean anyFakeInGrid = false;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s == null || s.isEmpty()) continue;
                if (FakeItem.stackEquals(s, fake)) {
                    if (foundSlot < 0) foundSlot = i;
                    anyFakeInGrid = true;
                } else if (FakeItem.isAnyFakeStack(s)) {
                    anyFakeInGrid = true;
                }
            }

            // 3) Update ghost state.
            if (cursorHoldsAnyFake) {
                // Fake is on cursor → drag in progress; the ghost slot is
                // conceptually "in transit". Remember the prior slot via the
                // lastGhostSlot already set when we picked it up. While
                // dragging it's neither in the player inv nor in a container
                // slot; stale container position (if any) is reconciled by the
                // truth-scan below, NOT cleared blindly here — other fake
                // copies may still sit in the chest.
                FakeItem.setCursorIsGhost(true);
                FakeItem.setGhostSlot(-1);
                FakeItem.dbg("onSlotClick", "CURSOR-HOLDS-FAKE slot=" + slot + " btn=" + button + " action=" + actionType);
            } else if (foundSlot >= 0) {
                // Fake landed in some player-inventory slot. Re-establish it
                // there (the chest GUI's bottom-grid slots are backed by
                // PlayerInventory, so the foundSlot scan above catches a
                // drop-back-into-inv too). Container memory is reconciled by
                // the truth-scan below — never cleared here, or fakes that
                // remain parked in the chest lose their protection.
                FakeItem.setCursorIsGhost(false);
                FakeItem.setGhostSlot(foundSlot);
                // Re-place defensively so the local slot exactly holds the
                // PRIMARY fake (in case vanilla split it / decremented it).
                inv.setStack(foundSlot, FakeItem.stackForInventorySlot(foundSlot));
                FakeItem.dbg("onSlotClick", "IN-PLAYER-INV slot=" + slot + " foundSlot=" + foundSlot + " action=" + actionType);
            } else {
                // Not on cursor and not in player inv. Check the OPEN screen
                // handler's NON-player-owned slots for the fake — found means
                // the user dropped it into a chest slot (or, for the player
                // screen handler, a craft-result / armor-slot-type inventory).
                ScreenHandler open = (ScreenHandler) (Object) this;
                int containerSlot = -1;
                try {
                    for (int i = 0; i < open.slots.size(); i++) {
                        Slot s = open.slots.get(i);
                        ItemStack st = s.getStack();
                        if (st == null || st.isEmpty() || !FakeItem.stackEquals(st, fake)) continue;
                        // Skip the chest GUI's bottom-grid player-inventory slots;
                        // those are backed by PlayerInventory, and any fake in
                        // them was already located by the scan above.
                        if (s.inventory instanceof PlayerInventory) continue;
                        containerSlot = i;
                        break;
                    }
                } catch (Throwable ignored) {}

                if (containerSlot >= 0) {
                    // Fake(s) landed in chest slots. Track the container so the
                    // outgoing-click + resync suppressors protect it and
                    // reassertGhost rewrites its slots every tick. Count EVERY
                    // parked copy so a close snap-backs them all to the player
                    // inventory instead of leaving one behind.
                    int containerCount = 0;
                    for (int i = 0; i < open.slots.size(); i++) {
                        Slot s2 = open.slots.get(i);
                        if (s2 == null || s2.inventory instanceof PlayerInventory) continue;
                        ItemStack st = s2.getStack();
                        if (st != null && !st.isEmpty() && FakeItem.stackEquals(st, fake)) {
                            containerCount++;
                        }
                    }
                    FakeItem.setCursorIsGhost(false);
                    FakeItem.setGhostSlot(-1);
                    FakeItem.setGhostContainerLocation(open.syncId, containerSlot, containerCount);
                    FakeItem.dbg("onSlotClick", "IN-CONTAINER slot=" + slot + " containerSlot=" + containerSlot
                        + " count=" + containerCount + " syncId=" + open.syncId + " action=" + actionType);
                } else {
                    // Fake is nowhere local: not on the cursor, not in the
                    // player grid, not in a chest slot. Two all-client features
                    // keep fakes out of the inventory WITHOUT them being lost:
                    //   - the fake was placed as a client block in the world
                    //     (isFakePlacedInWorld → ghostSlot == -1, no extras)
                    //   - a storage-GUI grant (bones/arrows/xp) lives in the
                    //     grid (getGhostSlots() non-empty — count-sensitive
                    //     stackEquals can't even see split grants)
                    // In either case re-creating the primary here would fight
                    // the user, so ONLY a genuinely-lost fake snaps back.
                    FakeItem.setCursorIsGhost(false);
                    if (FakeItem.isFakePlacedInWorld() || !FakeItem.getGhostSlots().isEmpty()
                        || FakeItem.isFakeInContainer()) {
                        FakeItem.dbg("onSlotClick", "NOT-FOUND → no snapback (fake placed in world, grants tracked, or parked in the open container) slot=" + slot + " action=" + actionType);
                    } else {
                        FakeItem.dbg("onSlotClick", "NOT-FOUND → SNAPBACK slot=" + slot + " action=" + actionType);
                        FakeItem.snapBackGhost();
                    }
                }
            }

            // 4) Truth-based container memory. Runs on EVERY click, not just when the
            //    inventory happened to be empty of fakes: record any container
            //    (non-player) slot that currently holds a fake copy, so the
            //    chest-close snap-back and close-detection cover ALL copies.
            //    This is ADDITIVE on purpose — a transient server wipe that
            //    emptied the slots must NOT clear our memory, or the per-tick
            //    heal would lose the fakes for good.
            ScreenHandler truth = (ScreenHandler) (Object) this;
            java.util.List<Integer> truthFakes = new java.util.ArrayList<>();
            try {
                for (int i = 0; i < truth.slots.size(); i++) {
                    Slot s = truth.slots.get(i);
                    if (s == null || s.inventory instanceof PlayerInventory) continue;
                    ItemStack st = s.getStack();
                    if (st != null && !st.isEmpty() && FakeItem.stackEquals(st, fake)) {
                        truthFakes.add(i);
                    }
                }
            } catch (Throwable ignored) {}
            FakeItem.addContainerFakeSlots(truth.syncId, truthFakes);
            FakeItem.dbg("onSlotClick", "CONTAINER-TRUTH found=" + truthFakes.size() + " syncId=" + truth.syncId);

            // If THIS click verifiably took a fake OUT of a container slot
            // (picked onto the cursor, moved into a player slot, shift-clicked
            // across the GUI, or thrown) while the fake is alive somewhere
            // else, unlink that exact slot from memory. When the fake is NOT
            // alive elsewhere, the slot was just wiped by a server resync —
            // keep the memory so the per-tick heal restores it (wiping flaky,
            // mid-resync slots is exactly how the "clone" stacks died before).
            if (slot >= 0 && slot < truth.slots.size()) {
                Slot cs = truth.slots.get(slot);
                boolean isContainerSlot = cs != null && !(cs.inventory instanceof PlayerInventory);
                boolean deliberatelyThrown = actionType == SlotActionType.THROW;
                if (isContainerSlot && (deliberatelyThrown || cursorHoldsFake || foundSlot >= 0)) {
                    ItemStack now = cs.getStack();
                    boolean lostFake = now == null || now.isEmpty() || !FakeItem.stackEquals(now, fake);
                    if (lostFake) {
                        FakeItem.removeContainerFakeSlots(truth.syncId,
                            java.util.Collections.singletonList(slot));
                        FakeItem.dbg("onSlotClick", "CONTAINER-TRUTH unlink slot=" + slot
                            + " thrown=" + deliberatelyThrown);
                    }
                }
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ScreenHandlerMixin threw", t);
        }
    }
}
