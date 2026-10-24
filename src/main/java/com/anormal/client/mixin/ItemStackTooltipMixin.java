package com.anormal.client.mixin;

import com.anormal.client.module.impl.misc.WorthDisplay;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Appends the DonutSMP worth line to item tooltips.
 *
 * <p>WorthDisplay has no tick of its own - it only knows how to decorate a tooltip - so without
 * this hook the module is completely inert. Hooking the tail of {@code getTooltip} means the line
 * lands after the vanilla lore instead of replacing it.</p>
 */
@Mixin(ItemStack.class)
public class ItemStackTooltipMixin {

    @Inject(method = "getTooltip", at = @At("TAIL"), require = 0)
    private void anormal$appendWorth(Item.TooltipContext context, PlayerEntity player,
                                     TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        try {
            if (!WorthDisplay.isActive()) return;
            if (!WorthDisplay.isOnDonutSMP()) return;

            List<Text> lines = cir.getReturnValue();
            if (lines == null || lines.isEmpty()) return;

            WorthDisplay.appendWorthLines(lines, (ItemStack) (Object) this);
        } catch (Throwable ignored) {
            // A tooltip decoration must never break the frame.
        }
    }
}
