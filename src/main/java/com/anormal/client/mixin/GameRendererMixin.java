package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.NoHurtCam;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        NoHurtCam noHurtCam = ModuleManager.getModule(NoHurtCam.class);
        if (noHurtCam != null && noHurtCam.isEnabled()) {
            ci.cancel();
        }
    }
}
