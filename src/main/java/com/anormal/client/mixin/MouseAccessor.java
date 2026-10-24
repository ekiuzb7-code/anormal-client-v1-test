package com.anormal.client.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for {@link Mouse#onMouseButton} — lets MouseSimulation dispatch a
 * synthetic click through the vanilla input pipeline (Anchor Macro's Click
 * Simulation feature).
 */
@Mixin(Mouse.class)
public interface MouseAccessor {

    @Invoker("onMouseButton")
    void lucid$press(long window, MouseInput input, int action);
}
