package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.combat.BlockHit;
import com.anormal.client.module.impl.combat.Criticals;
import com.anormal.client.module.impl.combat.HitFlick;
import com.anormal.client.module.impl.combat.HitSelect;
import com.anormal.client.module.impl.combat.WTap;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The single choke point for every attack, whether it comes from a vanilla
 * click or from our own KillAura.
 *
 * <p>Five combat modules need to run at exactly this moment, so they are all
 * driven from here instead of from each module's own tick:</p>
 * <ul>
 *   <li>{@code HitSelect} - may hold the attack back for better timing, so it
 *       runs first and can cancel.</li>
 *   <li>{@code Criticals} - has to fake the fall <i>before</i> the server
 *       processes the hit, otherwise the crit does not count.</li>
 *   <li>{@code WTap}, {@code HitFlick} and {@code BlockHit} - all react
 *       <i>after</i> the hit has landed.</li>
 * </ul>
 */
@Mixin(ClientPlayerInteractionManager.class)
public class InteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$beforeAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            if (player == null || target == null) return;

            HitSelect hitSelect = ModuleManager.getModule(HitSelect.class);
            if (hitSelect != null && hitSelect.isEnabled() && !hitSelect.shouldAttack()) {
                ci.cancel();
                return;
            }

            Criticals criticals = ModuleManager.getModule(Criticals.class);
            if (criticals != null && criticals.isEnabled()) {
                criticals.doCrit();
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "attackEntity", at = @At("RETURN"), require = 0)
    private void anormal$afterAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            if (player == null || target == null) return;

            WTap wTap = ModuleManager.getModule(WTap.class);
            if (wTap != null && wTap.isEnabled()) wTap.onAttack();

            HitFlick hitFlick = ModuleManager.getModule(HitFlick.class);
            if (hitFlick != null && hitFlick.isEnabled()) hitFlick.onAttack();

            BlockHit blockHit = ModuleManager.getModule(BlockHit.class);
            if (blockHit != null && blockHit.isEnabled()) blockHit.onAttack();
        } catch (Throwable ignored) {
        }
    }
}
