package com.lucidclient.mixin;

import com.lucidclient.module.impl.FakeStats;
import com.lucidclient.module.impl.NameProtect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin that hides the local player's username inside the DonutSMP
 * scoreboard sidebar by swapping the drawn {@link Text} for any sidebar line
 * that contains the username.
 *
 * <h3>Why {@code @ModifyArg} on {@code drawText}</h3>
 * Earlier approaches failed because DonutSMP's scoreboard uses <b>fake
 * colour-code score holders</b> (the {@code ScoreboardEntry.owner} is a single
 * {@code §} character, not a player name), does <b>not</b> use vanilla team
 * prefix/suffix (so {@code Team.decorateName} is never called), and the
 * {@code SidebarEntry} record that holds the final line text is
 * <b>package-private</b> (can't be referenced or constructed from a mixin).
 *
 * <p>The one point that always runs and that we CAN hook cleanly is the actual
 * {@link DrawContext#drawText} call inside
 * {@code InGameHud.renderScoreboardSidebar(DrawContext, ScoreboardObjective)}.
 * Vanilla calls {@code drawText} three times per render: once for the title
 * and once each for the {@code name} and {@code score} of every sidebar line.
 * We {@link ModifyArg @ModifyArg} the {@code Text} argument (index 1) of every
 * {@code drawText(TextRenderer, Text, int, int, int, boolean)} call scoped to
 * {@code renderScoreboardSidebar}, and if the text contains the local
 * player's username as a substring, replace it with the chosen preset.
 *
 * <p>This is scoped to the sidebar method only, so it never touches chat, the
 * tab list, nametags, or any other {@code drawText} caller. Only the local
 * player's own username is replaced — other players' lines don't contain
 * {@code player.getNameForScoreboard()} and pass through intact. No reference
 * to the package-private {@code SidebarEntry} type is needed at all.
 */
@Mixin(InGameHud.class)
public abstract class ScoreboardEntryMixin {

    /**
     * Swap the {@code Text} argument (index 1) of every
     * {@code drawText(TextRenderer, Text, int, int, int, boolean)} call inside
     * {@code renderScoreboardSidebar(DrawContext, ScoreboardObjective)} when it
     * contains the local player's username.
     *
     * <p>The {@code at = @At("INVOKE", ...)} with {@code ordinal = 0} would only
     * hit the first call; we omit {@code ordinal} so the modifier applies to
     * <i>all</i> matching {@code drawText} invocations in the method (title +
     * every line's name + score).
     */
    @ModifyArg(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"
        ),
        index = 1
    )
    private Text lucid$hideNameInSidebar(Text text) {
        try {
            // Chain two transforms in one pass over every sidebar drawText call:
            //   1. NameProtect — swap the local player's username for a preset.
            //   2. FakeStats — swap Money/Shards/Playtime/Rank values for fakes.
            // Each returns null to mean "no change"; the original text passes
            // through. Both are gated on DonutSMP + module-active internally.
            Text t = NameProtect.replaceUsernameInText(text);
            if (t == null) t = text;
            Text t2 = FakeStats.replaceStatsInText(t);
            if (t2 != null) t = t2;
            return t;
        } catch (Throwable t) {
            com.lucidclient.LucidClient.LOGGER.error("Lucid sidebar @ModifyArg threw", t);
            return text;
        }
    }
}