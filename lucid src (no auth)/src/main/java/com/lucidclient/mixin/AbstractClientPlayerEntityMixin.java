package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.SkinProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that swaps the local player's skin with a preset (Steve/Alex)
 * when the SkinProtect module is active.
 *
 * {@link AbstractClientPlayerEntity#getSkin()} is the single chokepoint for
 * skin lookup — both world rendering (via PlayerEntityRenderer) and any GUI
 * that shows the player's face/body go through it. By injecting at RETURN
 * and cancelling with a replacement {@link SkinTextures}, the renderer
 * draws the preset skin instead of the real one.
 *
 * Only the local player ({@code MinecraftClient.getInstance().player}) is
 * intercepted — other players' {@code getSkin()} calls pass through
 * untouched, so this is purely client-side visual.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void lucid$overrideSkin(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            if (!SkinProtect.isActive()) return;

            // Only swap for the local player — other players keep their real skin.
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            if (client.player != (Object) this) return;

            int preset = SkinProtect.getSelectedPreset();
            SkinTextures replacement;
            switch (preset) {
                case SkinProtect.PRESET_STEVE:
                    replacement = DefaultSkinHelper.getSteve();
                    break;
                case SkinProtect.PRESET_ALEX:
                case SkinProtect.PRESET_RANDOM:
                default:
                    // Both Alex and Random use the UUID-based picker, which
                    // distributes across all default skins (including Alex).
                    replacement = DefaultSkinHelper.getSkinTextures(
                        ((AbstractClientPlayerEntity) (Object) this).getUuid());
                    break;
            }
            if (replacement != null) {
                cir.setReturnValue(replacement);
            }
        } catch (Throwable t) {
            // Never let a mixin crash the game — but log so we can debug.
            LucidClient.LOGGER.error("Lucid AbstractClientPlayerEntityMixin handler threw", t);
        }
    }
}