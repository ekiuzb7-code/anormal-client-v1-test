package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that performs the predictive client-side removal of End Crystals.
 *
 * The vanilla chain when you attack a crystal:
 *   client → attack packet → server → server computes the explosion, destroys
 *   the crystal → server sends back a removal packet → client removes the
 *   entity. On high ping this round-trip delays the next placement because the
 *   physical crystal entity is still in the way on your screen.
 *
 * We {@code @Inject} at the TAIL of
 * {@link ClientPlayerInteractionManager#attackEntity attackEntity} — i.e. AFTER
 * the attack packet has been sent — and, when the attacked entity is an
 * {@link EndCrystalEntity} and CrystalOptimizer is active, immediately
 * {@link Entity#discard()} it on the client. The server still registers the
 * damage a few ms later, but the crystal is gone from your view instantly,
 * freeing the space for the next placement.
 *
 * We inject at TAIL (not HEAD) so the attack packet is actually sent before we
 * mutate the client world — keeping the server-side damage in sync.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class FakeItemInteractionMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void anormal$breakFakeFrameHead(PlayerEntity player, Entity target, CallbackInfo ci) {
        // DonutSMP-only "break the fake out of an item frame" via left-click.
        // The vanilla attack packet tells the server to break the frame; the
        // server has no record of the fake → nothing happens. We intercept the
        // packet HEAD, pop the fake out of the frame on the client (clearing
        // the frame marker + spawning a clientside ItemEntity for re-absorb),
        // and cancel so the outgoing attack never makes the server redundant.
        try {
            boolean isActive = FakeItem.isActive();
            boolean isDed = FakeItem.isDedicatedServer();
            FakeItem.dbg("attackEntity-HEAD", "target=" + (target == null ? "null" : target.getClass().getSimpleName() + "#" + target.getId()) + " isActive=" + isActive + " isDedicated=" + isDed);
            if (!isActive) return;
            if (!isDed) return;  // singleplayer: real frames

            // Placed RESTING fake drop (non-block fake right-click-placed on the
            // ground): a left-click on it is the "mine it back" gesture — pop it
            // into the inventory and cancel so no attack packet reaches the server.
            if (target instanceof net.minecraft.entity.ItemEntity dropEntity
                    && FakeItem.isPlacedDrop(dropEntity.getId())) {
                boolean mined = FakeItem.mineBackPlacedDrop(dropEntity.getId());
                FakeItem.dbg("attackEntity-drop", "placedDropId=" + dropEntity.getId() + " mined=" + mined);
                if (mined) {
                    ci.cancel();
                    return;
                }
            }
            if (!(target instanceof ItemFrameEntity frame)) return;
            boolean frameInSet = FakeItem.isFrameFaked(frame.getId());
            FakeItem.dbg("attackEntity-frame", "frameId=" + frame.getId() + " inSet=" + frameInSet);
            if (!frameInSet) return;
            boolean ok = FakeItem.popFakeFromFrame(frame.getId());
            FakeItem.dbg("attackEntity-pop", "ok=" + ok);

            ci.cancel();
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayerInteractionManagerMixin.breakFakeFrame threw", t);
        }
    }

    /**
     * Fake Item: place / remove the fake in an item frame by right-clicking it.
     *
     * <p>Vanilla {@link ClientPlayerInteractionManager#interactEntity} sends a
     * {@code PlayerInteractEntityC2SPacket} to let the server validate the
     * right-click — and the server independently checks whether the clicker
     * actually has an item in hand to place. On a server-authoritative SMP
     * like DonutSMP the server has no record of the fake, so it silently
     * ignores the click and never echoes back an "item frame now holds X"
     * update — the frame stays visually empty / unchanged no matter how many
     * times you click it.
     *
     * <p>This handler intercepts the right-click at HEAD, BEFORE the vanilla
     * body sends the interaction packet, and:
     * <ul>
     *   <li>If the target is an {@link ItemFrameEntity} AND the module is
     *       active AND the stack in the clicker's {@code hand} is the fake
     *       → toggles the frame's "fake placement" marker
     *       ({@link FakeItem#toggleFrame(int)}) and short-circuits with
     *       {@link ActionResult#SUCCESS}, so the interaction packet is NEVER
     *       sent. The server can't reject what it doesn't see — the frame
     *       visually holds the fake purely clientside via
     *       {@link ItemFrameEntityRendererMixin}. This works on DonutSMP
     *       exactly as on any other server (it's local-only).</li>
     *   <li>Otherwise falls through untouched so vanilla handling (boats,
     *       villagers, minecarts, etc.) proceeds normally and the packet
     *       is sent as usual.</li>
     * </ul>
     *
     * <p>The right-click is a per-frame toggle: clicking an already-faked
     * frame removes the fake, clicking a fresh frame places it. Placement
     * CONSUMES one copy from the fake stack in the inventory (the fake leaves
     * the hand and lands in the frame); removal RETURNS it to the inventory.
     * {@code ;clear} / module-disable wipes all fake-frame markers.
     */
    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void anormal$placeFakeInFrame(PlayerEntity player, Entity target, Hand hand,
                                        CallbackInfoReturnable<ActionResult> cir) {
        try {
            boolean isActive = FakeItem.isActive();
            boolean isDed = FakeItem.isDedicatedServer();
            FakeItem.dbg("interactEntity-HEAD", "target=" + (target == null ? "null" : target.getClass().getSimpleName() + "#" + target.getId()) + " hand=" + hand + " isActive=" + isActive + " isDedicated=" + isDed);
            if (!isActive) return;
            // Only on a dedicated server — on the integrated (singleplayer /
            // LAN host) server the fake is a REAL item, so vanilla right-click
            // places it into the frame natively and we must not interfere.
            if (!isDed) return;
            if (!(target instanceof ItemFrameEntity frame)) return;

            ItemStack fake = FakeItem.getFakeStack();
            if (fake.isEmpty()) return;

            ItemStack held = player != null ? player.getStackInHand(hand) : ItemStack.EMPTY;
            // Count-insensitive identity: after the FIRST placement consumes one
            // copy from a multi-count stack, the held stack's COUNT no longer
            // matches the fakeStack template (2 left in the template vs 1 held), and
            // ItemStack.areEqual would reject the second frame's click. The fake
            // gate must match on item + components only.
            FakeItem.dbg("interactEntity-frame", "held=" + (held == null ? "null" : held.getItem().getName().getString())
                + " fake=" + fake.getItem().getName().getString()
                + " matches=" + FakeItem.isFakeIdentity(held));
            if (!FakeItem.isFakeIdentity(held)) return;

            // Determine the exact inventory slot the click is happening with so
            // the consume/return of the fake targets the slot being held — with
            // multiple fake stacks a blind "first matching slot" consume would
            // shrink the WRONG stack and the item in the user's hand would stay.
            int heldSlot = -1;
            if (player != null) {
                PlayerInventory inv = player.getInventory();
                heldSlot = hand == Hand.OFF_HAND ? PlayerInventory.OFF_HAND_SLOT : inv.getSelectedSlot();
            }
            // Toggle this frame's fake-placement marker and short-circuit
            // BEFORE the vanilla body sends the interaction packet (so the
            // server, which has no record of the fake, never has the chance
            // to reject the click — this is what makes it work on DonutSMP).
            boolean nowFaked = FakeItem.toggleFrame(frame.getId(), heldSlot);
            FakeItem.dbg("interactEntity-toggle", "frameId=" + frame.getId() + " nowFaked=" + nowFaked);
            // Vanilla's add/remove-item frame "pop", played purely locally.
            FakeItem.playFrameToggleSound(frame, nowFaked);

            cir.setReturnValue(ActionResult.SUCCESS);
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayerInteractionManagerMixin.placeFakeInFrame threw", t);
        }
    }

    /**
     * Fake Item: serve the right-click "use" gesture for the fake entirely
     * clientside on a dedicated server so singleplayer's use gestures
     * (eat, drink, shield, spyglass, bow draw, crossbow charge, trident
     * charge, instant throwables, goat horn) all feel the same on DonutSMP.
     *
     * <p>Vanilla {@link ClientPlayerInteractionManager#interactItem} runs the
     * item's {@code use()} body (which for held-use items like food calls
     * {@code setCurrentHand} and starts the pose) AND THEN sends a
     * {@code PlayerInteractItemC2SPacket} so the server agrees you used the
     * item. On DonutSMP the server has no record of the fake, so the use
     * packet is at best ignored and at worst reaches anti-cheat. We
     * intercept at HEAD: when the fake is the held item AND we have a
     * clientside cosmetic for its use family, run the cosmetic now
     * ({@link FakeItem#onInteractItemStart}) and short-circuit with
     * {@link ActionResult#SUCCESS} so the vanilla use-body never executes →
     * no outgoing use packet. {@link ClientConnectionMixin} double-guards
     * the outgoing use/release packets too.
     *
     * <p>Server-authoritative OUTCOMES (real damage / hunger / potion /
     * teleport / XP / flight) are impossible from the client alone and stay
     * out of scope; this hook only services the cosmetic gesture.
     */
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void anormal$serveFakeUse(PlayerEntity player, Hand hand,
                                    CallbackInfoReturnable<ActionResult> cir) {
        try {
            boolean isActive = FakeItem.isActive();
            boolean isDed = FakeItem.isDedicatedServer();
            if (!isActive || !isDed) {
                FakeItem.dbg("interactItem-HEAD", "SKIP isActive=" + isActive + " isDedicated=" + isDed + " hand=" + hand);
                return;
            }
            // Pass the decision to FakeItem — it classifies the use family and
            // either starts a held pose locally, fires an instant use, or
            // returns false to let vanilla fall through untouched.
            boolean served = FakeItem.onInteractItemStart(hand);
            FakeItem.dbg("interactItem", "hand=" + hand + " served=" + served);
            if (served) {
                cir.setReturnValue(ActionResult.SUCCESS);
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayerInteractionManagerMixin.serveFakeUse threw", t);
        }
    }

    /**
     * Fake Item ground placement (DonutSMP spawner fixed):
     * right-clicking a REAL block with a block fake in hand places the fake as
     * a clientside block against the clicked face (no placement packet — the
     * server never hears about it). Short-circuits so vanilla's
     * {@code PlayerInteractBlockC2SPacket} never leaves the client.
     */
    @Inject(method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;",
        at = @At("HEAD"), cancellable = true)
    private void anormal$handleFakeBlockUse(ClientPlayerEntity player, Hand hand,
                                          BlockHitResult hitResult,
                                          CallbackInfoReturnable<ActionResult> cir) {
        try {
            if (!FakeItem.isActive() || !FakeItem.isDedicatedServer()) return;
            if (player == null || hitResult == null) return;
            BlockPos hitPos = hitResult.getBlockPos();

            // Storage-container anchor capture: remember which block the player
            // is right-clicking so the container window about to open is keyed
            // to that block ("fake item stays in the container" across
            // close/reopen — chest/barrel/hopper/shulker get "pos@x,y,z", the
            // ender chest gets "ender"). Only storage blocks produce a key;
            // other GUI blocks leave openContainerKey untouched.
            String containerKey = FakeItem.storageKeyForBlock(
                player.getEntityWorld().getBlockState(hitPos).getBlock(), hitPos);
            if (containerKey != null) {
                FakeItem.captureOpenedContainer(containerKey);
                FakeItem.dbg("interactBlock-container", "captured key=" + containerKey);
            }

            // Right-click with a BLOCK fake held + ground placement enabled →
            // place the fake against the clicked face. Note: this also covers
            // right-clicking our OWN placed fake blocks (spawner or otherwise)
            // — there is no storage GUI anymore, that's just placement.
            ItemStack held = player.getStackInHand(hand);
            if (held == null || held.isEmpty()) return;
            if (!FakeItem.isFakeIdentity(held)) return;
            if (!FakeItem.canPlaceOnGround()) return;
            BlockPos placeAt = hitPos.offset(hitResult.getSide());
            if (FakeItem.placeFakeBlock(placeAt, hand)) {

                cir.setReturnValue(ActionResult.SUCCESS);
            }
            // placeFakeBlock returned false (non-block fake / air target) →
            // fall through untouched so vanilla's use path handles it.
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayerInteractionManagerMixin.handleFakeBlockUse threw", t);
        }
    }

    /**
     * Fake Item hold-to-break: left-click on a placed fake block is intercepted
     * at HEAD (returning true EXTENDS the vanilla "I'm mining" state so the
     * game keeps re-calling attackBlock every held frame) and the actual
     * breaking progress + crack overlay are driven clientside by
     * {@link FakeItem#tickFakeBlockWorld}. No break packet ever reaches the
     * server — it has no record of the block.
     */
    @Inject(method = "attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
        at = @At("HEAD"), cancellable = true)
    private void anormal$cancelFakeBlockAttack(BlockPos pos, Direction direction,
                                             CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!FakeItem.isActive() || !FakeItem.isDedicatedServer()) return;
            if (pos == null) return;
            if (FakeItem.isFakeBlock(pos)) {
                FakeItem.dbg("attackBlock", "FAKE-BLOCK hold-to-break pos=" + pos);
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayerInteractionManagerMixin.cancelFakeBlockAttack threw", t);
        }
    }

    /**
     * Ditto for the continuous per-frame breaking path — if the game ever
     * routes the held attack through updateBlockBreakingProgress (rather than
     * re-invoking attackBlock), keep it local and never emit break packets for
     * a fake block.
     */
    @Inject(method = "updateBlockBreakingProgress(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
        at = @At("HEAD"), cancellable = true)
    private void anormal$cancelFakeBlockProgress(BlockPos pos, Direction direction,
                                               CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!FakeItem.isActive() || !FakeItem.isDedicatedServer()) return;
            if (pos == null) return;
            if (FakeItem.isFakeBlock(pos)) {
                FakeItem.dbg("updateBlockBreakingProgress", "FAKE-BLOCK keep local pos=" + pos);
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid ClientPlayerInteractionManagerMixin.cancelFakeBlockProgress threw", t);
        }
    }
}
