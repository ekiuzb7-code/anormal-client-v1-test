package com.anormal.client.mixin;

import com.anormal.client.module.impl.player.SkinShower;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the SkinShower texture on the local player's model.
 *
 * <p>Runs before SkinProtect's hook (both inject at RETURN, this one just wins by setting the
 * value first) and only for the local player, so nobody else's skin changes.</p>
 */
@Mixin(AbstractClientPlayerEntity.class)
public class SkinShowerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$applyCustomSkin(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            SkinTextures custom = SkinShower.override();
            if (custom == null) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if (mc.player != (Object) this) return;

            cir.setReturnValue(custom);
        } catch (Throwable ignored) {
        }
    }
}
