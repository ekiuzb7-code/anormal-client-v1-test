package com.anormal.client.mixin;

import com.anormal.client.module.impl.optimization.AsyncChunkMeshing;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caps how many chunk rebuild tasks are queued per tick.
 *
 * <p>Chunk meshing already runs on worker threads, but queueing thousands of
 * tasks in one frame still costs real time on the main thread. This hook lets
 * {@code AsyncChunkMeshing} hold some of them back so the frame time stays
 * even.</p>
 */
@Mixin(ChunkBuilder.class)
public class ChunkBuilderMixin {

    @Inject(method = "rebuild", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$rebuild(CallbackInfo ci) {
        try {
            if (AsyncChunkMeshing.shouldSkip(0.0)) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
