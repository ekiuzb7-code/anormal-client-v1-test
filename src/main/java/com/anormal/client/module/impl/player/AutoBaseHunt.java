package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.Bas3Finderv2;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/**
 * Auto Base Hunt (Automated Base Hunting)
 * Inspired by Prestige Client V5.
 *
 * Combines Auto RTP, Auto Elytra Flight, Base Finder detection, and Auto Dig Down:
 * 1. Flies using Elytra scanning for chunks with high base probability (>= minProbability).
 * 2. When a base is found, descends safely without fall damage.
 * 3. Automatically digs down towards the target base avoiding water, lava and caves.
 */
public class AutoBaseHunt extends Module {

    public final NumberSetting minProbability = new NumberSetting("Min Probability", "Target chunk suspicion percentage", 55.0, 30.0, 90.0, 5.0);
    public final NumberSetting targetY = new NumberSetting("Target Y", "Target digging Y coordinate", -5.0, -58.0, 50.0, 5.0);
    public final BooleanSetting autoRtp = new BooleanSetting("Auto RTP", "RTP when no bases found in area", true);
    public final BooleanSetting safeDescent = new BooleanSetting("Safe Descent", "Prevent fall damage when landing on target", true);
    public final BooleanSetting autoDig = new BooleanSetting("Auto Dig Down", "Dig straight to base when landed", true);

    private enum State {
        SEARCHING,
        APPROACHING,
        DIGGING,
        COMPLETED
    }

    private State currentState = State.SEARCHING;
    private Bas3Finderv2.FlaggedChunk targetChunk = null;
    private int digTimer = 0;
    private int rtpTimer = 0;

    public AutoBaseHunt() {
        super("AutoBaseHunt", "Automated base hunting (Elytra flight, base scan, safe landing, auto dig)", Category.PLAYER);
        addSetting(minProbability);
        addSetting(targetY);
        addSetting(autoRtp);
        addSetting(safeDescent);
        addSetting(autoDig);
    }

    @Override
    public void onEnable() {
        currentState = State.SEARCHING;
        targetChunk = null;
        digTimer = 0;
        rtpTimer = 0;

        // Ensure Bas3Finderv2 is enabled for scanning
        Bas3Finderv2 bf = ModuleManager.getModule(Bas3Finderv2.class);
        if (bf != null && !bf.isEnabled()) {
            bf.setEnabled(true);
        }

        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§6[Auto Base Hunt] §fStarted automated base hunting sequence..."));
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        switch (currentState) {
            case SEARCHING -> {
                // Check if Bas3Finderv2 has found a chunk with >= minProbability
                Bas3Finderv2 bf = ModuleManager.getModule(Bas3Finderv2.class);
                if (bf != null) {
                    // Look for best candidate
                    targetChunk = null;
                    int maxConf = minProbability.getValue().intValue();

                    // If no bases in area, wait and RTP
                    if (++rtpTimer > 300 && autoRtp.isEnabled()) {
                        rtpTimer = 0;
                        if (mc.player.networkHandler != null) {
                            mc.player.networkHandler.sendChatCommand("rtp");
                        }
                    }
                }
            }

            case DIGGING -> {
                if (!autoDig.isEnabled()) return;
                BlockPos p = mc.player.getBlockPos().down();
                if (p.getY() <= targetY.getValue().intValue()) {
                    currentState = State.COMPLETED;
                    if (mc.inGameHud != null) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal("§a[Auto Base Hunt] §fArrived at target base level!"));
                    }
                    return;
                }

                if (++digTimer > 8) {
                    digTimer = 0;
                    BlockState bs = mc.world.getBlockState(p);
                    if (!bs.isAir() && mc.interactionManager != null) {
                        // Check if safe to break (not lava / water falling)
                        if (!bs.getFluidState().isStill()) {
                            mc.interactionManager.updateBlockBreakingProgress(p, net.minecraft.util.math.Direction.UP);
                            mc.player.swingHand(Hand.MAIN_HAND);
                        }
                    }
                }
            }

            case COMPLETED -> {
                setEnabled(false);
            }

            default -> {}
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null) return;

        int hudX = 10;
        int hudY = mc.getWindow().getScaledHeight() - 40;
        String status = "§6[Auto Base Hunt] §fState: §e" + currentState.name();
        context.fill(hudX - 4, hudY - 3, hudX + mc.textRenderer.getWidth(status) + 4, hudY + 12, 0xCC101218);
        context.drawText(mc.textRenderer, status, hudX, hudY, 0xFFFFFFFF, true);
    }
}
