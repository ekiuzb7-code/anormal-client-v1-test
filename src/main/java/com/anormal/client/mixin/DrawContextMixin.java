package com.anormal.client.mixin;

import com.anormal.client.util.HudStyle;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes every HUD module measurable, and optionally adds a glow to its text.
 *
 * <p>Measurement used to rely on {@code RenderUtils.fill}, so modules that drew straight through
 * {@code DrawContext} (the music card, the new glass panels) reported no bounds at all - the HUD
 * editor showed a 90x14 box and the drag felt wrong. Recording the raw fill calls here means the
 * editor and the outline overlay see every overlay, whichever API it uses.</p>
 */
@Mixin(DrawContext.class)
public class DrawContextMixin {

    @Inject(method = "fill(IIIII)V", at = @At("HEAD"), require = 0)
    private void anormal$recordFill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        try {
            if (RenderUtils.isMeasuring()) {
                RenderUtils.recordBounds(x1, y1, x2, y2);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(
            method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
            at = @At("HEAD"),
            require = 0
    )
    private void anormal$textGlow(TextRenderer tr, Text text, int x, int y, int color,
                                  boolean shadow, CallbackInfo ci) {
        try {
            HudStyle.beforeText((DrawContext) (Object) this, tr, text, x, y);
        } catch (Throwable ignored) {
        }
    }
}
