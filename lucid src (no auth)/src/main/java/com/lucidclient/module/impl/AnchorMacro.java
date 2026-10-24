package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.util.MouseSimulation;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AnchorMacro — the Argon implementation behind Lucid's module + settings UI.
 *
 * <p>Hold right-click while aiming at a Respawn Anchor and the macro runs the
 * full charge → detonate sequence for you:
 *
 * <ol>
 *   <li><b>Charge</b>: if the aimed anchor is uncharged, swaps to glowstone
 *       (after Switch Delay, gated by Switch Chance) and right-clicks the
 *       anchor (after Glowstone Delay, gated by Glowstone Chance).</li>
 *   <li><b>Detonate</b>: once the anchor reads as charged, swaps to the
 *       configured Explode Slot (Switch Delay / Switch Chance) and right-clicks
 *       to blow it up (Explode Delay / Explode Chance).</li>
 * </ol>
 *
 * <p>Argon options carried over: While Use (ignore eat/shield blocking),
 * Stop on Kill (pause when a dead body is within 6 blocks), Click Simulation
 * (dispatch a synthetic right-click through the vanilla input pipeline so the
 * CPS HUD sees it), Only Own (only detonate anchors YOU placed — tracked on
 * every vanilla item use), Only Charge (charge anchors but never detonate),
 * and the six delay/chance randomizers plus the Explode Slot.
 *
 * <p>Every interaction is a plain vanilla block-use with the LIVE crosshair
 * hit — the player aims, the macro sequences.
 */
public class AnchorMacro extends Module {

    private final BooleanSetting whileUse = addSetting(
        new BooleanSetting("While Use", "Also trigger while eating / holding up a shield.", true));
    private final BooleanSetting stopOnKill = addSetting(
        new BooleanSetting("Stop on Kill", "Doesn't anchor if a body is nearby.", false));
    private final BooleanSetting clickSimulation = addSetting(
        new BooleanSetting("Click Simulation", "Makes the CPS hud think you're legit.", false));
    private final BooleanSetting humanize = addSetting(
        new BooleanSetting("Humanize", "Randomized pacing: each phase fires at or before its configured delay — usually on time, sometimes a beat quicker, never longer than the set delay.", true));
    private final IntSetting switchDelay = addSetting(
        new IntSetting("Switch Delay", "Ticks to wait before swapping the hotbar slot.", 0, 20, 0, 1));
    private final IntSetting switchChance = addSetting(
        new IntSetting("Switch Chance", "Chance that a hotbar swap actually happens.", 0, 100, 100, 1));
    private final IntSetting placeChance = addSetting(
        new IntSetting("Place Chance", "Chance that the charge/detonate sequence starts at all (randomization).", 0, 100, 100, 1));
    private final IntSetting glowstoneDelay = addSetting(
        new IntSetting("Glowstone Delay", "Ticks between swapping to glowstone and clicking the anchor.", 0, 20, 0, 1));
    private final IntSetting glowstoneChance = addSetting(
        new IntSetting("Glowstone Chance", "Chance that the glowstone click happens (randomization).", 0, 100, 100, 1));
    private final IntSetting explodeDelay = addSetting(
        new IntSetting("Explode Delay", "Ticks between swapping to the explode slot and detonating.", 0, 20, 0, 1));
    private final IntSetting explodeChance = addSetting(
        new IntSetting("Explode Chance", "Chance that the detonate click happens (randomization).", 0, 100, 100, 1));
    private final IntSetting explodeSlot = addSetting(
        new IntSetting("Explode Slot", "Hotbar slot (1-9) to hold while detonating.", 1, 9, 1, 1));
    private final BooleanSetting onlyOwn = addSetting(
        new BooleanSetting("Only Own", "Only detonate anchors that you placed yourself.", false));
    private final BooleanSetting onlyCharge = addSetting(
        new BooleanSetting("Only Charge", "Charge anchors but never detonate them.", false));

    private int switchClock = 0;
    private int glowstoneClock = 0;
    private int explodeClock = 0;

    /** Humanized per-wait delay targets (rolled at the start of each wait). */
    private int switchTarget = 0;
    private int glowstoneTarget = 0;
    private int explodeTarget = 0;

    /** Anchors this client placed (for Only Own) — tracked on every vanilla item use. */
    private final Set<BlockPos> ownedAnchors = new HashSet<>();

