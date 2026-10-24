package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the local player from actually starting to glide when the elytra is fake.
 *
 * <p>With a forged elytra the client would otherwise send the glide state and the server would
 * reject (or worse, desync) it. Blocking {@code checkGliding} for the local player only leaves the
 * visual pose intact while the real movement stays normal.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class FakeItemGlideMixin {

    @Inject(method = "checkGliding", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$blockVanillaGlideStart(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if ((Object) this != (Object) mc.player) return;
            if (!FakeItem.isFakeElytraEquipped()) return;

            cir.setReturnValue(false);
        } catch (Throwable t) {
            LucidLog.LOGGER.error("glide block threw", t);
        }
    }
}
