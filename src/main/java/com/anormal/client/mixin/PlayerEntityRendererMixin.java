package com.anormal.client.mixin;

import com.anormal.client.util.PlayerColorizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps the player skin for a generated solid / tinted texture while the body colouring
 * of PlayerESP is active.
 *
 * <p>1.21.11 builds the model through {@code OrderedRenderCommandQueue}, so the skin
 * identifier returned here is the last point where a colour can still be applied.</p>
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(
            method = "getTexture(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)Lnet/minecraft/util/Identifier;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void anormal$applyBodyColor(PlayerEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
        if (!PlayerColorizer.isActive()) return;

        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            PlayerEntity player = anormal$resolve(mc, state);
            if (player == null) return;

            Identifier replacement = PlayerColorizer.textureFor(player, state.skinTextures.body().texturePath());
            if (replacement != null) cir.setReturnValue(replacement);
        } catch (Throwable ignored) {
        }
    }

    /**
     * The render state only carries an entity id, and that id is not guaranteed to be
     * resolvable (the entity may have been removed between the tick and the draw), so the
     * display name is used as a fallback.
     */
    private static PlayerEntity anormal$resolve(MinecraftClient mc, PlayerEntityRenderState state) {
        Entity entity = mc.world.getEntityById(state.id);
        if (entity instanceof PlayerEntity player) return player;

        if (state.playerName == null) return null;
        String name = state.playerName.getString();
        for (PlayerEntity candidate : mc.world.getPlayers()) {
            if (candidate.getName().getString().equals(name)) return candidate;
        }
        return null;
    }
}