    public AnchorMacro() {
        super("Anchor Macro",
            "Automatically blows up respawn anchors for you",
            Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
        switchTarget = 0;
        glowstoneTarget = 0;
        explodeTarget = 0;
    }

    @Override
    protected void onDisable() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
        switchTarget = 0;
        glowstoneTarget = 0;
        explodeTarget = 0;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        // While Use = off: don't fight eating / shielding (either hand).
        if (!whileUse.isEnabled()
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
                && (mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD)
                    || mc.player.getMainHandStack().getItem() instanceof ShieldItem
                    || mc.player.getOffHandStack().getItem() instanceof ShieldItem
                    || mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD))) {
            return;
        }

        if (stopOnKill.isEnabled() && isDeadBodyNearby(mc)) return;

        int randomInt = randomInt(1, 100);

        if (GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
            return;
        }
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        if (!isBlock(mc, hit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) return;

        if (onlyOwn.isEnabled() && !ownedAnchors.contains(hit.getBlockPos())) return;

        // Vanilla's use key is suppressed for this sequence via the
        // shouldOwnClick() cancel in MinecraftClientMixin — see that method.

        if (isAnchorNotCharged(mc, hit.getBlockPos())) {
            randomInt = randomInt(1, 100);

            if (randomInt <= placeChance.get()) {
                if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                    if (switchClock == 0) switchTarget = humanizeDelay(switchDelay.get());
                    if (switchClock != switchTarget) {
                        switchClock++;
                        return;
                    }

                    randomInt = randomInt(1, 100);

                    if (randomInt <= switchChance.get()) {
                        switchClock = 0;
                        selectItemFromHotbar(mc, Items.GLOWSTONE);
                    }
                }

                if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                    if (glowstoneClock == 0) glowstoneTarget = humanizeDelay(glowstoneDelay.get());
                    if (glowstoneClock != glowstoneTarget) {
                        glowstoneClock++;
                        return;
                    }

                    randomInt = randomInt(1, 100);

                    if (randomInt <= glowstoneChance.get()) {
                        glowstoneClock = 0;

                        if (clickSimulation.isEnabled()) {
                            MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        }

                        placeBlock(mc, hit, true);
                    }
                }
            }
        }

        if (isAnchorCharged(mc, hit.getBlockPos())) {
            int slot = explodeSlot.get() - 1;

            if (mc.player.getInventory().getSelectedSlot() != slot) {
                if (switchClock == 0) switchTarget = humanizeDelay(switchDelay.get());
                if (switchClock != switchTarget) {
                    switchClock++;
                    return;
                }

                if (randomInt <= switchChance.get()) {
                    switchClock = 0;
                    mc.player.getInventory().setSelectedSlot(slot);
                }
            }

            if (mc.player.getInventory().getSelectedSlot() == slot) {
                if (explodeClock == 0) explodeTarget = humanizeDelay(explodeDelay.get());
                if (explodeClock != explodeTarget) {
                    explodeClock++;
                    return;
                }

                randomInt = randomInt(1, 100);

                if (randomInt <= explodeChance.get()) {
                    explodeClock = 0;

                    if (!onlyCharge.isEnabled()) {
                        if (clickSimulation.isEnabled()) {
                            MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        }

                        placeBlock(mc, hit, true);

                        ownedAnchors.remove(hit.getBlockPos());
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Vanilla-use ownership (called from MinecraftClientMixin.doItemUse)
    // ════════════════════════════════════════════════════════════════

    /**
     * True when the macro owns right-click use and vanilla's doItemUse must
     * be cancelled: enabled + holding right mouse + aiming at a respawn
     * anchor. Without this, vanilla's own use (which the OS re-asserts every
     * frame, and which handleInputEvents processes BEFORE our tick hook)
     * charges/detonates the anchor on its own 4-tick cooldown the moment the
     * hotbar swap lands — completely bypassing the macro's delay pacing.
     * Mirrors the same gates as {@link #onTick} so the suppression exactly
     * tracks when the macro is actually sequencing.
     */
    public static boolean shouldOwnClick() {
        AnchorMacro instance = instance();
        if (instance == null || !instance.isEnabled()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return false;

        boolean rightHeld = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
            GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (!rightHeld) return false;

        // While Use = off: don't own clicks while eating / shielding (the
        // macro itself stands down in that case too).
        if (!instance.whileUse.isEnabled()
                && (mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD)
                    || mc.player.getMainHandStack().getItem() instanceof ShieldItem
                    || mc.player.getOffHandStack().getItem() instanceof ShieldItem
                    || mc.player.getOffHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD))) {
            return false;
        }

        if (instance.stopOnKill.isEnabled() && instance.isDeadBodyNearby(mc)) return false;

        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return false;
        if (!instance.isBlock(mc, hit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) return false;
        if (instance.onlyOwn.isEnabled() && !instance.ownedAnchors.contains(hit.getBlockPos())) return false;

        return true;
    }

    // ════════════════════════════════════════════════════════════════
    //  Item-use hook (Only Own tracking) — called from MinecraftClientMixin
    //  on every vanilla doItemUse, Argon's ItemUseListener equivalent.
    // ════════════════════════════════════════════════════════════════

    /** Anchor placement records ownership; a use on a charged anchor clears it. */
    public static void onVanillaItemUse() {
        AnchorMacro instance = instance();
        if (instance == null || !instance.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hitResult)
                || hitResult.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            return;
        }

        if (mc.player.getMainHandStack().getItem() == Items.RESPAWN_ANCHOR) {
            Direction dir = hitResult.getSide();
            BlockPos pos = hitResult.getBlockPos();

            if (!mc.world.getBlockState(pos).isReplaceable()) {
                switch (dir) {
                    case UP -> instance.ownedAnchors.add(pos.add(0, 1, 0));
                    case DOWN -> instance.ownedAnchors.add(pos.add(0, -1, 0));
                    case EAST -> instance.ownedAnchors.add(pos.add(1, 0, 0));
                    case WEST -> instance.ownedAnchors.add(pos.add(-1, 0, 0));
                    case NORTH -> instance.ownedAnchors.add(pos.add(0, 0, -1));
                    case SOUTH -> instance.ownedAnchors.add(pos.add(0, 0, 1));
                }
            } else {
                instance.ownedAnchors.add(pos);
            }
        }

        BlockPos bp = hitResult.getBlockPos();
        if (instance.isAnchorCharged(mc, bp)) {
            instance.ownedAnchors.remove(bp);
        }
    }

    private static AnchorMacro instance() {
        Module m = LucidClient.getInstance() != null
            && LucidClient.getInstance().getModuleManager() != null
            ? LucidClient.getInstance().getModuleManager().getModule(AnchorMacro.class)
            : null;
        return (AnchorMacro) m;
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers (Argon's BlockUtils / InventoryUtils / WorldUtils inlined)
    // ════════════════════════════════════════════════════════════════

    private boolean isBlock(MinecraftClient mc, BlockPos pos, Block block) {
        return mc.world.getBlockState(pos).getBlock() == block;
    }

    private boolean isAnchorCharged(MinecraftClient mc, BlockPos pos) {
        if (isBlock(mc, pos, Blocks.RESPAWN_ANCHOR)) {
            return mc.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES) != 0;
        }
        return false;
    }

    private boolean isAnchorNotCharged(MinecraftClient mc, BlockPos pos) {
        if (isBlock(mc, pos, Blocks.RESPAWN_ANCHOR)) {
            return mc.world.getBlockState(pos).get(RespawnAnchorBlock.CHARGES) == 0;
        }
        return false;
    }

    /** True when a dead body (dropped totem screen-shake source) is within 6 blocks. */
    private boolean isDeadBodyNearby(MinecraftClient mc) {
        return mc.world.getPlayers().parallelStream()
                .filter(e -> e != mc.player)
                .filter(e -> e.squaredDistanceTo(mc.player) <= 36)
                .anyMatch(LivingEntity::isDead);
    }

    /** Vanilla right-click on the live crosshair hit; swing on success. */
    private void placeBlock(MinecraftClient mc, BlockHitResult blockHit, boolean swingHand) {
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
        if (result.isAccepted() && swingHand) mc.player.swingHand(Hand.MAIN_HAND);
    }

    /** Select the first hotbar slot holding the given item (vanilla slot swap). */
    private static void selectItemFromHotbar(MinecraftClient mc, Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    private static int randomInt(int start, int bound) {
        return ThreadLocalRandom.current().nextInt(start, bound);
    }

    /**
     * Humanize roll (the past-client behavior, capped): the configured
     * delay is the CEILING. Each wait usually fires right on the configured
     * delay, sometimes a touch quicker, very rarely near-instant — but
     * never longer than configured. Returns the configured delay untouched
     * when Humanize is off or the delay is 0.
     */
    private int humanizeDelay(int configured) {
        if (!humanize.isEnabled() || configured <= 0) return configured;
        int r = ThreadLocalRandom.current().nextInt(100);
        if (r < 55) return configured;                        // on time
        if (r < 80) return Math.max(0, configured * 3 / 4);   // a touch quick
        if (r < 94) return Math.max(0, configured / 2);       // clearly quick
        return 0;                                             // flash reflexes
    }
}
