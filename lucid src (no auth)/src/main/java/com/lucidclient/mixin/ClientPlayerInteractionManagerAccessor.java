package com.lucidclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for {@link ClientPlayerInteractionManager}'s private mining
 * state fields, used by the AutoMine module to drive faster-than-vanilla
 * block breaking without re-implementing the packet/sequence logic.
 *
 * <p>The fields exposed here are the vanilla per-tick breaking gates:
 * <ul>
 *   <li>{@code blockBreakingCooldown} — an int tick cooldown that vanilla
 *       sets to 5 after each break/tick and decrements each tick. While
 *       &gt; 0, {@code updateBlockBreakingProgress} early-returns without
 *       accumulating progress. Resetting this to 0 each tick removes the
 *       vanilla 5-tick post-break gate so AutoMine can immediately start
 *       the next block.</li>
 *   <li>{@code currentBreakingProgress} — the accumulated 0..1 break
 *       progress. Bumping this toward 1.0f forces the completion branch
 *       on the next {@code updateBlockBreakingProgress} call, letting
 *       AutoMine finish a block faster than the legit per-tick delta would
 *       allow (the "instant mine" speedhack flavor).</li>
 * </ul>
 *
 * <p>We only expose the fields — AutoMine decides when to reset them based
 * on its mode settings. The interaction manager's {@code attackBlock} /
 * {@code updateBlockBreakingProgress} methods still drive the actual
 * sequenced-packet sends, so client-prediction sequences stay correct.
 */
@Mixin(ClientPlayerInteractionManager.class)
public interface ClientPlayerInteractionManagerAccessor {

    /** The per-tick mining cooldown (vanilla sets 5 after each break). */
    @Accessor("blockBreakingCooldown")
    int lucid$getBlockBreakingCooldown();

    @Accessor("blockBreakingCooldown")
    void lucid$setBlockBreakingCooldown(int value);

    /** The accumulated 0..1 break progress for the current block. */
    @Accessor("currentBreakingProgress")
    float lucid$getCurrentBreakingProgress();

    @Accessor("currentBreakingProgress")
    void lucid$setCurrentBreakingProgress(float value);
}