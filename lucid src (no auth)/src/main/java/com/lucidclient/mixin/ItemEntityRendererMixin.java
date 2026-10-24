package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dropped-item-entity surface for the Fake Item module.
 *
 * <p>{@link net.minecraft.client.render.entity.ItemEntityRenderer#updateRenderState}
 * bakes the rendered item from {@link ItemEntity#getStack()} (passed to the
 * render state's {@code update(entity, stack, modelManager)}). This mixin
 * {@link Redirect @Redirect}s that single call, swapping in the fake stack
 * when the module's "Forge World Items" toggle is on.
 *
 * <p><b>Visual scope note:</b> with single-held-slot semantics the only way a
 * fake item can appear on a dropped entity is to substitute <i>every</i>
 * dropped item entity while the module is active — which is why the toggle
 * defaults OFF. When ON, this hook makes <i>every</i> dropped item render as
 * the spawned fake stack (the server's real dropped item is untouched).
 */
@Mixin(net.minecraft.client.render.entity.ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {

    @Redirect(
        method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/ItemEntity;getStack()Lnet/minecraft/item/ItemStack;"
        )
    )
    private ItemStack lucid$overrideDroppedItem(ItemEntity entity) {
        try {
            // Only on a dedicated server — singleplayer drops are real.
            if (FakeItem.isActive() && FakeItem.isDedicatedServer()
                && FakeItem.isForgingWorldItems()) {
                ItemStack fake = FakeItem.getFakeStack();
                if (!fake.isEmpty()) return fake;
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ItemEntityRendererMixin threw", t);
        }
        return entity.getStack();
    }
}
