package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Renders dropped items as the fake stack.
 *
 * <p>Dropped-item entities carry their stack in the render state, so the only place the stack can
 * be swapped is the {@code getStack()} call the renderer makes. On a dedicated server that stack
 * comes from the server, which is exactly what we want to override.</p>
 */
@Mixin(ItemEntityRenderer.class)
public abstract class FakeItemEntityRendererMixin {

    @Redirect(
            method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/ItemEntity;getStack()Lnet/minecraft/item/ItemStack;"),
            require = 0
    )
    private ItemStack anormal$overrideDroppedItem(ItemEntity entity) {
        try {
            if (FakeItem.isActive() && FakeItem.isDedicatedServer() && FakeItem.isForgingWorldItems()) {
                ItemStack fake = FakeItem.getFakeStack();
                if (!fake.isEmpty()) return fake;
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("dropped item render threw", t);
        }
        return entity.getStack();
    }
}
