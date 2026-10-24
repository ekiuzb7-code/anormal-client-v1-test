package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * AutoEat — automatically eats when your hunger (or health) drops below a
 * threshold.
 *
 * ── The vanilla process ──────────────────────────────────────────
 * Eating is manual: notice low hunger → scroll to food → hold right-click for
 * ~1.6s (32 ticks) → switch back. AutoEat automates that whole sequence with
 * a tick-synchronised state machine so it wastes no frames.
 *
 * ── The state machine ────────────────────────────────────────────
 *
 *   IDLE → SWAP_TO_FOOD → EATING → SWITCH_BACK → IDLE
 *
 *   - IDLE:        monitor hunger/health. When the trigger fires, scan the
 *                  hotbar (and inventory if "Search Inventory" is on) for the
 *                  best food per Food Priority, remember the original slot,
 *                  and pre-select the food slot → SWAP_TO_FOOD.
 *   - SWAP_TO_FOOD: wait one tick (server processes the slot-change before
 *                   the use packet — same race-fix as AnchorMacro), then call
 *                   interactionManager.interactItem(player, MAIN_HAND) to
 *                   start the eat → EATING.
 *   - EATING:      keep using the item until the player stops using it (vanilla
 *                  fires finishUsing internally and the stack shrinks). Each
 *                   tick we re-issue interactItem if the player isn't currently
 *                   using (covers the case where the first interact didn't
 *                   stick, e.g. cooldown). When the player is no longer using
 *                   AND the food stack is gone / different, the eat is done.
 *   - SWITCH_BACK: restore the original hotbar slot → IDLE.
 *
 * ── Triggers ─────────────────────────────────────────────────────
 *   - Hunger trigger: fires when {@code foodLevel < Min Hunger Bar}.
 *   - Health trigger (PVP): overrides hunger when {@code health < Health
 *     Threshold} — force-feed to trigger natural regen even at high hunger.
 *
 * ── Food priority ───────────────────────────────────────────────
 *   - Highest Saturation: pick the food with the best saturation modifier
 *     (best per-food-slot value — gold apples, steak, etc. first).
 *   - Lowest Quality: pick the worst food first (so you save the good stuff).
 *   - Golden Apples Only: only eat enchanted / regular golden apples (and
 *     chorus fruit / god apples) — used for clutch PvP healing.
 *
 * ── Cancel on Action ─────────────────────────────────────────────
 * If "Cancel on Action" is on and the player left-clicks (attacks) or places a
 * block mid-eat, the sequence aborts and restores the original slot — so the
 * mod never locks you into the eating animation while you're fighting.
 *
 * Settings:
 *   Min Hunger Bar, Health Threshold, Food Priority, Search Inventory,
 *   Cancel on Action
 */
public class AutoEat extends Module {

    private final FloatSetting minHunger = addSetting(
        new FloatSetting("Min Hunger Bar",
            "Start eating when your food level drops below this (0–20, 20 = always eating).",
            0f, 20f, 16f, 0));
    private final FloatSetting healthThreshold = addSetting(
        new FloatSetting("Health Threshold",
            "Force-eat when your health drops below this, even at high hunger (PVP regen). "
            + "Set to 0 to disable.",
            0f, 20f, 0f, 0));
    private final EnumSetting foodPriority = addSetting(
        new EnumSetting("Food Priority",
            "Highest Saturation = best food first. Lowest Quality = worst food first. "
            + "Golden Apples Only = only eat golden / enchanted golden apples.",
            List.of("Highest Saturation", "Lowest Quality", "Golden Apples Only"),
            "Highest Saturation"));
    private final BooleanSetting searchInventory = addSetting(
        new BooleanSetting("Search Inventory",
            "Also scan your main inventory for food (not just the hotbar) and swap it into the hotbar.",
            false));
    private final BooleanSetting cancelOnAction = addSetting(
        new BooleanSetting("Cancel on Action",
            "Abort eating if you left-click (attack) or place a block, so the mod never locks you in the eat animation.",
            true));

    // ── State machine ────────────────────────────────────────
    private enum State { IDLE, SWAP_TO_FOOD, EATING, SWITCH_BACK }

    private State state = State.IDLE;
    private int ticksInState = 0;

    // Inventory state (captured at trigger).
    private int originalSlot = -1;   // the hotbar slot the player was on
    private int foodSlot = -1;        // the hotbar slot the food ended up in
    private int originalHotbarSwapSlot = -1; // if we MOVED food from inventory to hotbar,
                                             // the hotbar slot we used (to revert)

    // ── Eat progress detection ────────────────────────────────
    // We watch the active food stack's count shrink to know when the eat is done.
    private int lastFoodCount = -1;
    private int stuckTicks = 0; // safety: if eating stalls too long, abort

    private static final int MAX_EAT_TICKS = 50;  // 2.5s — longer than the 32-tick eat
    private static final int SWAP_DELAY = 2;     // ticks for the server to see the slot swap

    // Left-click edge detection for Cancel on Action.
    private boolean wasAttackDown = false;

