package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.client.NameProtectV2;
import com.anormal.client.module.impl.player.FakeStats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends the fake rank to your own name in the tab list.
 *
 * <p>FakeStats replaces sidebar values, but the tab list builds its text through
 * {@code PlayerListHud.getPlayerName}, so the rank needs its own hook. Only the local player's
 * entry is touched - prefixing everyone would be instantly obvious.</p>
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$prependFakeRank(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        try {
            if (!FakeStats.isActive()) return;

            String rank = FakeStats.getFakeRank();
            if (rank == null || rank.isEmpty()) return;

            // Server scope: only DonutSMP, otherwise the rank makes no sense.
            if (!NameProtectV2.isOnDonutSMPDebug()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            java.util.UUID id = entryId(entry);
            if (id == null || !id.equals(mc.player.getUuid())) return;

            Text original = cir.getReturnValue();
            if (original == null) return;

            MutableText prefixed = Text.empty()
                    .append(Text.literal("[" + rank + "] ").formatted(Formatting.GOLD))
                    .append(original);
            cir.setReturnValue(prefixed);
        } catch (Throwable t) {
            LucidLog.LOGGER.error("tab list rank prefix threw", t);
        }
    }

    /** The profile became a record in modern authlib, so the id accessor is {@code id()}. */
    private static java.util.UUID entryId(PlayerListEntry entry) {
        try {
            if (entry == null || entry.getProfile() == null) return null;
            return entry.getProfile().id();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
