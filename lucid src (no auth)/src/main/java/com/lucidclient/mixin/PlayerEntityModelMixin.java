package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeItem;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fake Item (rebuilt elytra pose system): lock the local player's HEAD to
 * vanilla's gliding pose during the cosmetic glide.
 *
 * <p>Decompiled {@code BipedEntityModel.setAngles} sets the head to follow
 * the aim ({@code head.pitch = state.pitch * 0.017453292f}) and then, in its
 * {@code isGliding} branch, hard-overwrites it with the CONSTANT
 * {@code -0.7853982f} (-45°). The real game NEVER points the head down while
 * gliding — the head holds that fixed 45° nose-up angle regardless of where
 * the player looks. Our PlayerEntityRendererMixin sets {@code state.isGliding}
 * while the fake glide is engaged, which already triggers vanilla's own
 * constant; this TAIL injection just re-asserts the identical value so
 * nothing downstream can drift the head into a nose-down pose (the earlier
 * "head looks straight down" read came from our own aim-following / horizon
 * compensation formulas, not from vanilla).
 *
 * <p>The head part is reached via the {@code ModelWithHead#getHead()} interface
 * method — the {@code head} FIELD lives on the superclass BipedEntityModel and
 * cannot be @Shadow'd against PlayerEntityModel (Mixin resolves shadows only
 * against the target class itself; shadowing it crashed launch in v1.13.3).
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(
        method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
        at = @At("TAIL")
    )
    private void lucid$lockGlideHead(PlayerEntityRenderState state, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;

            // Vanilla's gliding head is the FIXED -π/4 constant from
            // BipedEntityModel's isGliding branch — not aim-followed. Snap-free
            // version: blend continuously from the aim-following pitch
            // (state.pitch * 0.017453292f, exactly vanilla's own formula) to
            // the gliding constant by the smoothed pose progress, so the
            // fold-out eases the head back to the aim instead of flipping at a
            // threshold when the animation ends.
            ModelPart head = ((BipedEntityModel<PlayerEntityRenderState>) (Object) this).getHead();
            float aim = state.pitch * 0.017453292f;
            float k = Math.min(1.0f, Math.max(0.0f, FakeItem.getGlideProgress()));
            head.pitch = aim + (-0.7853982f - aim) * k;
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid PlayerEntityModelMixin threw", t);
        }
    }
}
