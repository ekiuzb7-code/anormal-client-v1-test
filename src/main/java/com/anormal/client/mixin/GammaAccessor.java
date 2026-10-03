package com.anormal.client.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface GammaAccessor {
    @Accessor("value")
    void setRawValue(Object value);
}
