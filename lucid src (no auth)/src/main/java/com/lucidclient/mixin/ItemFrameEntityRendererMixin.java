package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeItem;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Item-frame surface for the Fake Item module.
 *
 * <p>{@link net.minecraft.client.render.entity.ItemFrameEntityRenderer#updateRenderState}
 * bakes the rendered framed item from {@link ItemFrameEntity#getHeldItemStack()}
 * (passed to {@code ItemModelManager.updateForNonLivingEntity}). This mixin
 * {@link Redirect @Redirect}s that single call, swapping in the fake stack
 * when the module's "Forge World Items" toggle is on.
 *
 * <p>Same visual-scope caveat as the dropped-entity mixin: with single-held-slot
 * semantics this hook substitutes <i>every</i> framed item while the module +
 * toggle are active — which is why the toggle defaults OFF. The server's real
 * framed items are untouched.
 */
@Mixin(net.minecraft.client.render.entity.ItemFrameEntityRenderer.class)
public abstract class ItemFrameEntityRendererMixin {

    @Redirect(
        method = "updateRenderState(Lnet/minecraft/entity/decoration/ItemFrameEntity;Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/decoration/ItemFrameEntity;getHeldItemStack()Lnet/minecraft/item/ItemStack;"
        )
    )
    private ItemStack lucid$overrideFramedItem(ItemFrameEntity frame) {
        try {
            if (FakeItem.isActive() && FakeItem.isDedicatedServer()) {
                // 1) Per-frame placement — a frame the user has explicitly
                //    right-clicked to insert the fake into (clientside only).
                //    Highest priority: shows the fake only in that one frame.
                if (FakeItem.isFrameFaked(frame.getId())) {
                    ItemStack fake = FakeItem.getFakeStack();
                    if (!fake.isEmpty()) return fake;
                }
                // 2) Global "Forge World Items" toggle — overlay the fake on
                //    every dropped item entity and every item frame while on.
                if (FakeItem.isForgingWorldItems()) {
                    ItemStack fake = FakeItem.getFakeStack();
                    if (!fake.isEmpty()) return fake;
                }
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ItemFrameEntityRendererMixin threw", t);
        }
        return frame.getHeldItemStack();
    }
}
