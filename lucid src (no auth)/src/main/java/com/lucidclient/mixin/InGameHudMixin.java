package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam (Freecam-mod implementation): makes the vanilla HUD (hearts,
 * hunger, hotbar, air) read its stats from the real player instead of the
 * FreeCamera entity that is currently the camera.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamHudPlayer(CallbackInfoReturnable<PlayerEntity> cir) {
        try {
            if (Freecam.isActive()) {
                cir.setReturnValue(MinecraftClient.getInstance().player);
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid InGameHudMixin threw", t);
        }
    }
}
