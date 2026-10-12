package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables Minecraft's "smart culling" while X-Ray is active.
 *
 * <p>Vanilla marks a chunk section as closed whenever it is surrounded by
 * opaque full cubes, which lets the renderer skip it entirely. Underground
 * that means whole chunks get skipped and X-Ray shows nothing. Cancelling
 * {@code markClosed} keeps every section visible.</p>
 */
@Mixin(ChunkOcclusionDataBuilder.class)
public class ChunkOcclusionDataBuilderMixin {

    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$markClosed(BlockPos pos, CallbackInfo ci) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray != null && xray.isEnabled() && xray.isRealMode()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
