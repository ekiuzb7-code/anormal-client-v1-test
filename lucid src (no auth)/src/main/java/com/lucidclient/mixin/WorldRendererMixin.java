package com.lucidclient.mixin;

import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam (Freecam-mod implementation) Show Player: while the camera is
 * detached, the real player's render state is injected into the world render
 * pass so the frozen body renders in the world (with an optional glow
 * outline). Vanilla culls the camera entity's own player from this pass.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Shadow
    protected abstract EntityRenderState getAndUpdateRenderState(Entity entity, float tickProgress);

    @Inject(method = "fillEntityRenderStates", at = @At("RETURN"))
    private void lucid$showPlayer(Camera camera, Frustum frustum, RenderTickCounter tickCounter, WorldRenderState renderStates, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || !Freecam.isActive() || !Freecam.shouldShowPlayer()) return;
            float tickProgress = tickCounter.getTickProgress(false);
            EntityRenderState state = this.getAndUpdateRenderState(mc.player, tickProgress);
            renderStates.entityRenderStates.add(state);
            if (Freecam.isOutlineEnabled()) {
                renderStates.hasOutline = true;
            }
        } catch (Throwable t) {
            com.lucidclient.LucidClient.LOGGER.error("Lucid WorldRendererMixin threw", t);
        }
    }
}
