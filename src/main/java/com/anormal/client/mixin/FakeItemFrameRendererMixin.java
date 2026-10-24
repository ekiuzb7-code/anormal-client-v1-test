package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Renders item frames as holding the fake stack.
 *
 * <p>Two sources: a frame the player explicitly right-clicked (per-frame, highest priority) and
 * the global "Forge World Items" toggle, which overlays the fake on every frame while it is on.</p>
 */
@Mixin(ItemFrameEntityRenderer.class)
public abstract class FakeItemFrameRendererMixin {

    @Redirect(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/ItemFrameEntity;Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;F)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/decoration/ItemFrameEntity;getHeldItemStack()Lnet/minecraft/item/ItemStack;"),
            require = 0
    )
    private ItemStack anormal$overrideFramedItem(ItemFrameEntity frame) {
        try {
            if (FakeItem.isActive() && FakeItem.isDedicatedServer()) {
                if (FakeItem.isFrameFaked(frame.getId())) {
                    ItemStack fake = FakeItem.getFakeStack();
                    if (!fake.isEmpty()) return fake;
                }
                if (FakeItem.isForgingWorldItems()) {
                    ItemStack fake = FakeItem.getFakeStack();
                    if (!fake.isEmpty()) return fake;
                }
            }
        } catch (Throwable t) {
            LucidLog.LOGGER.error("item frame render threw", t);
        }
        return frame.getHeldItemStack();
    }
}
