package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the ghost item alive against the server's own sync packets.
 *
 * <p>Every one of these packets carries the real inventory contents, so each of them would erase
 * the fake on the next tick: a single-slot update, a full inventory resync, a cursor sync, an
 * equipment sync, or the player-inventory push after a respawn. They are cancelled only for the
 * exact slot the ghost lives in - everything else still flows through untouched, so real items
 * keep syncing normally.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public class FakeItemNetworkMixin {

    /** Debug counter: how many slot updates were inspected. */
    private static volatile int onSlotUpdateSeenCnt = 0;

    /** Set when a full inventory packet arrives, so the resync can be re-applied afterwards. */
    private static volatile boolean anormal$reapplyAfterInventory = false;

    /**
     * Cancel a server-driven overwrite of the ghost slot in the player's own
     * {@link net.minecraft.entity.player.PlayerInventory}. The server believes
     * that slot is empty (or holds the pre-fake real item) — a periodic resync
     * packet would silently revert our fake to nothing locally. Dropping the
     * packet here keeps the fake in view.
     */
    @Inject(method = "onSetPlayerInventory", at = @At("HEAD"), cancellable = true)
    private void anormal$cancelSetPlayerInventoryForGhostSlot(SetPlayerInventoryS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            // Only on a dedicated server — singleplayer's integrated server is
            // authoritative on the SAME inventory slot (we wrote the fake there
            // via syncToIntegratedServer), so its resyncs carry the fake and must
            // flow through un-cancelled; canceling them would desync the client
            // when the server-side slot later takes a real picked-up item.
            if (!FakeItem.isDedicatedServer()) return;
            // Guard EVERY fake-occupied player-inventory slot (primary ghost +
            // extras) against the server overwriting it with its "empty" truth.
            if (FakeItem.isGhostSlot(packet.slot())) {
                ci.cancel();
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onSetPlayerInventory threw", t);
        }
    }
    /**
     * Cancel a server-driven slot update for EITHER the player-inventory ghost
     * slot OR the tracked container ghost slot. The server believes those slots
     * are empty (the fake never existed there for it) — a periodic resync
     * packet would silently revert our local fake to nothing. Cancelling keeps
     * the fake in view. Out-of-scope slots flow through so honest chest truth
     * (other slots the server legitimately updated) stays intact.
     */
    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"), cancellable = true)
    private void anormal$cancelSlotUpdateForGhostSlot(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            // Only on a dedicated server — singleplayer's integrated server is
            // authoritative on the SAME inventory, so canceling its syncs
            // would fight real server-side truth.
            if (!FakeItem.isDedicatedServer()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            int syncId = packet.getSyncId();
            int slot = packet.getSlot();

            // Unified, screen-aware guard: cancels a server-driven overwrite for
            // ANY fake-occupied slot of the CURRENT open screen — the player
            // grid (primary ghost + every clone stack) whether addressed via the
            // player screen OR embedded in an open chest/shulker/enderchest
            // window at chest offsets, AND every container slot remembered in
            // the chest. The server believes those slots are empty (the fakes
            // never existed for it); a periodic resync packet would silently
            // revert each one to nothing locally. This single check treats
            // every stack identically — no more "primary survives, clones die".
            //
            // Empty-push suppression only: a server slot update that carries an
            // EMPTY stack is the lesson "wipe this slot back to truth" — the
            // exact sweep that kills a fake, so cancel it and let the heal keep
            // the fake. But an update carrying a REAL stack in a remembered fake
            // slot is the server delivering authoritative content (another real
            // item slid into that position) — that must ALWAYS render or the
            // user's container contents "disappear". The full-window path
            // (onInventory) already lets real contents through; this keeps the
            // per-slot path consistent.
            ItemStack incoming = null;
            try { incoming = packet.getStack(); } catch (Throwable ignored) {}
            boolean emptyPush = incoming == null || incoming.isEmpty();
            if (FakeItem.isFakeScreenSlot(syncId, slot) && emptyPush) {
                FakeItem.dbg("onSlotUpdate-CANCEL", "empty-push fake-slot syncId=" + syncId + " slot=" + slot);
                ci.cancel();
                return;
            }
            // Pass-through sample (every 200th) so we can see the server's
            // resync traffic shape and confirm it's not hitting us in a
            // syncId/slot pairing we didn't account for.
            if (onSlotUpdateSeenCnt++ % 200 == 0) {
                FakeItem.dbg("onSlotUpdate-pass", "syncId=" + syncId + " slot=" + slot
                    + " ghostSlot=" + FakeItem.getGhostSlot() + " cnt=" + onSlotUpdateSeenCnt);
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onScreenHandlerSlotUpdate threw", t);
        }
    }
    /**
     * Absorb a server-driven FULL-WINDOW refresh ({@link InventoryS2CPacket},
     * handler {@code onInventory}) that would otherwise wipe the fake.
     * {@code onInventory} routes to {@code ScreenHandler.updateSlotStacks}
     * which wholesale overwrites every slot in that handler. Two cases:
     * <ul>
     *   <li><b>Player window</b> ({@code syncId} == player screen handler):
     *       the fake lives in the player grid, so a refresh would sweep it —
     *       cancel the whole packet and re-apply the fake to its tracked slots
     *       (player inv + container + cursor) via
     *       {@link FakeItem#reapplyAfterResync()}.</li>
     *   <li><b>Open CONTAINER window</b>: the packet carries the storage's
     *       REAL contents (the server's authoritative view). Voiding it used
     *       to blank the entire container whenever a fake sat in the player
     *       grid of the same window. We now LET the packet through — real
     *       items render — and re-assert the fake slots immediately after via
     *       the {@link #anormal$reapplyAfterInventory RETURN} inject, so fakes
     *       survive in place with no flicker.</li>
     * </ul>
     */
    @Inject(method = "onInventory", at = @At("HEAD"), cancellable = true)
    private void anormal$cancelInventoryResyncForGhost(InventoryS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            int syncId = packet.syncId();
            int ghostSlot = FakeItem.getGhostSlot();
            boolean cursorGhost = FakeItem.isCursorIsGhost();

            int playerSyncId;
            try { playerSyncId = client.player.playerScreenHandler.syncId; }
            catch (Throwable t) { playerSyncId = -1; }
            int openSyncId = (client.player.currentScreenHandler != null)
                ? client.player.currentScreenHandler.syncId : -1;

            // Wholesale window refresh. Every screen embeds the player
            // inventory, so a full-window sync of the CURRENT screen carries
            // the server's "empty" truth for BOTH the container slots AND the
            // player-grid section — sweeping every fake stack (primary +
            // clones) at once in its old shape.
            boolean playerWindowSync = syncId == playerSyncId;
            boolean containerWindowSync = !playerWindowSync && openSyncId >= 0 && syncId == openSyncId;
            boolean fakeOnScreen = FakeItem.hasFakeOnScreen(syncId);
            boolean wipesCursor = cursorGhost && syncId == openSyncId;

            if (containerWindowSync && (fakeOnScreen || wipesCursor)) {
                // Container full-window refresh while fakes are present: let the
                // packet apply (so the container's REAL items render) and
                // re-assert the fakes right after it lands — same-tick, before
                // the frame, so no flicker.
                anormal$reapplyAfterInventory = true;
                FakeItem.dbg("onInventory-LET-THROUGH", "pkt.syncId=" + syncId
                    + " playerSyncId=" + playerSyncId + " openSyncId=" + openSyncId
                    + " ghostSlot=" + ghostSlot + " cursorGhost=" + cursorGhost
                    + " fakeOnScreen=" + fakeOnScreen);
                return;
            }

            if (!playerWindowSync) return;   // unrelated window — let it through

            // Player window: a refresh sweeps the grid fake (and would also
            // blank a fake-carrying cursor) — cancel and restore everything.
            if (!fakeOnScreen && !wipesCursor) return;

            FakeItem.dbg("onInventory-CANCEL", "pkt.syncId=" + syncId
                + " playerSyncId=" + playerSyncId + " openSyncId=" + openSyncId
                + " ghostSlot=" + ghostSlot + " cursorGhost=" + cursorGhost);

            ci.cancel();
            FakeItem.reapplyAfterResync();
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onInventory threw", t);
        }
    }
    /**
     * Re-assert the fake slots right after a container {@code onInventory}
     * full-window refresh we chose to let through (see the HEAD inject,
     * {@code anormal$cancelInventoryResyncForGhost}). The packet's real contents
     * have already been written to the handler, so re-applying restores the
     * fakes in place while keeping every honest item on screen.
     */
    @Inject(method = "onInventory", at = @At("RETURN"))
    private void anormal$reapplyAfterInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        try {
            if (!anormal$reapplyAfterInventory) return;
            anormal$reapplyAfterInventory = false;
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            FakeItem.reapplyAfterResync();
            // Post-write snapshot of the OPEN storage window: shows exactly
            // what the server's real contents left behind plus what the
            // reapply restored, so the "other stacks disappear and leave 1"
            // repro is visible in the log on a single line.
            try {
                net.minecraft.screen.ScreenHandler handler = client.player.currentScreenHandler;
                if (handler != null && handler.syncId >= 0
                    && !(handler instanceof net.minecraft.screen.PlayerScreenHandler)) {
                    StringBuilder sb = new StringBuilder("container-after-reapply syncId=" + handler.syncId);
                    for (int i = 0; i < handler.slots.size(); i++) {
                        net.minecraft.screen.slot.Slot s = handler.slots.get(i);
                        if (s == null || s.inventory instanceof net.minecraft.entity.player.PlayerInventory) continue;
                        net.minecraft.item.ItemStack st = s.getStack();
                        if (st != null && !st.isEmpty()) {
                            sb.append(" | ").append(i).append('=')
                              .append(st.getItem().getName().getString()).append('x').append(st.getCount());
                        }
                    }
                    FakeItem.dbg("container-after-reapply", sb.toString());
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onInventory reapply threw", t);
        }
    }
    /**
     * Cancel a server-driven cursor overwrite
     * ({@link SetCursorItemS2CPacket}, handler {@code onSetCursorItem}) when
     * the local cursor currently carries the fake. The server believes the
     * cursor is empty (the fake never existed there for it) — this packet
     * would silently snap the cursor back to empty mid-drag. Cancelling keeps
     * the fake on the cursor so the user can finish dropping it where they
     * like.
     */
    @Inject(method = "onSetCursorItem", at = @At("HEAD"), cancellable = true)
    private void anormal$cancelCursorResyncForGhost(SetCursorItemS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            if (!FakeItem.isCursorIsGhost()) return;
            // The server can only legitimately clear or replace the cursor
            // when IT initiated the drag. For our fake the cursor was set
            // purely clientside, so any server cursor overwrite is a wipe we
            // must ignore. (Empty content → wipe; non-empty content → the
            // server trying to seed a real cursor stack we never asked for,
            // which can't happen since the fake isn't there server-side.)
            FakeItem.dbg("onSetCursorItem-CANCEL", "pkt.contents=" + (packet.contents() == null ? "null" : packet.contents().getItem().getName().getString()));
            ci.cancel();
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onSetCursorItem threw", t);
        }
    }
    /**
     * Cancel a server-driven equipment overwrite for OUR local player when
     * the update targets the fake's equipped slot (e.g. ELYTRA → CHEST) and
     * nothing else. The server believes that slot is empty (the fake elytra
     * was never equipped for it); a periodic
     * {@link EntityEquipmentUpdateS2CPacket} for our own entity-id would nuke
     * the local elytra, killing the cosmetic wing pose + the elytra gliding
     * model. Cancelling preserves the fake equipment so the wings stay open.
     *
     * <p>If the equipment update touches other slots too (it can carry a
     * list of {@code (slot, stack)} pairs), we let it through so those
     * honest slots stay current and rely on {@code FakeItem}'s periodic
     * re-assert to put the fake equipment back next tick (at most a
     * 50 ms flicker) — the alternative of partial application isn't
     * possible because {@code apply()} is one atomic call.
     */
    @Inject(method = "onEntityEquipmentUpdate", at = @At("HEAD"), cancellable = true)
    private void anormal$cancelEquipmentResyncForGhost(EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            int myId = client.player.getId();
            boolean isMe = packet.getEntityId() == myId;
            EquipmentSlot fakeSlot = FakeItem.getFakeEquipSlot();
            // Log every equipment packet for our own player (not spammy —
            // these fire on item equip/unequip, ~1/event).
            if (isMe) {
                FakeItem.dbgLight("onEntityEquipmentUpdate", "entityId=" + packet.getEntityId() + " isMe=" + isMe + " fakeEquipSlot=" + fakeSlot);
            }
            if (!isMe) return;

            if (fakeSlot == null) {
                FakeItem.dbgLight("onEntityEquipmentUpdate", "SKIP fakeSlot=null (fake item has no EQUIPPABLE component)");
                return;
            }

            java.util.List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> list =
                packet.getEquipmentList();
            if (list == null || list.isEmpty()) return;

            boolean touchesFake = false;
            boolean touchesOther = false;
            for (com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack> p : list) {
                if (p.getFirst() == fakeSlot) touchesFake = true;
                else touchesOther = true;
            }
            FakeItem.dbg("onEntityEquipmentUpdate", "touchesFake=" + touchesFake + " touchesOther=" + touchesOther + " fakeSlot=" + fakeSlot);
            if (touchesFake && !touchesOther) {
                FakeItem.dbgLight("onEntityEquipmentUpdate", "CANCEL (pure-equip-slot-wipe)");
                ci.cancel();
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onEntityEquipmentUpdate threw", t);
        }
    }

}
