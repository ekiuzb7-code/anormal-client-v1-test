package com.lucidclient.mixin;

import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam (Freecam-mod implementation): prevents switching to third person
 * (F5) while the camera is detached — the freecam camera owns the view.
 */
@Mixin(GameOptions.class)
public class GameOptionsMixin {

    @Inject(method = "setPerspective", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamPerspective(CallbackInfo ci) {
        if (Freecam.isActive()) {
            ci.cancel();
        }
    }
}
