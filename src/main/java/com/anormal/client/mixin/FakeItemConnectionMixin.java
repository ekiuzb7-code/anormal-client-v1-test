package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import com.anormal.client.module.impl.player.FakePay;
import com.anormal.client.module.impl.player.FakeStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Outgoing client-to-server click suppression for the Fake Item ghost.
 *
 * <p>When the user clicks an inventory slot, the client's local
 * {@code ScreenHandler.onSlotClick} performs a <i>predicted</i> slot move and
 * THEN sends a {@code ClickSlotC2SPacket} to the server so its authoritative
 * state agrees. For the ghost item this is a problem:
 * <ul>
 *   <li>The server has no record of the fake — it thinks the ghost slot is
 *       empty, the cursor empty. A click on the ghost slot (or with the ghost
 *       on the cursor) looks impossible to the server, so the server rejects
 *       it, resyncs, and visually reverts the user's action with a flicker.</li>
 * </ul>
 *
 * <p>Fix: suppress the outgoing {@code ClickSlotC2SPacket} BEFORE it leaves the
 * client whenever the click involves the ghost (the slot it's in) OR the
 * cursor is currently carrying the ghost (mid-drag). The local predicted
 * slot-move has already happened and remains intact; the server simply doesn't
 * get a packet it couldn't have honoured. The ghost lives entirely clientside.
 *
 * <p>For chest/container drops specifically the user opted into honest
 * "snap-back" — those clicks still leave the client (because the cursor still
 * thinks it's carrying the real prior contents the server agreed on, not the
 * ghost) and the server correctly rejects / resyncs; the
 * {@link ScreenHandlerMixin} TAIL hook detects the loss and snaps the fake
 * back to its prior slot. So:
 * <ul>
 *   <li>Clicks purely within the player's own inventory + cursor that involve
 *       the ghost: SUPPRESSED (effectively local-only, server stays at its
 *       pre-click state).</li>
 *   <li>Clicks that drop the ghost into a chest: LEAVE through to the server,
 *       which rejects → {@link ScreenHandlerMixin} snaps the fake back.</li>
 * </ul>
 *
 * <p>Heuristic: we suppress when <b>either</b> the click's {@code slot()}
 * matches the ghost's {@link FakeItem#getGhostSlot()}, <b>or</b> the local
 * cursor is currently flagged as carrying the fake ({@link
 * FakeItem#isCursorIsGhost()} == true, tracked by {@link ScreenHandlerMixin}).
 * Since {@code ClickSlotC2SPacket.syncId()} corresponds to whatever screen is
 * open at click time, we keep both player-inv and chest-screen clicks in scope
 * of the heuristic but only the player-inv cursor-carrying-fake case is
 * actually plausibly drag/drop-within-inventory.
 */
@Mixin(ClientConnection.class)
public abstract class FakeItemConnectionMixin {

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void anormal$suppressGhostClickPackets(Packet<?> packet, CallbackInfo ci) {
        try {
            // ── Crystal Optimizer (Argon functionality) ────────────────
            // When an attack packet on an entity leaves the client, check
            // whether the crosshair is on an End Crystal the player can
            // actually break (weakness/strength/tool gate) and discard the
            // crystal client-side immediately — no waiting for the server's
            // removal packet, so the next placement can happen at once.
            com.anormal.client.module.impl.combat.CrystalOptimizer.onPacketSend(packet);

            // ── Fake Stats / Fake Pay: kill the real commands at the wire.
            // A bare /balance (/bal /money) has NO signed arguments, so
            // vanilla sends it as CommandExecutionC2SPacket — NOT
            // ChatCommandSignedC2SPacket (that type is only used when the
            // command HAS signed args, e.g. /pay <player> <amount>). The
            // Fabric ALLOW_COMMAND event registered in LucidClient is the
            // (sometimes-unreliable) first layer; hooking the real packets
            // here is the guaranteed last line of defence — the server never
            // sees the command.
            if (packet instanceof net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket exec
                && FakeStats.maybeCancelFakeBalance(exec.command())) {
                ci.cancel();
                return;
            }
            if (packet instanceof net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket signed) {
                String cmd = signed.command();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                if (!FakePay.onSendCommand(cmd)) {
                    ci.cancel();
                    return;
                }
            }

            if (!FakeItem.isActive()) return;
            // Only on a dedicated server — on the integrated (singleplayer / LAN
            // host) server the fake is a REAL item, so all packets are legit
            // and we must not interfere with vanilla drop/click/interact.
            if (!FakeItem.isDedicatedServer()) return;

            // Dedicated-server Q-drop: when the client drops the held item, the
            // outgoing PlayerActionC2SPacket tells the server to spawn an item
            // entity. The server has no record of the fake, so without help it
            // would spawn a "nothing" drop and the held slot would clear with
            // NO dropping animation/visual in the world. We intercept here:
            // cancel the packet so the server stays unaware, and call
            // FakeItem.dropFakeFromHeld() to do the pure-clientside drop (clear
            // the slot, spawn a ghost ItemEntity at the player's eye).
            if (packet instanceof net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket pa) {
                net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action action = pa.getAction();
                FakeItem.dbgLight("send-PlayerAction", "action=" + action);
                // Defensive layer under ClientPlayerInteractionManagerMixin's
                // attackBlock/updateBlockBreakingProgress HEAD cancellation: any
                // destroy-block action (START / STOP / ABORT) that actually does
                // leave the client while aimed at a placed fake block is killed
                // here — the server has no record of the fake, so a break packet
                // would be at best a no-op the server resyncs around.
                if (action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    || action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                    || action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
                    try {
                        net.minecraft.util.math.BlockPos breakPos = pa.getPos();
                        if (breakPos != null && FakeItem.isFakeBlock(breakPos)) {
                            FakeItem.dbgLight("send-PlayerAction", "CANCEL destroy-action " + action + " pos=" + breakPos);
                            ci.cancel();
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
                if (action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.DROP_ALL_ITEMS
                    || action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.DROP_ITEM) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    ItemStack mainHandStack = (client != null && client.player != null) ? client.player.getMainHandStack() : ItemStack.EMPTY;
                    FakeItem.dbg("send-DROP-check", "action=" + action + " mainHand=" + mainHandStack.getItem().getName().getString() + " matchesFake=" + FakeItem.isFakeIdentity(mainHandStack));
                    if (client == null || client.player == null) return;
                    // Identity check (NOT count-equality): a partially-consumed
                    // fake (count 63 after a placement) must still count as
                    // "hand holds the fake" for Q-drop.
                    boolean handHoldsFake = FakeItem.isFakeIdentity(client.player.getMainHandStack());
                    // dropAll distinguishes Ctrl+Q (DROP_ALL_ITEMS, whole stack
                    // leaves) from plain Q (DROP_ITEM, one copy leaves).
                    boolean dropAll = action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.DROP_ALL_ITEMS;
                    // Q-drop fallback: with an EMPTY selected slot, vanilla's Q
                    // would drop nothing — but the user pressing Q while their
                    // fake sits in a different hotbar slot expects the fake to
                    // drop (that exact gap caused 51 dead Q presses in the
                    // last test: fake in hotbar slot 2, selected slot held
                    // air). dropFakeFromHeld drops from the tracked ghostSlot
                    // (0-8) and re-verifies the slot still holds the fake, so
                    // hijacking the packet here is safe: the server would have
                    // processed a no-op anyway.
                    boolean handEmptyFakeInHotbar = !handHoldsFake
                        && (mainHandStack == null || mainHandStack.isEmpty())
                        && FakeItem.getGhostSlot() >= 0 && FakeItem.getGhostSlot() <= 8;
                    if (handHoldsFake || handEmptyFakeInHotbar) {
                        if (FakeItem.dropFakeFromHeld(dropAll)) {
                            FakeItem.dbgLight("send-DROP", "CANCEL (dropFakeFromHeld OK, dropAll=" + dropAll + " viaFallback=" + handEmptyFakeInHotbar + ")");
                            ci.cancel();
                            return;
                        } else {
                            FakeItem.dbgLight("send-DROP", "dropFakeFromHeld returned false — packet will go OUT");
                        }
                    }
                }
                // Held-use release on a held fake — when the right-click is
                // released mid-use (eat/drink/bow/crossbow/spear), vanilla
                // emits a PlayerActionC2SPacket(RELEASE_USE_ITEM) so the
                // server knows the use ended. The server has no record of
                // the fake, so it would just be a no-op (or worse, anti-cheat
                // bait). Cancel it — the cosmetic / fake-projectile spawn
                // was already driven clientside by FakeItem.onUseStopPre via
                // the LivingEntityUseMixin. Either hand may carry the fake
                // (e.g. off-hand shield), so check both.
                if (action == net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.RELEASE_USE_ITEM
                    && FakeItem.isUseSimActive()) {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client != null && client.player != null
                        && (FakeItem.stackEquals(client.player.getMainHandStack(), FakeItem.getFakeStack())
                            || FakeItem.stackEquals(client.player.getOffHandStack(), FakeItem.getFakeStack()))
                        && (FakeItem.getCurrentSimHand() == null
                            || FakeItem.stackEquals(client.player.getStackInHand(FakeItem.getCurrentSimHand()),
                                                    FakeItem.getFakeStack()))) {
                        FakeItem.dbgLight("send-RELEASE", "CANCEL");
                        ci.cancel();
                        return;
                    }
                }
            }

            // Outgoing use-init packet on a held fake. Vanilla
            // {@code interactItem} normally sends PlayerInteractItemC2SPacket
            // so the server can validate the use; on a dedicated server the
            // server has no record of the fake → the use packet is at best
            // ignored and at worst anti-cheat bait. The interactItem HEAD
            // cancellation in ClientPlayerInteractionManagerMixin already
            // stops the body that sends this packet; this is a defensive
            // second layer that catches any alternate use code path. The
            // packet carries the {@code Hand} it fired from, so we check
            // THAT hand's stack rather than both.
            if (packet instanceof net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket usePkt
                && FakeItem.isUseSimActive()) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    net.minecraft.util.Hand useHand = usePkt.getHand();
                    ItemStack heldAtHand = client.player.getStackInHand(useHand);
                    FakeItem.dbg("send-InteractItem", "hand=" + useHand + " held=" + heldAtHand.getItem().getName().getString() + " matches=" + FakeItem.stackEquals(heldAtHand, FakeItem.getFakeStack()));
                    if (FakeItem.stackEquals(heldAtHand, FakeItem.getFakeStack())) {
                        FakeItem.dbgLight("send-InteractItem", "CANCEL");
                        ci.cancel();
                        return;
                    }
                }
            }

            if (!(packet instanceof net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket click)) return;

            int targetSlot = click.slot();
            int ghostSlot = FakeItem.getGhostSlot();
            boolean cursorIsGhost = FakeItem.isCursorIsGhost();
            int ghostContainerSlot = FakeItem.getGhostContainerSlot();
            int ghostContainerSyncId = FakeItem.getGhostContainerSyncId();

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            FakeItem.dbgLight("send-Click", "syncId=" + click.syncId() + " slot=" + targetSlot + " action=" + click.actionType() + " btn=" + click.button() + " cursorIsGhost=" + cursorIsGhost + " ghostSlot=" + ghostSlot + " containerSlot=" + ghostContainerSlot + " containerSyncId=" + ghostContainerSyncId);

            // Case A: outgoing click targets ANY fake-occupied slot of the CURRENT
            // open screen — the primary ghost, one of the clone stacks, a
            // player-grid fake addressed under the player screen OR embedded
            // in an open chest window (chest syncId + chest slot offset), or
            // a container slot remembered in the chest (isFakeScreenSlot
            // resolves all of them by inventory identity). The server has no
            // record of any of them, so a click on one would be rejected +
            // resynced back to an empty slot, making the copy vanish. Treat
            // every fake slot like the single ghost.
            if (FakeItem.isFakeScreenSlot(click.syncId(), targetSlot)) {
                FakeItem.dbgLight("send-Click", "CANCEL Case A (fake-slot syncId=" + click.syncId() + " target=" + targetSlot + ")");
                // Q-throw from a player-inv slot (SlotActionType.THROW): serve
                // the cosmetic drop clientside so the user sees the tossed fake.
                if (click.actionType() == net.minecraft.screen.slot.SlotActionType.THROW) {
                    if (FakeItem.dropFakeFromScreenSlot(click.syncId(), targetSlot,
                                                        click.button() == 1)) {
                        ci.cancel();
                        return;
                    }
                }
                ci.cancel();
                return;
            }
            // Case B: outgoing click targets ANY container (non-player) slot that
            // currently holds a fake copy — the user has parked one or more
            // stacks in the open chest / shulker / hopper. The server has no
            // record of any of them; a click on such a slot is an "impossible"
            // click it would reject + resync, flickering the user's view and
            // wiping the fake. Cancel so the local predicted move stays and
            // the server stays untouched.
            if (FakeItem.isContainerFakeSlot(click.syncId(), targetSlot)) {
                FakeItem.dbgLight("send-Click", "CANCEL Case B (container-fake-slot " + targetSlot + ")");
                // Q-throw from a container slot (SlotActionType.THROW): serve
                // the cosmetic drop clientside instead of asking the server.
                if (click.actionType() == net.minecraft.screen.slot.SlotActionType.THROW) {
                    if (FakeItem.dropFakeFromScreenSlot(click.syncId(), targetSlot,
                                                        click.button() == 1)) {
                        ci.cancel();
                        return;
                    }
                }
                ci.cancel();
                return;
            }
            // Case C: cursor carries the fake → ANY click that involves the
            // cursor (drag start, drag-end-in-non-player-slot, drop-cursor,
            // pickup-into-cursor) is one the server couldn't honour. Cancel
            // it. This catches:
            //   - dragging fake from inv into a chest slot
            //     (playerScreenHandler-prev → chest syncId cursor stays fake)
            //   - swapping the fake onto a non-fake chest slot
            //   - dropping the cursor outside the window (slot==-999)
            //   - pick-up of a fake-containing slot (handled by A already).
            if (cursorIsGhost && clickInvolvesCursor(click)) {
                FakeItem.dbgLight("send-Click", "CANCEL Case C (cursor-is-ghost)");
                // Drop the cursor outside the GUI: slot == -999 means
                // "click outside the window"; SlotActionType.PICKUP or THROW
                // with slot==-999 → drop the cursor. Serve the cosmetic drop
                // clientside and skip the packet entirely.
                if (targetSlot == -999) {
                    if (FakeItem.dropFakeFromScreenSlot(click.syncId(), -999,
                                                        click.button() == 1)) {
                        ci.cancel();
                        return;
                    }
                }
                ci.cancel();
                return;
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientConnectionMixin threw", t);
        }
    }

    /**
     * True when the click's action type would place / read / drop the cursor.
     * Used to decide whether a click involving the fake-carrying cursor should
     * be cancelled. Covers PICKUP (left-click), QUICK_MOVE (shift-click),
     * SWAP (number-keys), PICKUP_ALL, THROW, CLONE (creative middle-click —
     * unlikely but cancels harmlessly), and the drag family ( QUICK_CRAFT).
     * PICKUP (placing the cursor onto a slot) and THROW/drag-release-outside
     * are the cases that actually matter for the cosmetic; the rest are
     * covered conservatively so no fake-carrying-cursor click leaks.
     */
    private static boolean clickInvolvesCursor(net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket click) {
        try {
            net.minecraft.screen.slot.SlotActionType t = click.actionType();
            return t == net.minecraft.screen.slot.SlotActionType.PICKUP
                || t == net.minecraft.screen.slot.SlotActionType.PICKUP_ALL
                || t == net.minecraft.screen.slot.SlotActionType.QUICK_MOVE
                || t == net.minecraft.screen.slot.SlotActionType.THROW
                || t == net.minecraft.screen.slot.SlotActionType.SWAP
                || t == net.minecraft.screen.slot.SlotActionType.CLONE
                || t == net.minecraft.screen.slot.SlotActionType.QUICK_CRAFT;
        } catch (Throwable t) {
            return false;
        }
    }
}
