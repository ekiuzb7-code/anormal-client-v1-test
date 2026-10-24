package dev.zaza.client.mixin.render;

import dev.zaza.client.render.GlassRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Inject(method = "render()V", at = @At("HEAD"))
    private void zaza$renderGlass(CallbackInfo info) {
        GlassRenderer.render();
    }
}
