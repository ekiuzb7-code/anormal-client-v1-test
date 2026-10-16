package com.anormal.client.mixin;

import com.anormal.client.module.impl.optimization.AsyncChunkMeshing;
import com.anormal.client.module.impl.optimization.ChunkUpdateLimiter;
import com.anormal.client.module.impl.optimization.FastRender;
import com.anormal.client.module.impl.optimization.NoAnimation;
import com.anormal.client.module.impl.optimization.NoRender;
import com.anormal.client.module.impl.optimization.SkyOptimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Render-side hooks for the Optimization category.
 *
 * <p>Each injector only cancels the specific sub-pass it owns, so the modules
 * compose freely: hiding the sky does not hide the weather, and so on. All of
 * them use {@code require = 0} so a mapping change degrades to "no effect"
 * rather than a crash.</p>
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    /** Rain and snow. */
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$renderWeather(CallbackInfo ci) {
        try {
            if (NoRender.hideWeather() || SkyOptimizer.hideWeatherLayer()
                    || FastRender.hideWeather() || NoAnimation.hideWeather()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

    /** The cloud layer. */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$renderClouds(CallbackInfo ci) {
        try {
            if (SkyOptimizer.hideCloudLayer() || FastRender.hideClouds()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    /** The sky dome, stars, sun and moon. */
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$renderSky(CallbackInfo ci) {
        try {
            if (SkyOptimizer.hideSkyDome() || FastRender.hideSky()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    /** The crack texture drawn while a block is being mined. */
    @Inject(method = "setBlockBreakingInfo", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$setBlockBreakingInfo(int entityId, BlockPos pos, int stage, CallbackInfo ci) {
        try {
            if (NoRender.hideBlockBreaking() || NoAnimation.hideBreakingAnimation()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Chunk rebuild scheduling - used by ChunkUpdateLimiter and
     * AsyncChunkMeshing. The arguments are chunk coordinates, so they are
     * converted to a block distance from the player before the budget check.
     */
    @Inject(method = "scheduleChunkRender(IIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$scheduleChunkRender(int x, int y, int z, boolean important, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            int dx = (x << 4) + 8 - mc.player.getBlockX();
            int dz = (z << 4) + 8 - mc.player.getBlockZ();

            if (ChunkUpdateLimiter.shouldBlock(dx, dz)) {
                ci.cancel();
                return;
            }
            double distSq = (double) dx * dx + (double) dz * dz;
            if (AsyncChunkMeshing.shouldSkip(distSq)) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