    public AutoEat() {
        super("AutoEat", "Automatically eats food when your hunger or health drops below a threshold.",
            Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState();
        wasAttackDown = false;
    }

    @Override
    protected void onDisable() {
        // If we were mid-eat, snap back to the original slot so the player isn't
        // left holding food.
        restoreOriginalSlot();
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        originalSlot = -1;
        foodSlot = -1;
        originalHotbarSwapSlot = -1;
        lastFoodCount = -1;
        stuckTicks = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — drive the state machine (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;

        // ── Cancel on Action: abort if the player attacks/places ──
        if (cancelOnAction.isEnabled() && state != State.IDLE) {
            if (didPlayerAttack(client)) {
                restoreOriginalSlot();
                resetState();
                return;
            }
        }

        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;

        ticksInState++;

        switch (state) {
            case IDLE: {
                if (shouldEat(player)) {
                    beginSequence(client, player);
                }
                break;
            }
            case SWAP_TO_FOOD: {
                // Wait SWAP_DELAY ticks for the server to process the slot swap
                // before sending the use packet (race-fix, same as AnchorMacro).
                if (ticksInState < SWAP_DELAY) return;
                // Guard: make sure the food is actually in our hand on the client
                // before issuing the use. If the slot swap hasn't synced yet
                // (server lag), wait a few more ticks rather than firing a use
                // on the wrong item.
                ItemStack mainHand = player.getMainHandStack();
                if (!isQualifyingFood(mainHand, foodPriority.get())) {
                    if (ticksInState > SWAP_DELAY + 10) {
                        restoreOriginalSlot();
                        resetState();
                    }
                    return;
                }
                // Start the eat.
                //
                // interactItem sends the use-item packet to the server (so the
                // server registers the eat), but it does NOT, by itself, start
                // the client-side eat — vanilla's doItemUse triggers the
                // client eat via a separate setCurrentHand call after seeing
                // the ActionResult. Calling setCurrentHand directly here is
                // what reliably flips isUsingItem=true and sets
                // itemUseTimeLeft = getMaxUseTime (~32 ticks). Without it,
                // interactItem alone sometimes leaves isUsingItem=false (the
                // "eat didn't start" abort) because nothing started the
                // client eat.
                //
                // Order matters: send the server packet first (interactItem),
                // THEN start the client eat (setCurrentHand) so the server
                // sees the use begin before we locally start ticking the timer.
                im.interactItem(player, Hand.MAIN_HAND);
                player.setCurrentHand(Hand.MAIN_HAND);

                lastFoodCount = mainHand.getCount();
                stuckTicks = 0;
                state = State.EATING;
                ticksInState = 0;
                break;
            }
            case EATING: {
                // Safety: never get stuck.
                if (ticksInState > MAX_EAT_TICKS) {
                    restoreOriginalSlot();
                    resetState();
                    return;
                }

                // Detection of completion: the eat is done when the food stack
                // shrank or changed (vanilla's consumeItem fires finishUsing
                // internally and the stack count drops). We do NOT re-issue
                // interactItem here — issuing it again would reset the 32-tick
                // eat timer and the eat would never complete (the original
                // infinite-loop bug).
                ItemStack mainHand = player.getMainHandStack();
                int count = mainHand.getCount();
                boolean stackChanged = count < lastFoodCount
                                    || mainHand.isEmpty()
                                    || !isQualifyingFood(mainHand, foodPriority.get());

                if (stackChanged) {
                    // Eat finished (or food ran out). Switch back.
                    state = State.SWITCH_BACK;
                    ticksInState = 0;
                    break;
                }

                // isUsingItem should be true the whole time. If it flips false
                // before the stack changed, something cancelled the eat (the
                // player stopped via vanilla, or a clearActiveItem fired). Give
                // it a short grace window (network/tick jitter) before aborting
                // — but do NOT loop re-issuing; that was the original bug.
                if (!player.isUsingItem()) {
                    stuckTicks++;
                    if (stuckTicks > 6) {
                        restoreOriginalSlot();
                        resetState();
                        return;
                    }
                } else {
                    stuckTicks = 0;
                }
                break;
            }
            case SWITCH_BACK: {
                restoreOriginalSlot();
                resetState();
                break;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Trigger logic
    // ════════════════════════════════════════════════════════════════
    private boolean shouldEat(ClientPlayerEntity player) {
        HungerManager hm = player.getHungerManager();
        int foodLevel = hm.getFoodLevel();
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();

        // Don't trigger if we're already full and the health trigger isn't firing.
        if (!hm.isNotFull() && foodLevel >= 20) {
            // canAlwaysEat foods (golden apples, chorus) bypass the full check
            // — but only fire on the health trigger, not the hunger trigger.
            float ht = healthThreshold.get();
            if (ht <= 0f || health >= ht) return false;
        }

        // Hunger trigger.
        if (foodLevel < minHunger.get()) return true;

        // Health trigger (PVP regen) — overrides hunger.
        float ht = healthThreshold.get();
        if (ht > 0f && health < ht) return true;

        return false;
    }

    // ════════════════════════════════════════════════════════════════
    //  Inventory scan + ghost swap
    // ════════════════════════════════════════════════════════════════
    private void beginSequence(MinecraftClient client, ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        originalSlot = inv.getSelectedSlot();

        // Find the best food slot per Food Priority.
        int targetHotbarSlot = findBestFoodHotbarSlot(inv);
        if (targetHotbarSlot < 0 && searchInventory.isEnabled()) {
            // No food in the hotbar — pull one from the main inventory.
            targetHotbarSlot = pullFoodFromInventory(client, player, inv);
        }
        if (targetHotbarSlot < 0) {
            // No food anywhere. Don't spam the chat line — only post once
            // per trigger attempt is fine; the next IDLE tick will silently retry.
            return;
        }

        foodSlot = targetHotbarSlot;
        // Pre-select the food slot now; the SWAP_TO_FOOD state waits SWAP_DELAY
        // ticks before sending the use packet (server race-fix).
        selectSlot(player, foodSlot);
        state = State.SWAP_TO_FOOD;
        ticksInState = 0;
    }

    /**
     * Find the best hotbar slot (0–8) holding edible food per the Food Priority.
     * Returns -1 if no qualifying food is in the hotbar.
     */
    private int findBestFoodHotbarSlot(PlayerInventory inv) {
        String priority = foodPriority.get();
        int bestSlot = -1;
        double bestScore = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!isQualifyingFood(stack, priority)) continue;
            double score = foodScore(stack, priority);
            if (bestSlot < 0 || isBetter(score, bestScore, priority)) {
                bestSlot = i;
                bestScore = score;
            }
        }
        return bestSlot;
    }

    /**
     * Search the main inventory (slots 9–35) for the best qualifying food and,
     * if found, swap it into the hotbar via {@link PlayerInventory#swapSlotWithHotbar}.
     * Returns the hotbar slot it ended up in, or -1 if no food was found.
     */
    private int pullFoodFromInventory(MinecraftClient client, ClientPlayerEntity player,
                                       PlayerInventory inv) {
        String priority = foodPriority.get();
        int bestInvSlot = -1;
        double bestScore = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (!isQualifyingFood(stack, priority)) continue;
            double score = foodScore(stack, priority);
            if (bestInvSlot < 0 || isBetter(score, bestScore, priority)) {
                bestInvSlot = i;
                bestScore = score;
            }
        }
        if (bestInvSlot < 0) return -1;

        // swapSlotWithHotbar picks a hotbar slot itself and swaps. We remember
        // the resulting selected slot so restoreOriginalSlot can revert it.
        inv.swapSlotWithHotbar(bestInvSlot);
        int hotbarTarget = inv.getSelectedSlot();
        originalHotbarSwapSlot = hotbarTarget;
        return hotbarTarget;
    }

    // ── Food classification + scoring ────────────────────────────
    private boolean isQualifyingFood(ItemStack stack, String priority) {
        if (stack.isEmpty()) return false;
        if (!isFood(stack)) return false;
        if ("Golden Apples Only".equals(priority)) {
            return stack.getItem() == Items.GOLDEN_APPLE
                || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE;
        }
        return true;
    }

    private boolean isFood(ItemStack stack) {
        return stack.contains(net.minecraft.component.DataComponentTypes.FOOD);
    }

    /**
     * Higher score = preferred. For "Highest Saturation" we score by the
     * FoodComponent's nutrition + saturation. For "Lowest Quality" we INVERT
     * the score so the worst food has the highest score (isBest picks it).
     */
    private double foodScore(ItemStack stack, String priority) {
        FoodComponent food = stack.get(net.minecraft.component.DataComponentTypes.FOOD);
        if (food == null) return -1;
        double base = food.nutrition() + food.saturation() * 2.0;
        if ("Lowest Quality".equals(priority)) return -base; // worst = highest score
        return base;
    }

    /** True if {@code score} beats the current best for the given priority. */
    private boolean isBetter(double score, double best, String priority) {
        // For "Lowest Quality" the score is negated, so higher is still better.
        return score > best;
    }

    // ════════════════════════════════════════════════════════════════
    //  Slot helpers
    // ════════════════════════════════════════════════════════════════
    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
    }

    private void restoreOriginalSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (originalSlot >= 0 && originalSlot <= 8) {
            selectSlot(mc.player, originalSlot);
        }
        originalSlot = -1;
    }

    // ════════════════════════════════════════════════════════════════
    //  Cancel-on-action detection
    // ════════════════════════════════════════════════════════════════
    private boolean didPlayerAttack(MinecraftClient client) {
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT)
                        == GLFW.GLFW_PRESS;
        boolean clicked = down && !wasAttackDown;
        wasAttackDown = down;
        return clicked;
    }

    // ── Public state for UI hooks ───────────────────────────────
    public boolean isEating() { return state != State.IDLE; }
}