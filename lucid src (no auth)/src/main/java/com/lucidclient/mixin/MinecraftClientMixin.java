package com.lucidclient.mixin;

import com.lucidclient.module.impl.AnchorMacro;
import com.lucidclient.module.impl.Freecam;
import com.lucidclient.util.MouseSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-level hooks for the ported Argon modules:
 *
 * <ul>
 *   <li><b>Freecam</b> (Freecam-mod implementation): blocks attacks / item
 *       use / block breaking / pick-block while the camera is detached and
 *       interactions are disabled, diverts hotbar keys into the tripod
 *       system while the freecam key is held, and disables freecam on
 *       disconnect.</li>
 *   <li><b>Anchor Macro</b> (Argon functionality): fires the item-use hook
 *       so the macro can track which anchors the player placed ("Only Own")
 *       and clear the detonated ones.</li>
 *   <li><b>MouseSimulation</b> (Anchor Macro's Click Simulation): consumes
 *       the pending simulated click at the vanilla doItemUse/doAttack/
 *       handleBlockBreaking entry points so the click runs the vanilla input
 *       path exactly once.</li>
 * </ul>
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Shadow
    public ClientWorld world;

    @Shadow
    public GameOptions options;

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void lucid$onItemUse(CallbackInfo ci) {
        // Anchor Macro owns right-click use while sequencing an anchor —
        // cancel vanilla so its own 4-tick use cooldown can't charge or
        // detonate the anchor ahead of the macro's paced clicks.
        if (AnchorMacro.shouldOwnClick()) {
            ci.cancel();
            return;
        }

        // Anchor Macro: track anchor ownership on every vanilla item use.
        AnchorMacro.onVanillaItemUse();

        if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
            ci.cancel();
        }

        if (lucid$freecamBlockInteract()) ci.cancel();
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void lucid$onAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
            MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
            cir.setReturnValue(false);
        }

        if (lucid$freecamBlockInteract()) cir.setReturnValue(false);
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void lucid$onItemPick(CallbackInfo ci) {
        if (lucid$freecamBlockInteract()) ci.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void lucid$onBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
            MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
            ci.cancel();
        }

        if (lucid$freecamBlockInteract()) ci.cancel();
    }

    /**
     * Tripod cameras: while the freecam key (or reset-tripod key) is held,
     * hotbar number presses open/reset that tripod camera instead of
     * switching the player's selected slot.
     */
    @Inject(method = "handleInputEvents", at = @At("HEAD"))
    private void lucid$onHandleInputEvents(CallbackInfo ci) {
        if (!Freecam.shouldBlockHotbar() || options == null) return;
        KeyBinding[] keys = options.hotbarKeys;
        for (int i = 0; i < keys.length; i++) {
            while (keys[i].wasPressed()) {
                Freecam.onHotbarSlot(i);
            }
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;ZZ)V", at = @At("HEAD"))
    private void lucid$onDisconnect(CallbackInfo ci) {
        if (Freecam.instance() != null) {
            Freecam.instance().onDisconnect();
        }
    }

    @Unique
    private static boolean lucid$freecamBlockInteract() {
        return Freecam.isActive() && !Freecam.isPlayerControlEnabled() && Freecam.shouldPreventInteractions();
    }
}
