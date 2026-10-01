package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void onRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ModuleManager.onRender2D(context, tickCounter.getTickDelta(false));
    }
}
