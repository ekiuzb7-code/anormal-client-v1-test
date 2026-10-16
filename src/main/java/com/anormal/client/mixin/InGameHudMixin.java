package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.optimization.NoAnimation;
import com.anormal.client.module.impl.optimization.NoRender;
import com.anormal.client.module.impl.render.CustomCrosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD hooks shared by two categories.
 *
 * <ul>
 *   <li>{@code CustomCrosshair} suppresses the vanilla crosshair.</li>
 *   <li>{@code NoRender} / {@code NoAnimation} suppress individual screen
 *       overlays (vignette, portal, nausea, fire, water, boss bar, ...).</li>
 * </ul>
 *
 * <p>Fire / water / lava overlays are all drawn through the generic
 * {@code renderOverlay(DrawContext, Identifier, float)} method, so the texture
 * path is what tells them apart.</p>
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$renderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            CustomCrosshair crosshair = ModuleManager.getModule(CustomCrosshair.class);
            if (crosshair != null && crosshair.shouldHideVanilla()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Fire, water and lava screen tints. */
    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$renderOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        try {
            if (texture == null) return;
            String path = texture.getPath().toLowerCase();
            if (path.contains("fire") && (NoRender.hideFireOverlay() || NoAnimation.hideFireOverlay())) {
                ci.cancel();
            } else if (path.contains("underwater") && NoRender.hideWaterOverlay()) {
                ci.cancel();
            } else if (path.contains("lava") && NoRender.hideLavaOverlay()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$vignette(DrawContext context, Entity entity, CallbackInfo ci) {
        try {
            if (NoRender.hideVignette()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$portal(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        try {
            if (NoRender.hidePortalOverlay() || NoAnimation.hidePortalOverlay()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$nausea(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        try {
            if (NoRender.hideNausea() || NoAnimation.hideNausea()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$spyglass(DrawContext context, float scale, CallbackInfo ci) {
        try {
            if (NoRender.hideSpyglass()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderSleepOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$sleep(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            if (NoRender.hideSleep()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$statusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            if (NoRender.hideStatusEffects()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderBossBarHud", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$bossBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            if (NoRender.hideBossBar()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$scoreboard(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            if (NoRender.hideScoreboard()) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
