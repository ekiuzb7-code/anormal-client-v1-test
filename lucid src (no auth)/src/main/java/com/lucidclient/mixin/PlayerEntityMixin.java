package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fake Item: block VANILLA's client-side double-tap glide-start for the local
 * player while our fake elytra is equipped on a dedicated server.
 *
 * <p>The fake elytra sits in the real chest slot client-side, so vanilla's
 * {@code PlayerEntity.checkGliding()} (fired from ClientPlayerEntity's
 * double-tap-space handling) thinks a real glide is available and starts one
 * locally, then tells the server. The server (which has no elytra for us)
 * rejects it and the rejection rubber-bands the camera — the "camera shakes
 * when I double tap space" symptom. Cancelling here keeps vanilla out of the
 * way entirely; FakeItem's purely-cosmetic glide system owns the pose.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "checkGliding", at = @At("HEAD"), cancellable = true)
    private void lucid$blockVanillaGlideStart(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return;
            if ((Object) this != (Object) c.player) return;  // local player only
            if (!FakeItem.isFakeElytraEquipped()) return;    // no fake elytra — vanilla stands
            cir.setReturnValue(false);
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid PlayerEntityMixin threw", t);
        }
    }
}
