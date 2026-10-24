package com.lucidclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin exposing {@link MinecraftClient}'s private fields: the
 * {@code mouse} field for MouseSimulation (Anchor Macro's Click Simulation),
 * and the cooldown fields used by AccuratePlacement / AutoCrystal /
 * DoubleAnchor / LungeSwapper to reset item-use & attack cooldowns.
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("mouse")
    Mouse lucid$getMouse();

    @Accessor("itemUseCooldown")
    void lucid$setItemUseCooldown(int value);

    @Accessor("attackCooldown")
    void lucid$setAttackCooldown(int value);
}