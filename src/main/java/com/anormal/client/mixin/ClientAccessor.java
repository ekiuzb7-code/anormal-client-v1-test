package com.anormal.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface ClientAccessor {
    @Accessor("itemUseCooldown")
    int getItemUseCooldown();

    @Accessor("itemUseCooldown")
    void setItemUseCooldown(int cooldown);

    // attackCooldown is protected in MinecraftClient, so it needs an accessor.
    @Accessor("attackCooldown")
    void setAttackCooldown(int cooldown);
}
