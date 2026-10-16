package com.anormal.client.mixin;

import com.anormal.client.module.impl.optimization.NoAnimation;
import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freezes animated textures.
 *
 * <p>{@code Sprite.isAnimated()} is what the atlas uses to decide whether a
 * sprite needs its frame advanced (and its texture re-uploaded) every tick.
 * Reporting {@code false} removes that work entirely.</p>
 */
@Mixin(Sprite.class)
public class SpriteMixin {

    @Inject(method = "isAnimated", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$isAnimated(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (cir.getReturnValueZ() && NoAnimation.shouldFreeze()) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
        }
    }
}
