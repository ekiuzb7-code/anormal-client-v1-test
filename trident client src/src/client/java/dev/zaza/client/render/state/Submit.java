package dev.zaza.client.render.state;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import dev.zaza.client.mixin.accessor.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Submit {
    public static void submit(GuiGraphicsExtractor graphics, GuiElementRenderState state) {
        ((GuiGraphicsExtractorAccessor) graphics).zaza$getGuiRenderState().addGuiElement(state);
    }
}
