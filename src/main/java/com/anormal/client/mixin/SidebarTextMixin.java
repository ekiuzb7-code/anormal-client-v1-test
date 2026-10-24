package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.client.NameProtectV2;
import com.anormal.client.module.impl.player.FakeStats;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Rewrites the text the scoreboard sidebar draws.
 *
 * <p>Servers put your name and your stats in the sidebar, so the client receives the real values
 * no matter what the local player believes. Both modules therefore have to intercept the draw
 * call, not the data: the sidebar renders through
 * {@code DrawContext.drawText(TextRenderer, Text, int, int, int, boolean)}, so the {@code Text}
 * argument (index 1) is replaced on its way in.</p>
 *
 * <p>Two transforms are chained per call - name first, then stats - and each returns the input
 * unchanged when it has nothing to do, so the vanilla text always survives intact.</p>
 */
@Mixin(InGameHud.class)
public class SidebarTextMixin {

    @ModifyArg(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V"
            ),
            index = 1,
            require = 0
    )
    private Text anormal$rewriteSidebarText(Text text) {
        try {
            Text result = text;

            Text protectedName = NameProtectV2.replaceUsernameInText(result);
            if (protectedName != null) result = protectedName;

            Text fakedStats = FakeStats.replaceStatsInText(result);
            if (fakedStats != null) result = fakedStats;

            return result;
        } catch (Throwable t) {
            LucidLog.LOGGER.error("sidebar text rewrite threw", t);
            return text;
        }
    }
}
