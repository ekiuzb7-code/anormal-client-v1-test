package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeStats;
import com.lucidclient.module.impl.NameProtect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TAB-list rank surface for the FakeStats module.
 *
 * <p>{@link net.minecraft.client.gui.hud.PlayerListHud#getPlayerName(PlayerListEntry)}
 * is the single chokepoint that computes the {@link Text} displayed for one
 * tab-list entry (player name + the team/formatting the server applied). This
 * mixin {@link Inject @Inject}s at {@code RETURN} and, when the entry belongs
 * to the <b>local player</b> AND we're on DonutSMP AND FakeStats is active AND
 * a fake rank is configured, returns a new {@link Text} with the fake rank
 * prefixed in gold (matching the conventional DonutSMP rank color) followed by
 * the original name verbatim, so any styling the server applied to the name is
 * preserved.
 *
 * <p>Other players' entries pass through untouched (the local-player gate
 * uses {@code client.player.getNameForScoreboard()} substring/equality
 * matching the entry's profile username, mirroring {@code NameProtect}'s
 * detection pattern). Off-server the gate ({@link NameProtect#isOnDonutSMP()})
 * short-circuits false so the mod stays inert.
 */
@Mixin(net.minecraft.client.gui.hud.PlayerListHud.class)
public abstract class PlayerListHudMixin {

    @Inject(
        method = "getPlayerName(Lnet/minecraft/client/network/PlayerListEntry;)Lnet/minecraft/text/Text;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void lucid$prependFakeRank(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        try {
            if (!FakeStats.isActive()) return;
            String rank = FakeStats.getFakeRank();
            if (rank == null || rank.isEmpty()) return;

            // Server scope — only DonutSMP. Reuses NameProtect's public
            // detector (the public variant of the on-server check).
            if (!NameProtect.isOnDonutSMPDebug()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            // Local-player gate — only prepend the rank to OUR OWN tab entry.
            // UUID match is the most robust check (the entry's GameProfile.id()
            // is the record accessor; GameProfile became a Record in modern
            // authlib so it's id()/name(), not getId()/getName()).
            java.util.UUID entryId = entryId(entry);
            if (entryId == null) return;
            if (!entryId.equals(client.player.getUuid())) return;

            Text original = cir.getReturnValue();
            if (original == null) return;

            // Prepend "[Rank] " in gold before the original (the original's own
            // styling — team color, §-codes — is preserved verbatim by appending).
            MutableText prefixed = Text.empty()
                .append(Text.literal("[" + rank + "] ").formatted(Formatting.GOLD))
                .append(original);
            cir.setReturnValue(prefixed);
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid PlayerListHudMixin threw", t);
        }
    }

    /** Defensive read of the entry's profile UUID; null on any failure. */
    private static java.util.UUID entryId(PlayerListEntry entry) {
        try {
            if (entry == null) return null;
            if (entry.getProfile() == null) return null;
            return entry.getProfile().id();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
