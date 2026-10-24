package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Speeds up block breaking, ported from Wurst's FastBreak.
 *
 * <p>Two separate mechanisms:</p>
 * <ul>
 *   <li><b>No delay</b> - the vanilla 5 tick {@code blockBreakingCooldown} is zeroed, so blocks
 *       can be mined back to back at the tool's real speed.</li>
 *   <li><b>Early finish</b> - while a block is being broken a {@code STOP_DESTROY_BLOCK} action is
 *       sent before the mc thinks it is done, which makes the server complete the break
 *       immediately.</li>
 * </ul>
 *
 * <p>{@code Legit Mode} keeps only the first mechanism, which is much slower but far less likely
 * to be flagged. {@code Activation Chance} lets only some blocks use the second mechanism.</p>
 */
public class FastBreak extends Module {

    public final NumberSetting activationChance = new NumberSetting("Activation Chance",
            "Only fast-break this percentage of blocks (ignored in Legit Mode)", 100.0, 1.0, 100.0, 1.0);
    public final BooleanSetting legitMode = new BooleanSetting("Legit Mode",
            "Only remove the delay between blocks, never finish a block early", false);
    public final BooleanSetting ignoreUnbreakable = new BooleanSetting("Ignore Unbreakable",
            "Never send the early finish for bedrock, barriers and similar", true);

    private BlockPos lastPos;
    private boolean fastBreakBlock;

    public FastBreak() {
        super("FastBreak", "Breaks blocks faster by removing the delay and finishing early", Category.WORLD);
        addSetting(activationChance);
        addSetting(legitMode);
        addSetting(ignoreUnbreakable);
    }

    @Override
    public void onDisable() {
        lastPos = null;
        fastBreakBlock = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // The delay is also cleared from the interaction manager mixin; doing it here as well
        // covers the ticks where no breaking progress is sent.
        if (legitMode.isEnabled()) return;

        if (!mc.interactionManager.isBreakingBlock()) {
            lastPos = null;
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHit)) {
            lastPos = null;
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (!pos.equals(lastPos)) {
            lastPos = pos;
            fastBreakBlock = Math.random() * 100.0 < activationChance.getValue();
        }
        if (!fastBreakBlock) return;

        if (ignoreUnbreakable.isEnabled()) {
            try {
                BlockState state = mc.world.getBlockState(pos);
                if (state.isAir()) return;
                if (state.getHardness(mc.world, pos) < 0.0f) return;
            } catch (Throwable ignored) {
            }
        }

        try {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, blockHit.getSide()));
            }
        } catch (Throwable ignored) {
        }
    }
}
