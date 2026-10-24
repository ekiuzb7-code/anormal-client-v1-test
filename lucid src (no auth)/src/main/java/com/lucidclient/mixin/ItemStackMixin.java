package com.lucidclient.mixin;

import com.lucidclient.module.impl.WorthDisplay;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin that appends the DonutSMP "worth" line to an item's hover tooltip.
 *
 * <p>{@code ItemStack.getTooltip} is the single chokepoint every inventory /
 * hand / container hover goes through, so a tail-inject here covers the whole
 * client. The returned list is freshly built per call, so appending to it is
 * safe. Everything is gated on the Worth Display module being active AND the
 * current server being DonutSMP (the module itself decides that) — off-server
 * or with the module disabled the hook is a cheap no-op.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getTooltip", at = @At("TAIL"))
    private void lucid$appendWorthTooltip(TooltipContext context, PlayerEntity player,
                                          TooltipType type,
                                          CallbackInfoReturnable<List<Text>> cir) {
        try {
            if (!WorthDisplay.isActive()) return;
            if (!WorthDisplay.isOnDonutSMP()) return;
            List<Text> lines = cir.getReturnValue();
            if (lines == null) return;
            ItemStack self = (ItemStack) (Object) this;
            WorthDisplay.appendWorthLines(lines, self);
        } catch (Throwable ignored) {
            // Never let a tooltip decoration crash the frame.
        }
    }
}