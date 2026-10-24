package com.anormal.client.mixin;

import com.anormal.client.module.impl.player.SkinProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the local player's skin on the client only.
 *
 * <p>The skin is read through {@code AbstractClientPlayerEntity.getSkin()}, which is where the
 * renderer, the tab list and the inventory model all get their textures from. Returning early with
 * a default skin therefore changes how <em>you</em> see yourself everywhere, while the server and
 * every other player keep seeing the real one.</p>
 *
 * <p>Only the local player is touched - other players always keep their real skin, otherwise this
 * would turn into an ESP.</p>
 */
@Mixin(AbstractClientPlayerEntity.class)
public class SkinProtectMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$overrideSkin(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            if (!SkinProtect.isActive()) return;
            // SkinShower owns the model when it has a texture loaded - otherwise the two
            // RETURN hooks would fight and the winner would depend on mixin order.
            if (com.anormal.client.module.impl.player.SkinShower.isActive()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if (mc.player != (Object) this) return;

            SkinTextures replacement;
            switch (SkinProtect.getSelectedPreset()) {
                case SkinProtect.PRESET_STEVE -> replacement = DefaultSkinHelper.getSteve();
                // Alex and Random both use the UUID-based picker, which spreads across all the
                // default skins (including Alex).
                default -> replacement = DefaultSkinHelper.getSkinTextures(
                        ((AbstractClientPlayerEntity) (Object) this).getUuid());
            }
            if (replacement != null) cir.setReturnValue(replacement);
        } catch (Throwable ignored) {
        }
    }
}
