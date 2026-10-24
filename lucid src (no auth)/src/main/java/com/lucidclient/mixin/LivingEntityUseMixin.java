package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fake Item: clientside use-completion hooks for the local player on a
 * dedicated server (DonutSMP).
 *
 * <p>On the integrated (singleplayer / LAN) server the fake item is a REAL
 * item, so vanilla {@code tickItemStackUsage}/{@code stopUsingItem} drive the
 * use-to-completion path natively and we must NOT interfere. On a dedicated
 * server the server has no record of the fake — vanilla's client-side tick
 * skips the consume branch entirely (it's {@code world.isClient}-gated) so an
 * eat would freeze mid-pose, and a bow/crossbow/trident release would call
 * the server-side-only projectile spawn and produce nothing locally. These
 * two hooks hand completion off to {@link FakeItem}'s simulation so the
 * cosmetic gesture matches singleplayer without the server ever being told.
 *
 * <ul>
 *   <li>{@code tickItemStackUsage(ItemStack) @At("HEAD")} cancellable — when
 *       the active item is the fake AND it's a HELD_EAT/HELD_DRINK whose
 *       timer is about to hit zero, call {@link FakeItem#onUseTickPre} which
 *       plays the finish sound + burp + clears the active item, then cancel
 *       so vanilla's no-op-on-client consume branch never runs.</li>
 *   <li>{@code stopUsingItem @At("HEAD")} (no cancel) — when the active item
 *       is the fake AND it's a HELD_BOW/CROSSBOW/SPEAR, read the draw charge
 *       from {@link LivingEntity#getItemUseTimeLeft()} and dispatch to Fake
 *       Item's {@link FakeItem#onUseStopPre}, which spawns a clientside fake
 *       projectile + plays the shoot sound. We return without cancelling so
 *       vanilla still clears the USING_ITEM flag at its tail.</li>
 * </ul>
 *
 * <p>Both injects are gated to the local player — other living entities (mobs,
 * other players) keep their real use behaviour, since we only fake the local
 * player's clientside item.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUseMixin {

    /** Read the active item of the {@code this} LivingEntity, or EMPTY. */
    private ItemStack lucid$selfActiveItem() {
        try {
            return ((LivingEntity) (Object) this).getActiveItem();
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Eat/drink completion on a dedicated server. Vanilla's client tick skips
     * the consume path entirely (world.isClient-gated), so we finish the pose
     * ourselves right before the timer hits zero.
     */
    @Inject(
        method = "tickItemStackUsage(Lnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void lucid$finishFakeHeldUse(ItemStack stack, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            ItemStack active = stack != null ? stack : lucid$selfActiveItem();
            LivingEntity self = (LivingEntity) (Object) this;
            if (FakeItem.onUseTickPre(self, active)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid LivingEntityUseMixin.tickItemStackUsage threw", t);
        }
    }

    /**
     * Bow/crossbow/trident release on a dedicated server. Read the draw
     * charge (useTicks elapsed) BEFORE vanilla clears the active item and
     * hand it to Fake Item which spawns a clientside fake projectile + plays
     * the shoot sound. We do NOT cancel — vanilla still clears the USING_ITEM
     * flag + active item stack at the tail.
     */
    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void lucid$fireFakeChargedRelease(CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            LivingEntity self = (LivingEntity) (Object) this;
            ItemStack active = lucid$selfActiveItem();
            if (active == null || active.isEmpty()) return;
            // Compute elapsed use ticks — vanilla getItemUseTime() = max - left.
            int useTime;
            try {
                useTime = self.getItemUseTime();  // elapsed ticks since setCurrentHand
            } catch (Throwable t) {
                useTime = 0;
            }
            FakeItem.onUseStopPre(self, active, useTime);
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid LivingEntityUseMixin.stopUsingItem threw", t);
        }
    }
}
