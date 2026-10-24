package com.anormal.client.module.impl.misc;

import com.anormal.client.lucid.LucidClient;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Fake Item — spawn a clientside-only "ghost" item into your real inventory via
 * the chat commands {@code ;spawn <item>} and {@code ;clear}, that you can then
 * drag around your inventory, wear as armor / elytra, etc. — exactly like a real
 * Minecraft item.
 *
 * <h3>The ghost-item model</h3>
 * Unlike the previous (render-overlay) build, the fake item is now a genuine
 * {@link ItemStack} written into a real slot of {@link PlayerInventory}
 * (purely local — no packet informs the server). You can drag it into any other
 * player-inventory slot, equip it into an armor slot, etc., and it behaves
 * according to its vanilla properties:
 * <ul>
 *   <li>A fake chestplate / helmet / etc. <b>wears</b> on your body and renders
 *       the armor model (because it's now a real stack in your real armor slot).</li>
 *   <li>A fake elytra <b>wears</b> in the chest slot, vanilla's
 *       {@code ElytraFeatureRenderer} draws the wing model, and the rebuilt
 *       pose system ({@link java.lang.Object}
 *       for the body lean + {@link java.lang.Object}
 *       for the wings, driven by the smoothed glide progress here) spreads
 *       them on a double-tap-space glide even without the server-validated
 *       gliding state.
 *       <b>No actual flight</b> — the server still knows you have no elytra,
 *       so glide physics/fall damage are unchanged. Cosmetic wing spread only.</li>
 *   <li>A normal item renders in your hand, hotbar, and inventory screens
 *       naturally — vanilla reads it from the real inventory slot.</li>
 *   <li>Dragging the fake onto a chest slot fits the "snap-back" agreement:
 *       the server rejects the click and resyncs the chest slot, so the fake
 *       falls back to its prior player-inventory slot (see {@link
 *       java.lang.Object}).</li>
 *   <li><b>Item frames:</b> right-click an item frame while holding the fake
 *       in your main hand to place the fake INTO that one frame (clientside
 *       visual). Right-click the same frame again to remove it. The outgoing
 *       {@code PlayerInteractEntityC2SPacket} is suppressed by
 *       {@link java.lang.Object} so
 *       the server never sees the placement — which is why this works on
 *       DonutSMP (the server can't reject a click it never gets, unlike the
 *       vanilla flow which the DonutSMP server silently ignores because it
 *       thinks your hand is empty). {@code ;clear} / module-disable wipes all
 *       fake-frame markers too.</li>
 * </ul>
 *
 * <h3>Why the server never finds out</h3>
 * Three network-side mixins keep the server blissfully ignorant:
 * <ul>
 *   <li>{@link java.lang.Object} suppresses outgoing
 *       {@code ClickSlotC2SPacket}s that involve the ghost slot (or a cursor
 *       holding the fake) — so the server never receives an impossible click
 *       it would have to reject.</li>
 *   <li>{@link java.lang.Object} cancels
 *       incoming {@code SetPlayerInventoryS2CPacket} /
 *       {@code ScreenHandlerSlotUpdateS2CPacket} packets that target the ghost
 *       slot, so a periodic server-side resync never silently clears the
 *       fake from your view.</li>
 *   <li>{@link java.lang.Object} {@code @Inject}s at
 *       the TAIL of {@code ScreenHandler.onSlotClick} to track the ghost
 *       slot's current position and re-place the fake on cursor snap-back.</li>
 * </ul>
 *
 * <h3>The "Forge World Items" toggle</h3>
 * Default OFF. With single-slot semantics the only way to make the fake appear
 * on dropped item entities or in item frames is to substitute <i>every</i>
 * one of them while the module is active — visually noisy. Flip the toggle ON
 * to also overlay the fake on dropped/framed items render-only (cosmetic).
 *
 * <h3>Settings</h3>
 *   Toggle Key, Forge World Items.
 *
 * <h3>Server scope</h3>
 * Unlike the Fake* siblings, Fake Item is <b>not</b> DonutSMP-gated — a held
 * item SWAT is generic and harmless on any server.
 */
public class FakeItem extends Module {

    // ── Static state read by the mixins (mirrors FakePay.isActive()).
    //    A static flag lets every @Inject/@Redirect handler no-op cheaply when
    //    the module is off.
    private static volatile boolean active = false;

    /** The fake item currently spawned (null = none). */
    private static volatile ItemStack fakeStack = null;

    /**
     * The {@link PlayerInventory} slot index the ghost currently occupies, or
     * {@code -1} when not placed (e.g. while on the cursor being dragged).
     * Indices: 0-8 hotbar, 9-35 main, 36 boots / 37 legs / 38 chest / 39 head,
     * 40 off-hand.
     */
    private static volatile int ghostSlot = -1;

    /**
     * The live count of the primary fake currently held in the player's
     * inventory (whatever the inventory slot currently shows). Re-synced from
     * the real slot every tick ({@link #reassertGhost()}); used by the
     * Ctrl+Q whole-stack drop, where vanilla's mc prediction EMPTIES the
     * slot before our {@code ClientConnection} hook fires, so the pre-drop
     * count can no longer be read from the slot itself.
     */
    private static volatile int liveFakeCount = -1;

    /**
     * When the fake has been dragged INTO an open container (a chest / shulker
     * / hopper GUI), this records the slot index <i>within that container's
     * {@link net.minecraft.screen.ScreenHandler}</i> where the fake currently
     * sits, plus that handler's {@code syncId}. {@code -1} when the fake is
     * not in a container (it's in the player's own inventory or on the
     * cursor). Used by the resync intercepts in {@link
     * java.lang.Object} to keep the
     * server's full-window refresh ({@code InventoryS2CPacket}) from wiping
     * the fake out of the container slot, and by {@link
     * java.lang.Object} to suppress chest-click
     * packets that would let the server reject the drag.
     */
    private static volatile int ghostContainerSlot = -1;
    private static volatile int ghostContainerSyncId = -1;
    /** How many fake copies are currently parked in the open container's slots. */
    private static volatile int containerFakeCount = 0;
    /** The slot indices (within the open container's handler) where fake copies
     *  are parked. This is MEMORY, not a re-scan: the server has no record of
     *  the fakes, so its resyncs temporarily EMPTY these slots — if we only
     *  ever re-asserted what a live scan still found, a wipe that applied
     *  before our next tick would erase the slots from our view forever and
     *  every clone in the chest would die. Keeping the slot list lets us heal
     *  from memory regardless of the wipe race. */
    private static final java.util.Set<Integer> containerFakeSlots = new java.util.HashSet<>();

    /**
     * Persisted fake positions inside STORAGE containers, surviving GUI
     * close/reopen. Key = storage anchor:
     * <ul>
     *   <li>{@code "ender"} — the player's ender chest: the SAME 27 slots from
     *       any ender-chest block, so fakes placed in one ender chest are seen
     *       again from any other (the contents are shared).</li>
     *   <li>{@code "pos@x,y,z"} — a specific placed block container (chest /
     *       barrel / hopper / placed shulker box) captured at right-click
     *       time, so re-opening the SAME block re-injects into the same slots.</li>
     * </ul>
     * Value = map of container slot number → fake copies parked there. This is
     * the "the fake item stays in the container" state: when the window closes
     * we do NOT snap the copies back to the inventory; when it opens again the
     * fakes are re-injected into the SAME slots. Container GUIs are backed by
     * fresh client-side {@code ArrayInventory} instances that are recreated
     * every open, so the anchor can never be an inventory object — the block
     * identity is the only stable handle.
     */
    private static final java.util.LinkedHashMap<String, java.util.TreeMap<Integer, Integer>>
        fakeContainerStorages = new java.util.LinkedHashMap<>();

    /**
     * Storage anchor of the container window currently open, captured by
     * {@link #captureOpenedContainer(String)} from
     * {@code ClientPlayerInteractionManagerMixin.interactBlock}. {@code null}
     * when no storage is open. Read at close-detection ("persist in the
     * container" vs "snap back to the inventory") and by the per-tick heal to
     * keep the persisted counts in lockstep with the visible stacks.
     */
    private static volatile String openContainerKey = null;

    /**
     * The slot the ghost was in before being picked up onto the cursor, used
     * to snap-back if it lands somewhere the server rejects (a chest).
     */
    private static volatile int lastGhostSlot = -1;

    /**
     * Extra slots (beyond the single tracked {@link #ghostSlot}) filled when
     * spawning with a stack count, e.g. {@code ;spawn diamond 64 5}. Each
     * holds an identical copy of {@link #fakeStack}; cleared by {@link
     * #removeGhostFromInventory()}.
     */
    private static final java.util.Set<Integer> extraGhostSlots =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** True while the held cursor is currently carrying the fake stack. */
    private static volatile boolean cursorIsGhost = false;

    /**
     * Item-frame entity IDs the user has explicitly "placed" the fake item into
     * via right-click (toggle — placing again un-marks). Each entry here is
     * rendered by {@link java.lang.Object}
     * as holding the fake, purely clientside; the server never sees the
     * placement interaction (the outgoing {@code PlayerInteractEntityC2SPacket}
     * is suppressed by {@link java.lang.Object}).
     *
     * <p>Stored by entity ID (int). Stale ID reuse after chunk unload is
     * guarded in practice by the renderer comparing against the actual frame
     * being rendered; {@code ;clear} / module-disable wipes the set so stale
     * entries never accumulate beyond the user resetting.
     */
    private static final java.util.Set<Integer> fakeFrames =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Extra fake stacks granted by the fake storage GUI (skeleton spawner
     * drops: bones / arrows / xp). Each entry maps an inventory slot to the
     * stack that sits there. Unlike {@link #extraGhostSlots} (identical copies
     * of {@link #fakeStack}) these are DIFFERENT items — the module's primary
     * tracker stays the fake spawner. All the suppression/resync-protection
     * that covers the primary fake is extended to these slots via
     * {@link #getGhostSlots()} / {@link #isGhostSlot(int)} / {@link
     * #findFakeSlotsInInv()}.
     */
    private static final java.util.Map<Integer, ItemStack> extraFakeStacks =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** The exact cursor stack the last time {@link #cursorIsGhost} flipped true. */
    private static volatile ItemStack cursorFakeStack = null;

    /**
     * Clientside fake blocks placed on the ground (right-click while holding
     * the fake) — spawner fakes render as a real spawner cage with the
     * rotating mob, other block items render their own block. The server has
     * no record of any of them; the per-tick driver re-asserts the block state
     * + spawner block entity so chunk reloads / server resyncs can't erase
     * them, and the hold-to-break driver (left-click) mines them back into the
     * inventory.
     */
    private static final java.util.Map<net.minecraft.util.math.BlockPos, PlacedFakeBlock> fakeBlocks =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Block currently mid-break (left-click held) — used for the crack overlay. */
    private static volatile net.minecraft.util.math.BlockPos mineActivePos = null;

    /** Wall-clock ms of the last "need Silk Touch" denial shown, to throttle the action-bar text. */
    private static volatile long lastSpawnerDenyMs = 0L;

    /**
     * Item-entity IDs spawned by placing a NON-block fake on the ground (they
     * sit still for click-to-mine-back, unlike Q-toss drops which are walkover
     * pickup). Subset of {@link #fakeDropped}.
     */
    private static final java.util.Set<Integer> placedDrops =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable Fake Item. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final BooleanSetting forgeWorldItems = addSetting(
        new BooleanSetting("Forge World Items",
            "When ON, also render the fake item on DROPPED item entities and in ITEM FRAMES across the world "
                + "(visual only — the server's real items are untouched). OFF by default because the only way "
                + "to make the fake appear on dropped/framed items is to substitute ALL of them while the "
                + "module is active, which is visually noisy.",
            false));
    private final BooleanSetting placeOnGround = addSetting(
        new BooleanSetting("Place On Ground",
            "Right-click places the held fake on the ground as a clientside block (spawners render "
                + "as a spawner cage with the rotating mob; other block items render their own block). "
                + "Hold left-click to mine it back. Non-block fakes drop as a resting item you can "
                + "click to pick back up.",
            true));

    /** Read the "Place On Ground" toggle without a module instance. */
    public static boolean canPlaceOnGround() {
        try {
            FakeItem fake = com.anormal.client.module.ModuleManager.getModule(FakeItem.class);
            return fake == null || fake.placeOnGround.isEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Toggle key edge detection (matches Sprint's onTickAlways pattern). */
    private boolean wasKeyDown = false;

    public FakeItem() {
        super("Fake Item",
            "Spawn a clientside ghost item via ;spawn <item> / ;clear — drag it in your inventory, "
                + "wear it, cosmetic elytra. The server is never told.",
            Category.MISC);
    }

    @Override
    public void onEnable() {
        wasKeyDown = false;
        active = true;
        dbg("onEnable", "");
    }

    @Override
    public void onDisable() {
        // Clear the ghost at its current inventory slot before flipping the
        // flag so the visuals revert to the real held item.
        dbg("onDisable", "");
        abortActiveUse();  // end any held-use pose so a disabled fake can't strand
        removeGhostFromInventory();
        clearFakeFrames();
        clearFakeBlocks();
        clearFakeDroppedEntities();
        placedDrops.clear();
        active = false;
        wasDedicatedLogged = false;  // reset so reconnect re-log fires again
    }

    /**
     * End any clientside held-use pose so a disable / ;clear mid-use doesn't
     * strand the local player stuck blocking/zooming/eating. Discards a cast
     * fake fishing bobber too. No-op when there's nothing to abort.
     *
     * <p>Order matters: we reset {@code currentSimKind}/{@code currentSimHand}
     * BEFORE calling {@code stopUsingItem} — that way the {@code
     * LivingEntityUseMixin} HEAD-inject on {@code stopUsingItem} →
     * {@link #onUseStopPre} sees {@code currentSimKind==NONE} and falls
     * through the dispatch's default branch (no projectile spawn, no finish
     * sound); the pose is cleared by vanilla's tail and that's it. Resetting
     * BEFORE the call is what makes this "abort" rather than "release".
     */
    private static void abortActiveUse() {
        try {
            currentSimKind = SimKind.NONE;
            currentSimHand = null;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.player != null && c.player.isUsingItem()) {
                try { c.player.stopUsingItem(); } catch (Throwable ignored) {}
            }
            discardFakeBobber();
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem abortActiveUse threw", t);
        } finally {
            resetUseSim();
        }
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching FakePay / FakeStats / Sprint. Also re-asserts the ghost at its
     * slot each tick so any incidental thieves (server resync that slipped
     * past the cancellation hooks, a re-equip, etc.) are corrected within a
     * tick — the item "snaps back" into view if something clears it.
     */
    @Override
    public void onTickAlways() {
        // Re-assert the ghost at its slot every tick while enabled. The click
        // suppression + incoming-sync cancellation should keep it stable; this
        // is a low-cost defensive catch.
        reassertGhost();

        if (mc.player == null) return;

        // Walkover re-absorb: pick any tracked fake-dropped entities back up
        // when the player walks over them (DonutSMP "spawn visual item +
        // re-absorb on walkover" scope). This MUST run before the toggle-key
        // gate below — when no toggle key is bound, getKey() returns -1 and
        // the old early-return skipped the pickup entirely (drops could never
        // be re-absorbed; zero drop-sweep logs across three test sessions).
        tickWalkoverPickup();

        // Re-assert placed fake blocks (server resync / chunk reload erases
        // them) + drive the hold-to-break mine progress + crack overlay.
        tickFakeBlockWorld(mc);

        if (mc.getWindow() == null) return;

        // ── Cosmetic glide: double-tap SPACE while airborne (vanilla's
        // flight-toggle gesture) engages the glide pose until landing. The
        // server never grants real FALL_FLYING for a fake elytra, so vanilla's
        // own toggle can't fire — we detect the same input pattern ourselves
        // and let PlayerEntityRendererMixin / ElytraEntityModelMixin drive
        // the pose from isCosmeticGliding().
        tickNow++;
        boolean jumpDown = false;
        try { jumpDown = mc.options.jumpKey.isPressed(); } catch (Throwable ignored) {}
        boolean glidable = false;
        try {
            glidable = !mc.player.isOnGround()
                && !mc.player.isTouchingWater()
                && !mc.player.isClimbing();
        } catch (Throwable ignored) {}
        if (jumpDown && !jumpWasDown) {
            // Rising edge of the jump key — vanilla's double-tap window is
            // 7 ticks. ENGAGE only from a fully-idle pose: not already
            // gliding AND the previous animation has completely folded
            // (progress back at 0). This locks out mid-animation spam — a
            // second double-tap while the elytra is still "out" (opening,
            // gliding, or folding) does nothing until the wings are fully
            // home and the player is airborne again.
            if (glidable && (tickNow - lastJumpTapTick) <= 7 && isFakeElytraEquipped()
                && !cosmeticGlide && glideProgress <= 0.02f) {
                cosmeticGlide = true;
            }
            lastJumpTapTick = tickNow;
        }
jumpWasDown = jumpDown;
        if (!glidable) cosmeticGlide = false;  // landed / entered water / ladder
        // Advance the vanilla-faithful wing controller (ElytraFlightController
        // math) once per tick, mirroring the real 20Hz update loop.
        stepWingController();
        // (Pose smoothing is frame-based �?" see stepGlidePose �?" driven by the
        // render hooks so the animation runs at full frame rate, not 20Hz
        // tick steps.)

        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = mc.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        if (isDown && !wasKeyDown) {
            setEnabled(!isEnabled());
        }
        wasKeyDown = isDown;
    }

    // ════════════════════════════════════════════════════════════
    //  Chat command surface — invoked from LucidClient.onInitializeClient
    //  via ClientSendMessageEvents.ALLOW_CHAT.register(FakeItem::onSendChat).
    // ════════════════════════════════════════════════════════════

    /** Fabric {@code ClientSendMessageEvents.ALLOW_CHAT} handler. */
    public static boolean onSendChat(String message) {
        if (!active) return true;
        if (message == null) return true;

        String trimmed = message.trim();
        if (trimmed.startsWith(";spawn ")) {
            String rest = trimmed.substring(";spawn ".length()).trim();
            if (rest.isEmpty()) {
                return false;
            }
            spawn(rest);
            return false;  // consume — the ;spawn line never reaches the server
        }
        if (trimmed.equals(";clear") || trimmed.startsWith(";clear ")) {
            clear();
            return false;
        }
        if (trimmed.equals(";enchant") || trimmed.startsWith(";enchant ")) {
            String rest = trimmed.substring(";enchant".length()).trim();
            if (rest.isEmpty()) {
                return false;
            }
            applyEnchant(rest);
            return false;
        }
        return true;
    }

    /**
     * Resolve an item id (with an optional count suffix) into a fake stack and
     * place it at the player's currently-selected hotbar slot (then it's a
     * normal item the user can drag anywhere in the inventory / wear / etc.).
     * Command syntax: {@code ;spawn <item> [count] [stacks]} — the count defaults
     * to 1 and is clamped to the item's natural max stack size (e.g. dirt=64,
     * netherite_ingot=64, chestplate=1); stacks defaults to 1 and fills
     * additional EMPTY inventory slots with identical copies.
     */
    public static void spawn(String arg) {
        try {
            if (!active) {
                return;
            }
            if (arg == null || arg.isEmpty()) {
                return;
            }
            String[] parts = arg.trim().split("\\s+");
            String itemId = parts[0];
            int requestedCount = 1;
            if (parts.length >= 2) {
                try {
                    requestedCount = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return;
                }
            }
            int requestedStacks = 1;
            if (parts.length >= 3) {
                try {
                    requestedStacks = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    return;
                }
            }
            if (requestedStacks < 1) requestedStacks = 1;
            Identifier id = parseId(itemId);
            if (id == null) {
                return;
            }
            Item item = Registries.ITEM.get(id);
            // "skeleton_spawner" / "zombie_spawner" / etc. aren't real item ids —
            // map them onto the spawner item, then stamp the entity below.
            if ((item == null || item == Items.AIR) && itemId.endsWith("_spawner")) {
                item = Items.SPAWNER;
            }
            if (item == null || item == Items.AIR) {
                return;
            }
            int count = Math.max(1, Math.min(requestedCount, item.getMaxCount()));

            // First, abort any held-use of the prior fake so its pose doesn't
            // carry over to the new fake (which may have a different UseAction).
            abortActiveUse();
            // ...and drop the previous ghost at its slot (if any).
            removeGhostFromInventory();

            ItemStack fake = new ItemStack((ItemConvertible) item, count);
            // Spawner items: stamp the block-entity data so the item shows the
            // requested mob in its cage (and places as that spawner).
            if (item == Items.SPAWNER) {
                applySpawnerEntity(fake, itemId);
            }
            
            if (mc.player == null) {
                // Keep a PRISTINE copy in the static — vanilla (equip-swap,
                // splitStack, decrement, etc.) mutates ItemStack instances
                // in-place; if our static aliased the inventory slot's
                // instance, a count→0 decrement would silently turn our
                // "fake" into ItemStack.EMPTY (getItem() returns Items.AIR
                // for empty stacks), and every mixin that reads it would
                // see "no fake" and no-op. The .copy() detaches our state
                // from the live inventory slot entirely.
                fakeStack = fake.copy();
                liveFakeCount = count;
                ghostSlot = -1;
                return;
            }
            PlayerInventory inv = mc.player.getInventory();
            int slot = inv.getSelectedSlot();
            // Place the fake into the selected hotbar slot. PlayerInventory
            // index == hotbar slot (0..8). Purely local mutation. The slot
            // instance and the static fakeStack MUST NOT alias — see the
            // .copy() note above. We hand the inventory a fresh copy and
            // keep an independent pristine reference in fakeStack so vanilla
            // in-place mutations (decrement / splitStack from equip-swap or
            // drag-out-from-slot) cannot empty our tracker.
            inv.setStack(slot, fake.copy());
            fakeStack = fake.copy();
            liveFakeCount = count;
            ghostSlot = slot;
            lastGhostSlot = slot;
            cursorIsGhost = false;
            // Integrated server (singleplayer / LAN): make the fake a REAL
            // item there too so every vanilla behaviour (drop, frames, real
            // elytra flight, etc.) works natively — the server agrees you
            // have the fake. On a dedicated server this is a no-op.
            syncToIntegratedServer(slot, fake);
            // Extra stacks (;spawn <item> <count> <stacks>): fill following
            // EMPTY inventory slots with identical copies. Only empty slots —
            // we never clobber real items — so fewer may go in than asked.
            int placedStacks = 1;
            while (placedStacks < requestedStacks) {
                int extraSlot = firstEmptyAvail(inv);
                if (extraSlot < 0) break;
                inv.setStack(extraSlot, fake.copy());
                extraGhostSlots.add(extraSlot);
                if (isIntegratedServer()) {
                    syncToIntegratedServer(extraSlot, fake);
                }
                placedStacks++;
            }
            dbg("spawn", "spawned=" + id + " slot=" + slot + " stacks=" + placedStacks
                + " integratedSync=" + isIntegratedServer()
                + " fakeStackIsCopy=" + (fakeStack != fake));
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem spawn threw for '{}'", arg, t);
        }
    }

    /**
     * Add an enchantment to every live copy of the primary fake (all tracked
     * slots) AND to the {@link #fakeStack} template. Both must change together:
     * {@link #sameFakeIdentity} compares the full component set, so a live
     * copy enchanted while the template stayed clean would stop matching the
     * template, get scanned as "fake gone", and be replaced by the reassert
     * heal with a clean copy. Command: {@code ;enchant <enchant> [level]} —
     * e.g. {@code ;enchant unbreaking 3} (plain number; Roman {@code III} is
     * also accepted but never required). Level defaults to 1 and clamps to
     * 1..255.
     */
    public static void applyEnchant(String arg) {
        try {
            if (!active) {
                return;
            }
            if (arg == null || arg.trim().isEmpty()) {
                return;
            }
            String[] parts = arg.trim().split("\\s+");
            String rawId = parts[0];
            int level = 1;
            if (parts.length >= 2) {
                Integer lv = parseEnchantLevel(parts[1]);
                if (lv == null) {
                    return;
                }
                level = lv;
            }

            
            if (mc.player == null || mc.world == null) {
                return;
            }
            net.minecraft.registry.Registry<Enchantment> reg =
                mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            RegistryKey<Enchantment> key = parseEnchantmentKey(rawId);
            if (key == null) {
                return;
            }
            RegistryEntry.Reference<Enchantment> entry;
            try {
                entry = reg.getOrThrow(key);
            } catch (Throwable t) {
                return;
            }

            // Capture the primary-fake slots BEFORE touching the template, so
            // sameFakeIdentity still matches during the scan.
            PlayerInventory inv = mc.player.getInventory();
            java.util.List<Integer> primarySlots = new java.util.ArrayList<>();
            if (fakeStack != null && !fakeStack.isEmpty()) {
                for (int s : findFakeSlotsInInv()) {
                    if (s >= 0 && s < inv.size() && isFakeIdentity(inv.getStack(s))) {
                        primarySlots.add(s);
                    }
                }
            }
            if (fakeStack == null && primarySlots.isEmpty()) {
                return;
            }

            if (fakeStack != null && !fakeStack.isEmpty()) {
                fakeStack.addEnchantment(entry, level);  // template — keeps identity aligned
            }
            for (int s : primarySlots) {
                ItemStack cur = inv.getStack(s).copy();
                cur.addEnchantment(entry, level);
                inv.setStack(s, cur);
                if (isIntegratedServer()) {
                    syncToIntegratedServer(s, cur);
                }
            }

            String enchName;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem applyEnchant threw for '{}'", arg, t);
        }
    }

    /**
     * Resolve a user-typed enchantment id into a registry key, defaulting to
     * the minecraft namespace: {@code unbreaking} and {@code minecraft:unbreaking}
     * both work. Returns null when the string isn't a valid identifier.
     */
    private static RegistryKey<Enchantment> parseEnchantmentKey(String raw) {
        String namespace = "minecraft", path = raw.trim();
        int colon = path.indexOf(':');
        if (colon >= 0) {
            namespace = path.substring(0, colon);
            path = path.substring(colon + 1);
        }
        if (path.isEmpty()) return null;
        Identifier ident;
        try {
            ident = Identifier.of(namespace, path);
        } catch (Throwable t) {
            return null;
        }
        return RegistryKey.of(RegistryKeys.ENCHANTMENT, ident);
    }

    /**
     * Parse an enchantment level typed by the player: a plain Arabic number
     * ({@code 3}) or a Roman numeral ({@code III}) both work — Roman is only a
     * fallback, never a requirement. Returns null for anything unparseable;
     * clamps the result to 1..255.
     */
    private static Integer parseEnchantLevel(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            int v = Integer.parseInt(t);
            return Math.max(1, Math.min(255, v));
        } catch (NumberFormatException ignored) {
            // fall through to the Roman attempt
        }
        int roman = romanToArabic(t);
        if (roman <= 0) return null;
        return Math.max(1, Math.min(255, roman));
    }

    /** Standard Roman numeral -> int; -1 when the string isn't valid Roman. */
    private static int romanToArabic(String s) {
        String upper = s.toUpperCase(java.util.Locale.ROOT);
        if (upper.isEmpty()) return -1;
        int total = 0, prev = 0;
        for (int i = upper.length() - 1; i >= 0; i--) {
            int val;
            switch (upper.charAt(i)) {
                case 'I': val = 1; break;
                case 'V': val = 5; break;
                case 'X': val = 10; break;
                case 'L': val = 50; break;
                case 'C': val = 100; break;
                case 'D': val = 500; break;
                case 'M': val = 1000; break;
                default: return -1;
            }
            if (val < prev) total -= val; else { total += val; prev = val; }
        }
        return total;
    }

    /**
     * Stamp a spawner item to replicate a real DonutSMP spawner exactly.
     * "skeleton_spawner" → skeleton, "zombie_spawner" → zombie, plain
     * "spawner" → skeleton. Tooltip layout (exported from a real item):
     * <pre>
     * Spawner                     pink (item_name)
     * Skeleton                     yellow
     *  ~$ 64K                    leading space white, "~" faded gray, "$" green, " 64K" white
     * (blank)
     * </pre>
     * Strictly NO italics anywhere: title uses item_name (not custom_name —
     * vanilla hardcodes ITALIC for the swap label and tooltip title whenever
     * a stack CONTAINS custom_name, verified in ItemStack.getFormattedName,
     * ItemStack.toHoverableText and InGameHud.renderHeldItemTooltip). On top
     * of that, every created text has withItalic(false) baked into its Style
     * so nothing can ever inherit slanting. There is also NO
     * {@code minecraft:block_entity_data} component (its presence is what
     * makes vanilla append the gray "Interact with Spawn Egg:" / blue
     * "Sets Mob Type" footer).
     */
    private static void applySpawnerEntity(ItemStack stack, String itemId) {
        try {
            String mobId = itemId.endsWith("_spawner")
                ? itemId.substring(0, itemId.length() - "_spawner".length())
                : "skeleton";
            if (mobId.isEmpty()) mobId = "skeleton";

            stack.set(DataComponentTypes.ITEM_NAME,
                styled("Spawner", Formatting.LIGHT_PURPLE));

            MutableText value = styled(" ", Formatting.WHITE)
                .append(styled("~", Formatting.GRAY))
                .append(styled("$", Formatting.GREEN))
                .append(styled(" 64K", Formatting.WHITE));
            stack.set(DataComponentTypes.LORE,
                new LoreComponent(java.util.List.of(
                    styled(toTitleCase(mobId), Formatting.YELLOW),
                    value,
                    styled("", null))));

            // Hidden economy-plugin style metadata (invisible, ID-safe).
            NbtCompound custom = new NbtCompound();
            custom.putString("lucid_spawner", "true");
            custom.putString("spawner_mob", mobId);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(custom));

            dbg("applySpawnerEntity", "mob=" + mobId);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem applySpawnerEntity threw for '{}'", itemId, t);
        }
    }

    /**
     * Literal text with an explicit color and italic HARD-DISABLED so the
     * line is upright in every render context, even if a parent style ever
     * tries to force italics. A null color leaves the default (white).
     */
    private static MutableText styled(String s, Formatting color) {
        return Text.literal(s)
            .styled(style -> style.withColor(color).withItalic(false));
    }

    /** "skeleton" → "Skeleton", "iron_golem" → "Iron Golem". */
    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] parts = s.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    /** Remove the fake from its current slot and clear module state. */
    public static void clear() {
        if (fakeStack == null && ghostSlot < 0 && extraGhostSlots.isEmpty()
            && ghostContainerSlot < 0 && fakeFrames.isEmpty() && fakeDropped.isEmpty()
            && fakeContainerStorages.isEmpty()) {
            return;
        }
        abortActiveUse();  // end any held-use pose so a ;clear mid-use doesn't strand
        removeGhostFromInventory();
        clearFakeFrames();
        // Also discard every fake-dropped item entity currently tracked
        // (a pending walkover pickup should not survive a ;clear).
        clearFakeDroppedEntities();
        placedDrops.clear();
        // Remove every placed fake block (right-click placements) + any
        // mid-break crack overlay on them.
        clearFakeBlocks();
        cursorIsGhost = false;
    }

    /** Remove any fake stack sitting in the player inventory, wiping module state. */
    private static void removeGhostFromInventory() {
        try {
            
            if (mc != null && mc.player != null) {
                PlayerInventory inv = mc.player.getInventory();
                // Clear EVERY slot currently holding a fake — the primary
                // ghost, every extra stack, and any copies the player has
                // moved around the inventory. The live scan self-heals: a slot
                // that used to hold an extra but now holds a real item the
                // user swapped in is skipped by the stackEquals guard, so we
                // never clobber honest loot.
                for (int s : new java.util.ArrayList<>(findFakeSlotsInInv())) {
                    if (s < 0 || s >= inv.size()) continue;
                    inv.setStack(s, ItemStack.EMPTY);
                    if (isIntegratedServer()) {
                        syncToIntegratedServer(s, ItemStack.EMPTY);
                    }
                }
                // Also clear any fakes still parked in the open container (from
                    // memory — wipes may have momentarily emptied the slots,
                    // but the bookkeeping still knows every position).
                    if (ghostContainerSyncId >= 0 && mc.player.currentScreenHandler != null) {
                        net.minecraft.screen.ScreenHandler h = mc.player.currentScreenHandler;
                        if (h.syncId == ghostContainerSyncId) {
                            for (int s : new java.util.ArrayList<>(containerFakeSlots)) {
                                if (s >= 0 && s < h.slots.size()) {
                                    h.slots.get(s).setStackNoCallbacks(ItemStack.EMPTY);
                                }
                            }
                        }
                    }
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem removeGhost threw", t);
        }
        extraGhostSlots.clear();
        extraFakeStacks.clear();
        cursorFakeStack = null;
        clearFakeContainerStorages();
        ghostContainerSlot = -1;
        ghostContainerSyncId = -1;
        containerFakeCount = 0;
        containerFakeSlots.clear();
        openContainerKey = null;
        fakeStack = null;
        ghostSlot = -1;
        lastGhostSlot = -1;
        liveFakeCount = -1;
    }

    /**
     * Re-sync {@link #liveFakeCount} from the inventory slot that actually
     * holds the primary fake. Called every tick from {@link #reassertGhost()};
     * also invoked by the drop path to catch a partial-drop decrement before
     * the next tick.
     */
    private static void syncLiveFakeCount(PlayerInventory inv) {
        try {
            if (fakeStack == null || inv == null) return;
            if (ghostSlot >= 0 && ghostSlot < inv.size()) {
                ItemStack cur = inv.getStack(ghostSlot);
                if (isFakeIdentity(cur) && cur.getCount() > 0) {
                    liveFakeCount = cur.getCount();
                    return;
                }
            }
            // Primary tracker is stale (fake moved) — scan for any slot holding
            // the primary fake and adopt its count.
            for (int i = 0; i < inv.size(); i++) {
                ItemStack cur = inv.getStack(i);
                if (isFakeIdentity(cur) && cur.getCount() > 0) {
                    liveFakeCount = cur.getCount();
                    return;
                }
            }
            // No primary fake in the inventory right now (on the ground / placed /
            // on the cursor) — keep the last known count so Ctrl+Q still has a
            // sane whole-stack size to spawn.
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem syncLiveFakeCount threw", t);
        }
    }

    /**
     * Defensive re-assert: if the module is active and a fake is spawned but
     * its tracked slot no longer holds the fake (e.g. a server resync slipped
     * through), re-place the fake there. Cheap — runs every tick while enabled.
     * Also reasserts the fake's position inside an OPEN container (chest /
     * shulker / hopper GUI) when the fake has been dragged into one.
     */
    private static void reassertGhost() {
        if (!active) return;
        if (fakeStack == null) return;
        try {
            
            if (mc.player == null) return;
            // Keep the authoritative fake count in sync with the live inventory
            // slot every tick, so Ctrl+Q's whole-stack drop can spawn the true
            // pre-drop count even though vanilla predicts the slot clear BEFORE
            // our drop hook runs. Falls back to syncToIntegratedServer-style
            // identity scan: whichever slot actually holds the primary fake.
            syncLiveFakeCount(mc.player.getInventory());
            // When the fake(s) were last parked in a chest slot and that container is
            // no longer open (the user closed the GUI), the chest's slots are gone
            // from the visible world — every parked copy would vanish. Snap them
            // ALL back to the player's inventory so the user keeps every stack
            // after closing the chest.
            if (!cursorIsGhost && ghostContainerSyncId >= 0
                && mc.player.currentScreenHandler != null
                && mc.player.currentScreenHandler.syncId != ghostContainerSyncId) {
                try {
                    if (openContainerKey != null) {
                        // PERSIST: the fakes stay parked in the storage
                        // container, client-side, at their saved slots. No
                        // snap-back — the GUI is gone but fakeContainerStorages
                        // keeps the positions so the next open (of the same
                        // block / the ender chest) re-injects them into the
                        // SAME spot (the user's "item stays in the container").
                        dbg("container-close", "persisted key=" + openContainerKey
                            + " slots=" + new java.util.TreeMap<>(fakeContainerStorages.getOrDefault(openContainerKey, new java.util.TreeMap<>())));
                        clearGhostContainerLocation();
                    } else {
                        int snapCount = Math.max(1, containerFakeCount);
                        clearGhostContainerLocation();
                        snapBackGhosts(snapCount);
                    }
                } catch (Throwable t) {
                    com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem chest-close snapback threw", t);
                }
                return;
            }
            if (cursorIsGhost) return;  // fake is on cursor; nothing to re-assert
            // 1) Player-inventory ghost slot(s). Reconcile the tracker with
            //    wherever the fake(s) actually are — multi-stack copies can be
            //    moved around independently, so instead of "if the tracked slot
            //    lost the fake, teleport a copy back" (which DUPLICATED the
            //    fake whenever the user manually moved it), we retarget the
            //    tracker to the moved copy and rebuild the extra-slot set from
            //    a live scan. This self-heals WIPED slots (server resync) AND
            //    honours real player moves. The scan covers the primary fake
            //    AND every multi-fake storage-GUI grant (bones etc.) via
            //    {@link #isAnyFakeStack}; the rebuild is identity-aware so a
            //    moved bone keeps its own stack instead of being swapped for
            //    the primary fake.
            if (ghostSlot >= 0 || !extraGhostSlots.isEmpty() || !extraFakeStacks.isEmpty()) {
                PlayerInventory inv = mc.player.getInventory();
                java.util.List<Integer> fakes = findFakeSlotsInInv();
                if (!fakes.isEmpty()) {
                    // Retarget if the tracked primary no longer holds the fake.
                    boolean trackedHoldsFake = false;
                    for (int s : fakes) {
                        if (s == ghostSlot) { trackedHoldsFake = true; break; }
                    }
                    // Rebuild the extra-slot sets from reality so moved extras
                    // are re-protected by the suppression mixins (isGhostSlot).
                    // Identity-aware: slots holding a copy of the PRIMARY fake
                    // become extraGhostSlots; slots holding a different item
                    // (a storage-GUI grant like bones) keep their own stack in
                    // extraFakeStacks, resolving through the previous entries so
                    // a moved grant doesn't get mistaken for a real item.
                    java.util.Map<Integer, ItemStack> rebuiltExtras = new java.util.HashMap<>();
                    java.util.Set<Integer> rebuiltExtraPrimary = new java.util.HashSet<>();
                    for (int s : fakes) {
                        if (s == ghostSlot) continue;
                        ItemStack cur = inv.getStack(s);
                        if (sameFakeIdentity(cur, fakeStack)) {
                            rebuiltExtraPrimary.add(s);
                        } else {
                            boolean resolved = false;
                            for (var e : extraFakeStacks.entrySet()) {
                                if (sameFakeIdentity(e.getValue(), cur)) {
                                    rebuiltExtras.put(s, e.getValue().copy());
                                    resolved = true;
                                    break;
                                }
                            }
                            if (!resolved) rebuiltExtras.put(s, cur.copy());
                        }
                    }
                    extraGhostSlots.clear();
                    extraGhostSlots.addAll(rebuiltExtraPrimary);
                    extraFakeStacks.clear();
                    extraFakeStacks.putAll(rebuiltExtras);
                    if (!trackedHoldsFake && !fakes.isEmpty()) {
                        // Migrate the primary tracker to wherever the first
                        // fake now lives (covers vanilla right-click equip-swap
                        // into armor slots, and manual moves of the primary).
                        ghostSlot = fakes.get(0);
                        lastGhostSlot = ghostSlot;
                    }
                } else {
                    // Fake is genuinely gone from the player inventory (a drop
                    // path emptied it or an un-cancelled resync wiped it) —
                    // restore EVERY still-tracked copy into its empty slot
                    // (clones included, each with its own count), then point
                    // the primary tracker at one of the restored slots.
                    // Storage-GUI grants (extraFakeStacks) resolve through
                    // their slot->stack mapping.
                    int primaryTarget = -1;
                    for (int s : new java.util.ArrayList<>(getGhostSlots())) {
                        if (s < 0 || s >= inv.size()) continue;
                        ItemStack cur = inv.getStack(s);
                        if (cur != null && !cur.isEmpty()) {
                            if (primaryTarget < 0 && sameFakeIdentity(cur, fakeStack)) primaryTarget = s;
                            continue;
                        }
                        inv.setStack(s, stackForInventorySlot(s));
                        if (primaryTarget < 0 && sameFakeIdentity(stackForInventorySlot(s), fakeStack)) {
                            primaryTarget = s;
                        }
                    }
                    if (primaryTarget < 0) {
                        int target = ghostSlot >= 0 ? ghostSlot
                            : (lastGhostSlot >= 0 ? lastGhostSlot : inv.getSelectedSlot());
                        if (target >= 0 && target < inv.size()) {
                            inv.setStack(target, fakeStack.copy());
                            primaryTarget = target;
                        }
                    }
                    if (primaryTarget >= 0) {
                        ghostSlot = primaryTarget;
                        lastGhostSlot = primaryTarget;
                        dbg("reassert-restore", "restored missing fakes across slots; primary=" + primaryTarget);
                    }
                }
            }
            // 2) Open container (chest / shulker / hopper GUI): heal EVERY remembered
            //    fake slot from MEMORY (containerFakeSlots), every tick. This
            //    intentionally does NOT depend on the click-tracked
            //    ghostContainerSyncId and does NOT require the fake to still be
            //    visible in the slot: when the first fake stacks are dropped
            //    while others still sit in the player inventory, the click
            //    handler only tracked the inventory slot; and when a server
            //    resync slips past the cancellers and EMPTIES the chest slots,
            //    a scan would find nothing and declare the fakes gone. Memory
            //    ignores both races — the slots we remember get rewritten no
            //    matter what the slot currently shows.
            if (mc.player.currentScreenHandler != null) {
                try {
                    net.minecraft.screen.ScreenHandler handler = mc.player.currentScreenHandler;
                    if (handler.syncId >= 0
                        && !(handler instanceof net.minecraft.screen.PlayerScreenHandler)) {
                        String key = openContainerKey;
                        // A storage container with persisted fakes just
                        // RE-OPENED: the window's slots were just rebuilt (and
                        // its fresh truth applied), so restore the remembered
                        // slot numbers into the transient memory set — the heal
                        // below re-plants the fake into the SAME spots. Slot
                        // numbers are stable across re-opens for these handlers
                        // (container slot N == handler slot N).
                        if (key != null) {
                            java.util.TreeMap<Integer, Integer> stored = fakeContainerStorages.get(key);
                            if (stored != null && !stored.isEmpty()) {
                                for (int ci : new java.util.ArrayList<>(stored.keySet())) {
                                    if (ci < 0 || ci >= handler.slots.size()) continue;
                                    net.minecraft.screen.slot.Slot sl = handler.slots.get(ci);
                                    if (sl == null || sl.inventory instanceof PlayerInventory) continue;
                                    containerFakeSlots.add(ci);
                                }
                                dbg("container-reopen", "key=" + key
                                    + " re-injected slots=" + new java.util.TreeMap<>(stored));
                            }
                        }
                        for (int s : new java.util.ArrayList<>(containerFakeSlots)) {
                            if (s < 0 || s >= handler.slots.size()) {
                                containerFakeSlots.remove(s);
                                continue;
                            }
                            net.minecraft.screen.slot.Slot slot = handler.slots.get(s);
                            ItemStack cur = slot.getStack();
                            if (sameFakeIdentity(cur, fakeStack)) continue; // already ours
                            if (cur == null || cur.isEmpty()) {
                                // Wiped or never re-asserted — heal from memory,
                                // honouring the persisted per-slot count.
                                int want = fakeStack.getCount();
                                if (key != null) {
                                    java.util.TreeMap<Integer, Integer> stored = fakeContainerStorages.get(key);
                                    if (stored != null) {
                                        Integer saved = stored.get(s);
                                        if (saved != null && saved > 0) want = saved;
                                    }
                                }
                                slot.setStackNoCallbacks(fakeStack.copyWithCount(want));
                            } else {
                                // A different real item occupies the slot now —
                                // the user moved something in (or the server has
                                // real content here); the fake is no longer ours.
                                containerFakeSlots.remove(s);
                                if (key != null) {
                                    java.util.TreeMap<Integer, Integer> stored = fakeContainerStorages.get(key);
                                    if (stored != null) {
                                        stored.remove(s);
                                        if (stored.isEmpty()) fakeContainerStorages.remove(key);
                                    }
                                }
                            }
                        }
                        // Safety net: pick up any fake copy present in the slots
                        // that isn't in memory yet (e.g. a quick-move that slipped
                        // past the click mixin).
                        containerFakeSlots.addAll(findContainerFakeSlots());
                        containerFakeCount = containerFakeSlots.size();
                        if (!containerFakeSlots.isEmpty()) {
                            ghostContainerSyncId = handler.syncId;
                            if (ghostContainerSlot < 0 || !containerFakeSlots.contains(ghostContainerSlot)) {
                                ghostContainerSlot = containerFakeSlots.iterator().next();
                            }
                        } else if (ghostContainerSyncId == handler.syncId) {
                            ghostContainerSlot = -1;
                            ghostContainerSyncId = -1;
                        }
                        // Keep the persisted counts in lockstep with what the
                        // slots actually show (a click changed a count this
                        // tick → reflect it in the stored backup).
                        if (key != null) {
                            java.util.TreeMap<Integer, Integer> stored =
                                fakeContainerStorages.computeIfAbsent(key, k -> new java.util.TreeMap<>());
                            for (int s : new java.util.ArrayList<>(containerFakeSlots)) {
                                if (s < 0 || s >= handler.slots.size()) continue;
                                ItemStack st = handler.slots.get(s).getStack();
                                if (st != null && !st.isEmpty() && sameFakeIdentity(st, fakeStack)) {
                                    stored.put(s, st.getCount());
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem reassert container threw", t);
                }
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem reassert threw", t);
        }
    }

    /**
     * Live scan of the player's inventory for every slot that currently holds
     * a copy of the fake (primary ghost, extra stacks, and copies the user has
     * moved around). This is the self-healing view of "where do the fakes
     * actually live" — it is NOT the bookkeeping (ghostSlot/extraGhostSlots),
     * so it stays accurate even after the player rearranges their inventory.
     * Returns an empty list when the fake is on the cursor, in a container,
     * or simply absent.
     */
    private static java.util.List<Integer> findFakeSlotsInInv() {
        java.util.List<Integer> found = new java.util.ArrayList<>();
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return found;
            if (fakeStack == null && extraFakeStacks.isEmpty()) return found;
            PlayerInventory inv = c.player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s != null && !s.isEmpty() && isAnyFakeStack(s)) found.add(i);
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem findFakeSlotsInInv threw", t);
        }
        return found;
    }

    /**
     * Find the fake inside one of the four armor slots (36 boots / 37 legs /
     * 38 chest / 39 head). Kept for diagnostics; the live scan
     * ({@link #findFakeSlotsInInv()}) already covers armor slots when the
     * tracker reconciles.
     */
    private static int findFakeInArmorSlots(PlayerInventory inv) {
        try {
            for (int i = 36; i <= 39 && i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s != null && !s.isEmpty() && stackEquals(s, fakeStack)) return i;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * Re-apply the fake to its tracked player-inventory slot AND container slot
     * immediately (not next-tick). Called by the network-handler mixin AFTER it
     * cancels an {@code InventoryS2CPacket} (full-window refresh) for the open
     * container, so the server's resync can't strand the fake's slot empty in
     * the visible GUI — we restore the fake synchronously before the player
     * sees a flicker.
     */
    public static void reapplyAfterResync() {
        try {
            if (!active || fakeStack == null) return;
            
            if (mc.player == null) return;
            // Player-inventory slots: RE-APPLY ONLY WIPED SLOTS, for EVERY
            // tracked fake (primary + clones), not just the primary ghostSlot.
            // A container full-window resync that slips through EMPTIES the
            // whole player-grid section of the window (the packet carries the
            // server's truth for every grid slot, and the server has no record
            // of any fake). Restoring only ghostSlot is exactly what made the
            // second stack vanish on chest-open ("the other stacks disappear
            // and leave 1"). We walk getGhostSlots() — the tracked primary,
            // every extra ghost slot, and every granted multi-fake stack — and
            // rewrite only the slots the resync emptied, each with its OWN
            // stored count via stackForInventorySlot.
            PlayerInventory inv = mc.player.getInventory();
            java.util.Set<Integer> tracked = getGhostSlots();
            if (tracked.isEmpty()) tracked.addAll(findFakeSlotsInInv());
            for (int s : tracked) {
                if (s < 0 || s >= inv.size()) continue;
                ItemStack now = inv.getStack(s);
                if (now == null || now.isEmpty()) {
                    inv.setStack(s, stackForInventorySlot(s));
                }
            }
            // Container ghost slots: re-assert EVERY REMEMBERED fake slot in the
            // open chest / shulker / hopper GUI from memory, so a cancelled-
            // or-applied full-window resync restores the whole multi-stack group
            // in place — including slots the resync emptied (memory heals wipes).
            // The write is EMPTY-ONLY: a non-empty slot holds either a user-edited
            // fake (keep its count) or REAL content (never overwrite honest items).
            if (mc.player.currentScreenHandler != null) {
                try {
                    net.minecraft.screen.ScreenHandler handler = mc.player.currentScreenHandler;
                    String key = openContainerKey;
                    for (int s : new java.util.ArrayList<>(containerFakeSlots)) {
                        if (s >= 0 && s < handler.slots.size()) {
                            net.minecraft.screen.slot.Slot slot = handler.slots.get(s);
                            if (slot == null || slot.inventory instanceof PlayerInventory) continue;
                            ItemStack cur = slot.getStack();
                            if (cur == null || cur.isEmpty()) {
                                int want = fakeStack.getCount();
                                if (key != null) {
                                    java.util.TreeMap<Integer, Integer> stored = fakeContainerStorages.get(key);
                                    if (stored != null) {
                                        Integer saved = stored.get(s);
                                        if (saved != null && saved > 0) want = saved;
                                    }
                                }
                                handler.slots.get(s).setStackNoCallbacks(fakeStack.copyWithCount(want));
                                dbg("reapply-container-write", "slot=" + s + " -> count=" + want);
                            } else {
                                containerFakeSlots.remove(s);
                                dbg("reapply-container-unlink", "slot=" + s
                                    + " now holds real content; unlinked");
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // Restore a fake-carrying cursor the server just tried to clear.
            if (cursorIsGhost) {
                try {
                    ItemStack cursorRestore = cursorFakeStack != null && !cursorFakeStack.isEmpty()
                        ? cursorFakeStack.copy() : fakeStack.copy();
                    mc.player.currentScreenHandler.setCursorStack(cursorRestore);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem reapplyAfterResync threw", t);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Mixin read surface — static, no module instance required
    // ════════════════════════════════════════════════════════════

    public static boolean isActive() { return active; }

    public static ItemStack getFakeStack() {
        ItemStack fs = fakeStack;
        return fs == null ? ItemStack.EMPTY : fs;
    }

    public static int getGhostSlot() { return ghostSlot; }
    /** All fake-occupied slots: the primary {@link #ghostSlot}, every extra stack slot, and every multi-fake storage-GUI grant slot. Empty set when none. */
    public static java.util.Set<Integer> getGhostSlots() {
        java.util.Set<Integer> s = new java.util.HashSet<>();
        if (ghostSlot >= 0) s.add(ghostSlot);
        s.addAll(extraGhostSlots);
        s.addAll(extraFakeStacks.keySet());
        return s;
    }

    /**
     * True when {@code slot} is currently occupied by ANY fake copy — the
     * primary {@link #ghostSlot}, an extra stack slot, or a multi-fake
     * storage-GUI grant (bones / arrows / xp). Used by the sync-suppression
     * mixins so every multi-stack copy AND every granted drop gets the same
     * protection as the single ghost. When the fake is mid-drag on the cursor,
     * {@code ghostSlot} is {code -1} and the extras are ignored (the drag
     * suppressor handles the cursor).
     */
    public static boolean isGhostSlot(int slot) {
        return (ghostSlot >= 0 && slot == ghostSlot)
            || extraGhostSlots.contains(slot)
            || extraFakeStacks.containsKey(slot);
    }

    /**
     * True when any tracked fake (primary spawner, its extra copies, or a
     * storage-GUI grant) currently sits in the player inventory. Used by the
     * click tracker to avoid clobbering grants with the primary fake and to
     * suppress the snap-back branch when the fake lives elsewhere (a placed
     * block).
     */
    public static boolean hasTrackedFakesInInventory() {
        return !findFakeSlotsInInv().isEmpty();
    }

    /** Item + components equality (ignores stack count) — the identity that defines "a fake". */
    public static boolean sameFakeIdentity(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        var ca = a.getComponents();
        var cb = b.getComponents();
        return (ca == null ? cb == null : ca.equals(cb));
    }

    /** True when {@code s} is the PRIMARY fake item (identity-only, count ignored). */
    public static boolean isFakeIdentity(ItemStack s) {
        return s != null && !s.isEmpty() && fakeStack != null && sameFakeIdentity(s, fakeStack);
    }

    /** True when {@code s} is the primary fake or any granted multi-fake stack (identity-only, count ignored). */
    public static boolean isAnyFakeStack(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (fakeStack != null && sameFakeIdentity(s, fakeStack)) return true;
        for (ItemStack granted : extraFakeStacks.values()) {
            if (sameFakeIdentity(s, granted)) return true;
        }
        return false;
    }

    /** The stack that SHOULD sit in inventory slot {@code slot} (primary fake or its granted multi-fake copy). */
    public static ItemStack stackForInventorySlot(int slot) {
        ItemStack granted = extraFakeStacks.get(slot);
        if (granted != null) return granted.copy();
        if (fakeStack != null) return fakeStack.copy();
        return ItemStack.EMPTY;
    }

    /** True when the fake has been consumed into a placed block (keeps reassert from teleporting a copy back). */
    public static boolean isFakePlacedInWorld() {
        return !fakeBlocks.isEmpty();
    }

    public static int getLastGhostSlot() { return lastGhostSlot; }
    public static boolean isCursorIsGhost() { return cursorIsGhost; }

    /** Slot index within the open container's screen handler the fake sits in, or {@code -1}. */
    public static int getGhostContainerSlot() { return ghostContainerSlot; }
    /** syncId of the open container's screen handler the fake sits in, or {@code -1}. */
    public static int getGhostContainerSyncId() { return ghostContainerSyncId; }

    /**
     * Live scan of the OPEN screen handler's non-player-owned slots for every
     * slot that currently holds a copy of the fake (the user may have parked
     * multiple stacks in a chest / shulker / hopper GUI). Player-inventory
     * slots are skipped — they are covered by {@link #findFakeSlotsInInv()}.
     */
    private static java.util.List<Integer> findContainerFakeSlots() {
        java.util.List<Integer> found = new java.util.ArrayList<>();
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return found;
            if (fakeStack == null) return found;
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler == null || handler.syncId < 0) return found;
            for (int i = 0; i < handler.slots.size(); i++) {
                net.minecraft.screen.slot.Slot s = handler.slots.get(i);
                if (s == null || s.inventory instanceof PlayerInventory) continue;
                ItemStack st = s.getStack();
                if (st != null && !st.isEmpty() && stackEquals(st, fakeStack)) found.add(i);
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem findContainerFakeSlots threw", t);
        }
        return found;
    }

    /**
     * True when the screen handler at {@code syncId} + {@code slot} is a
     * container (non-player) slot we REMEMBER holding a fake copy. Used by the
     * outgoing-click and incoming-resync suppression mixins so EVERY fake the
     * user placed in a chest is protected, not just one tracked slot. This is
     * intentionally memory-based ({@link #containerFakeSlots}), NOT a live
     * scan: a server resync may have momentarily emptied the slot, and we must
     * keep suppressing/cancelling it so the memory-based heal can rewrite the
     * fake on the next tick instead of letting the wipe land.
     */
    public static boolean isContainerFakeSlot(int syncId, int slot) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler == null || handler.syncId != syncId) return false;
            if (slot < 0 || slot >= handler.slots.size()) return false;
            net.minecraft.screen.slot.Slot s = handler.slots.get(slot);
            if (s == null || s.inventory instanceof PlayerInventory) return false;
            return containerFakeSlots.contains(slot);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when the open screen handler at {@code syncId} is REMEMBERED to
     * hold at least one fake copy in a non-player slot. Used to decide whether
     * a full-window resync for that screen would wipe fakes.
     */
    public static boolean hasContainerFake(int syncId) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler == null || handler.syncId != syncId) return false;
            return !containerFakeSlots.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Unified, screen-aware fake-slot detection. True when the CURRENT open
     * screen handler (matching {@code syncId}) has a fake-occupied slot at
     * {@code slot}: either a non-player container slot remembered in
     * {@link #containerFakeSlots}, or any slot backed by the player's
     * PlayerInventory whose inventory index is a tracked fake slot (primary +
     * ALL clone stacks). Resolving through the slot's inventory identity makes
     * this correct no matter which window the server addresses — the player
     * inventory lives inside every screen, so a chest-window sync (syncId =
     * chest's) of "player slot 12" (chest-window slot 39) is recognized as a
     * fake exactly like the player-screen form. This is what makes every
     * stack equally a fake item: one detector, full coverage.
     */
    public static boolean isFakeScreenSlot(int syncId, int slot) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            if (fakeStack == null) return false;
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler == null || handler.syncId != syncId) return false;
            if (slot < 0 || slot >= handler.slots.size()) return false;
            net.minecraft.screen.slot.Slot s = handler.slots.get(slot);
            if (s == null) return false;
            if (s.inventory instanceof PlayerInventory) {
                if (s.inventory != c.player.getInventory()) return false;
                return isGhostSlot(s.getIndex());
            }
            return containerFakeSlots.contains(slot);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when the CURRENT open screen handler (matching {@code syncId})
     * contains at least one fake-occupied slot anywhere — player grid or
     * container slots. Used to cancel wholesale window resyncs: every screen
     * embeds the player inventory, so a full-window refresh of any open screen
     * would sweep all fake slots with the server's empty truth.
     */
    public static boolean hasFakeOnScreen(int syncId) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            if (fakeStack == null) return false;
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler == null || handler.syncId != syncId) return false;
            if (!getGhostSlots().isEmpty()) return true;
            if (!containerFakeSlots.isEmpty()) return true;
            return !findContainerFakeSlots().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the fake is currently in an open container (chest / shulker / hopper GUI). */
    public static boolean isFakeInContainer() {
        return !containerFakeSlots.isEmpty() && ghostContainerSyncId >= 0;
    }

    /** Remember container slots that currently hold fake copies. */
    public static void addContainerFakeSlots(int syncId, java.util.List<Integer> slots) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return;
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler == null || handler.syncId != syncId) return;
            if (fakeStack == null) return;
            String key = openContainerKey;
            for (int s : slots) {
                if (s >= 0 && s < handler.slots.size()) {
                    net.minecraft.screen.slot.Slot s2 = handler.slots.get(s);
                    if (s2 != null && !(s2.inventory instanceof PlayerInventory)) {
                        containerFakeSlots.add(s);
                        // Mirror the slot into the persistent backup (keyed by
                        // the captured storage anchor) so it survives this
                        // window closing and is re-injected on the next open
                        // of the same container / ender chest.
                        if (key != null) {
                            ItemStack st = s2.getStack();
                            int cnt = (st != null && !st.isEmpty()) ? st.getCount() : fakeStack.getCount();
                            fakeContainerStorages.computeIfAbsent(key, k -> new java.util.TreeMap<>())
                                .put(s, cnt);
                        }
                    }
                }
            }
            if (!containerFakeSlots.isEmpty()) {
                ghostContainerSyncId = syncId;
                if (ghostContainerSlot < 0 || !containerFakeSlots.contains(ghostContainerSlot)) {
                    ghostContainerSlot = containerFakeSlots.iterator().next();
                }
                containerFakeCount = containerFakeSlots.size();
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem addContainerFakeSlots threw", t);
        }
    }

    /** Forget container slots the user verifiably moved the fake OUT of. */
    public static void removeContainerFakeSlots(int syncId, java.util.List<Integer> slots) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return;
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler == null || handler.syncId != syncId) return;
            String key = openContainerKey;
            for (int s : slots) {
                containerFakeSlots.remove(s);
                // Mirror: a storage slot the user verifiably took the fake OUT
                // of no longer persists here.
                if (key != null) {
                    java.util.TreeMap<Integer, Integer> stored = fakeContainerStorages.get(key);
                    if (stored != null) {
                        stored.remove(s);
                        if (stored.isEmpty()) fakeContainerStorages.remove(key);
                    }
                }
            }
            containerFakeCount = containerFakeSlots.size();
            if (containerFakeSlots.isEmpty()) {
                ghostContainerSlot = -1;
                if (handler.syncId == ghostContainerSyncId) ghostContainerSyncId = -1;
            } else if (ghostContainerSlot < 0 || !containerFakeSlots.contains(ghostContainerSlot)) {
                ghostContainerSlot = containerFakeSlots.iterator().next();
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem removeContainerFakeSlots threw", t);
        }
    }

    /**
     * Record the fake's position inside an open container (called by
     * ScreenHandlerMixin). {@code count} is how many fake copies are parked in
     * that container's non-player slots — it drives the chest-close snap-back
     * so every copy returns to the inventory.
     */
    public static void setGhostContainerLocation(int syncId, int slot, int count) {
        ghostContainerSyncId = syncId;
        ghostContainerSlot = slot;
        containerFakeCount = count;
        if (slot >= 0) containerFakeSlots.add(slot);
    }

    /** Forget the fake's container position (e.g. it was dragged back into the player inv). */
    public static void clearGhostContainerLocation() {
        ghostContainerSlot = -1;
        ghostContainerSyncId = -1;
        containerFakeCount = 0;
        containerFakeSlots.clear();
        openContainerKey = null;
    }

    /**
     * Storage anchor of the block the player just right-clicked, or
     * {@code null} when that block never opens a STORAGE GUI. Called from
     * {@code ClientPlayerInteractionManagerMixin.interactBlock} HEAD so the key
     * is captured BEFORE the open screen arrives. Only actual storage blocks
     * (single-inventory container windows) are eligible:
     * <ul>
     *   <li>{@link net.minecraft.block.EnderChestBlock} → {@code "ender"} — the
     *       contents are shared player state, so ANY ender-chest block reopens
     *       the same persisted slots.</li>
     *   <li>chest / trapped chest / barrel / hopper / placed shulker box →
     *       {@code "pos@x,y,z"} — re-opening the SAME block re-injects.</li>
     * </ul>
     * Other GUI blocks (crafting table, furnace, dispenser, etc.) return null
     * so their windows keep the old snap-back-on-close behaviour.
     */
    public static String storageKeyForBlock(net.minecraft.block.Block block, net.minecraft.util.math.BlockPos pos) {
        try {
            if (block == null || pos == null) return null;
            if (block instanceof net.minecraft.block.EnderChestBlock) return "ender";
            if (block instanceof net.minecraft.block.ChestBlock
                || block instanceof net.minecraft.block.BarrelBlock
                || block instanceof net.minecraft.block.HopperBlock
                || block instanceof net.minecraft.block.ShulkerBoxBlock) {
                return "pos@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Record the storage anchor of the container window now open (from the
     *  interact mixin). {@code null} clears it. */
    public static void captureOpenedContainer(String key) {
        openContainerKey = key;
    }

    /**
     * Drop every persisted fake position across all storage containers
     * ({@code ;clear} / module disable / re-spawn). Container GUI contents are
     * client-side-only ArrayInventory instances discarded at close, so the map
     * IS the only location — real items in the real container are untouched;
     * re-opening the container simply won't re-inject the fakes anymore.
     */
    public static void clearFakeContainerStorages() {
        try {
            fakeContainerStorages.clear();
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem clearFakeContainerStorages threw", t);
        }
    }

    /**
     * True when the open (or player) screen handler at {@code syncId} + {@code slot} is where the fake lives.
     * Used by packet mixins to decide which resync traffic to cancel vs. let through.
     */
    public static boolean isFakeLocation(int syncId, int slot) {
        if (isContainerFakeSlot(syncId, slot)) return true;
        if (slot == ghostContainerSlot && syncId == ghostContainerSyncId) return true;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null) return false;
        // PlayerScreenHandler slot numbering == PlayerInventory index.
        int playerSyncId = c.player.playerScreenHandler.syncId;
        return syncId == playerSyncId && slot == ghostSlot;
    }

    /** Current equipment slot the fake equips to (from its EQUIPPABLE component), or null. */
    public static EquipmentSlot getFakeEquipSlot() {
        ItemStack fs = getFakeStack();
        if (fs.isEmpty()) return null;
        EquippableComponent eq = fs.get(DataComponentTypes.EQUIPPABLE);
        return eq != null ? eq.slot() : null;
    }

    /** True when the spawned item is the elytra (drives the cosmetic wing pose). */
    public static boolean isFakeElytra() {
        ItemStack fs = getFakeStack();
        return !fs.isEmpty() && fs.getItem() == Items.ELYTRA;
    }

    /** Read the "Forge World Items" toggle without a module instance. */
    public static boolean isForgingWorldItems() {
        try {
            FakeItem fake = com.anormal.client.module.ModuleManager.getModule(FakeItem.class);
            return fake != null && fake.forgeWorldItems.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Per-item-frame placement ("put the fake in THIS frame")
    // ────────────────────────────────────────────────────────────

    /** True when the given item-frame entity ID has the fake placed in it. */
    public static boolean isFrameFaked(int entityId) {
        return fakeFrames.contains(entityId);
    }

    /** Mark an item frame as holding the fake (clientside visual only). */
    public static void markFrame(int entityId) {
        fakeFrames.add(entityId);
    }

    /** Un-mark an item frame (the fake no longer renders there). */
    public static void unmarkFrame(int entityId) {
        fakeFrames.remove(entityId);
    }

    /**
     * Toggle fake-placement on an item frame; returns the new state.
     *
     * <p>PLACEMENT marks the frame AND consumes ONE copy out of the player's
     * fake stack in the inventory — preferably from the slot actually being
     * held ({@code heldSlot}), so the item in the user's hand is the one that
     * visibly leaves and appears in the frame (with multiple stacks a blind
     * "first slot" lookup would shrink the WRONG stack). Removal unmarks the
     * frame and returns ONE copy to the inventory, merging into the held slot
     * first, then any partial fake stack, then the main hand. The frame
     * rendering is purely clientside; the server never sees the interaction.
     */
    public static boolean toggleFrame(int entityId, int heldSlot) {
        if (fakeFrames.contains(entityId)) {
            fakeFrames.remove(entityId);
            giveBackFakeOne(heldSlot);
            return false;
        }
        fakeFrames.add(entityId);
        takeFakeOne(heldSlot);
        return true;
    }

    /**
     * Consume ONE copy of the primary fake from the player inventory when a
     * placement puts it into an item frame (mirrors the consumed count of
     * {@link #placeFakeBlock}). Prefers the {@code heldSlot} — the slot the
     * click used — so the held item itself leaves the inventory; falls back to
     * the first slot still holding the fake. When the slot being consumed had
     * the last copy it is emptied and the ghost tracker resets.
     */
    private static void takeFakeOne(int heldSlot) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return;
            PlayerInventory inv = c.player.getInventory();
            if (heldSlot >= 0 && heldSlot < inv.size()) {
                ItemStack held = inv.getStack(heldSlot);
                if (held != null && !held.isEmpty() && isFakeIdentity(held)) {
                    if (consumeFakeSlot(inv, heldSlot)) return;
                }
            }
            for (int i = 0; i < inv.size(); i++) {
                ItemStack held = inv.getStack(i);
                if (held == null || held.isEmpty() || !isFakeIdentity(held)) continue;
                if (consumeFakeSlot(inv, i)) return;
            }
            dbg("frame-take", "no fake slot to consume");
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem takeFakeOne threw", t);
        }
    }

    /** Decrement {@code slot}'s fake stack by one; returns true on success. */
    private static boolean consumeFakeSlot(PlayerInventory inv, int slot) {
        ItemStack held = inv.getStack(slot);
        if (held == null || held.isEmpty()) return false;
        if (held.getCount() <= 1) {
            inv.setStack(slot, ItemStack.EMPTY);
            if (ghostSlot == slot) { ghostSlot = -1; lastGhostSlot = -1; }
            extraGhostSlots.remove(slot);
            liveFakeCount = 0;
        } else {
            inv.setStack(slot, held.copyWithCount(held.getCount() - 1));
            liveFakeCount = held.getCount() - 1;
        }
        dbg("frame-take", "consumed one from inv slot " + slot + " remaining=" + liveFakeCount);
        return true;
    }

    /**
     * Return ONE copy of the primary fake to the inventory when it is removed
     * from an item frame: merges into the held slot first (the slot the click
     * used, so the recovered copy lands back on the item you're holding),
     * then an existing partial fake stack, otherwise drops a fresh copy into
     * the main hand, falling back to the first empty main-inventory slot.
     */
    private static void giveBackFakeOne(int heldSlot) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return;
            ItemStack fake = getFakeStack();
            if (fake == null || fake.isEmpty()) return;
            PlayerInventory inv = c.player.getInventory();
            if (heldSlot >= 0 && heldSlot < inv.size()) {
                ItemStack held = inv.getStack(heldSlot);
                if (held != null && !held.isEmpty() && isFakeIdentity(held)
                        && held.getCount() < held.getMaxCount()) {
                    inv.setStack(heldSlot, held.copyWithCount(held.getCount() + 1));
                    liveFakeCount = held.getCount() + 1;
                    dbg("frame-giveback", "merged one back into held slot " + heldSlot + " count=" + liveFakeCount);
                    return;
                }
            }
            for (int i = 0; i < inv.size(); i++) {
                ItemStack held = inv.getStack(i);
                if (held == null || held.isEmpty() || !isFakeIdentity(held)) continue;
                if (held.getCount() < held.getMaxCount()) {
                    inv.setStack(i, held.copyWithCount(held.getCount() + 1));
                    liveFakeCount = held.getCount() + 1;
                    dbg("frame-giveback", "merged one back into slot " + i + " count=" + liveFakeCount);
                    return;
                }
            }
            ItemStack one = fake.copy();
            one.setCount(1);
            int hand = inv.getSelectedSlot();
            if (hand < inv.size() && (inv.getStack(hand) == null || inv.getStack(hand).isEmpty())) {
                inv.setStack(hand, one);
                ghostSlot = hand;
                liveFakeCount = 1;
                dbg("frame-giveback", "fresh copy into hand slot " + hand);
                return;
            }
            for (int i = 0; i < 36; i++) {
                if (inv.getStack(i) == null || inv.getStack(i).isEmpty()) {
                    inv.setStack(i, one);
                    ghostSlot = i;
                    liveFakeCount = 1;
                    dbg("frame-giveback", "fresh copy into empty slot " + i);
                    return;
                }
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem giveBackFakeOne threw", t);
        }
    }

    /** Wipe all per-frame fake placements ({@code ;clear} / disable). */
    public static void clearFakeFrames() {
        fakeFrames.clear();
    }

    // ────────────────────────────────────────────────────────────
    //  Integrated-server (singleplayer / LAN host) path
    // ────────────────────────────────────────────────────────────

    /**
     * True when the local player is hosting an integrated (singleplayer / LAN)
     * server <b>in-process</b>. In that case the integrated
     * {@link net.minecraft.server.network.ServerPlayerEntity} lives in the same
     * JVM as the mc and we can make the fake a <i>real</i> item there too —
     * then the server agrees the player has the fake and ALL vanilla behaviours
     * (drop, item-frame placement/break, real elytra flight, armor wearing)
     * work natively with zero special-casing. On a dedicated server (DonutSMP),
     * {@link MinecraftClient#getServer()} returns null and we stay clientside.
     */
    public static boolean isIntegratedServer() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            return c != null && c.player != null && c.getServer() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when connected to a remote/dedicated server (e.g. DonutSMP) — the
     * opposite of {@link #isIntegratedServer()}. The clientside-ghost path and
     * all its simulation mixins (drop, frame-break, outgoing-click suppression,
     * incoming-sync cancellation, cosmetic wing pose, frame-marker overlay)
     * only fire under this gate.
     */
    public static boolean isDedicatedServer() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            boolean dedicated = c != null && c.player != null && c.getServer() == null;
            // Log the FIRST transition into dedicated mode so we have a baseline
            // for every other gate's snapshotted state. Subsequent calls are
            // cheap no-ops. This is the load-bearing diagnostic: if it doesn't
            // fire on connect, every feature is silently off.
            if (dedicated && !wasDedicatedLogged) {
                wasDedicatedLogged = true;
                com.anormal.client.lucid.LucidLog.LOGGER.info("[FakeItem-DBG] CONNECTED to dedicated server (DonutSMP). {}", snapshot());
            }
            return dedicated;
        } catch (Throwable t) {
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Debug logging — temporary instrumentation for the DonutSMP
    //  "features don't fire" investigation. All gated behind the
    //  {@link #DEBUG} flag so release builds emit nothing unless
    //  we flip it on. Log tag is "[FakeItem-DBG]".
    // ────────────────────────────────────────────────────────────

    /** Master debug switch — flip on to log every gate from every mixin. */
    public static final boolean DEBUG = true;

    /** Counter throttling the periodic dropped-entity position sweep log. */
    private static volatile int dropSweepCnt = 0;

    /**
     * Play a sound purely clientside at a world position — the server never
     * hears it (SoundManager.play does not emit a packet). Used for the fake
     * item-frame add/remove pops and the walkover pickup.
     */
    public static void playLocalSound(net.minecraft.sound.SoundEvent event,
                                      double x, double y, double z,
                                      float volume, float pitch) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.getSoundManager() == null) return;
            net.minecraft.util.math.random.Random rng =
                (c.world != null) ? c.world.getRandom()
                                  : net.minecraft.util.math.random.Random.create();
            c.getSoundManager().play(new net.minecraft.client.sound.PositionedSoundInstance(
                event, net.minecraft.sound.SoundCategory.PLAYERS, volume, pitch, rng, x, y, z));
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem playLocalSound threw", t);
        }
    }

    /** Sound for toggling a fake in/out of an item frame (called by the interact mixin). */
    public static void playFrameToggleSound(net.minecraft.entity.Entity frame, boolean added) {
        try {
            net.minecraft.util.math.Vec3d p = frame.getLerpedPos(1.0f);
            playLocalSound(added
                    ? net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM
                    : net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM,
                p.x, p.y, p.z, 1.0f, 1.0f);
        } catch (Throwable ignored) {}
    }

    /**
     * Smoothed cosmetic glide-pose progress for the local player (0 = folded,
     * 1 = full glide). Owned and tick-smoothed HERE (onTickAlways); the render
     * mixins only read it.
     */
    private static volatile float glideProgress = 0.0f;

    /** Current cosmetic glide-pose progress (0..1). */
    public static float getGlideProgress() { return glideProgress; }

    /**
     * Advance the glide-pose progress one RENDER FRAME toward the target —
     * called by PlayerEntityRendererMixin's per-frame hook so the open/close
     * animation runs at full frame rate (smooth at 60+fps) instead of the
     * visibly steppy 20Hz tick updates. 0.07/frame completes the pose in
     * ~40 frames (~0.7s at 60fps) — a gentle drift into the glide rather than
     * the snappy ~0.28s snap that read as a sudden nose-dive.
     */
    public static void stepGlidePose(boolean targetOn) {
        float target = targetOn ? 1.0f : 0.0f;
        glideProgress += (target - glideProgress) * 0.07f;
        if (glideProgress < 0.02f) glideProgress = 0.0f;
        if (glideProgress > 0.995f) glideProgress = 1.0f;
    }

    /**
     * Vanilla-faithful wing pose, replicating the state machine of
     * {@code net.minecraft.entity.mob.ElytraFlightController} exactly (verified
     * from the 1.21.11 bytecode). On DonutSMP that controller never updates
     * for a fake elytra �?" its update is tied to the server-owned glide state �?"
     * so we run the SAME math here, once per tick from {@link #onTickAlways}:
     *
     * <ul>
     *   <li><b>Idle</b> (folded, wings seated on the back): pitch 0.2618,
     *       roll -0.2618, yaw 0.</li>
     *   <li><b>Sneak</b> (vanilla's crouch pose): pitch 0.69813 (40°), roll
     *       -0.7854 (-45°), yaw 0.0873 (5°). This is the real
     *       "wings spread while crouching" shape.</li>
     *   <li><b>Glide</b>: spread factor {@code 1 - (-vyNorm)^1.5} �?" full
     *       spread only while velocity runs close to horizontal, folding back
     *       toward the idle shape when falling steeply �?" lerping pitch
     *       {@code 0.2618 -> 0.3491}, roll {@code -0.2618 -> -1.5708}, yaw 0.</li>
     * </ul>
     *
     * <p>Each value smooths toward its target by 0.3/tick, exactly like the
     * real controller, and the model mirror negates yaw/roll on the right
     * wing. The body lean stays on {@link #glideProgress}; the wings follow
     * this controller.
     */
    private static volatile float elytraWingPitch = 0.2617994f;
    private static volatile float elytraWingYaw = 0.0f;
    private static volatile float elytraWingRoll = -0.2617994f;

    // Per-tick snapshots (taken at the start of each stepWingController call)
    // so the renderer can interpolate between ticks by the partial tick and
    // render the wing animation at full frame rate instead of 20Hz steps.
    private static volatile float elytraWingPitchPrev = 0.2617994f;
    private static volatile float elytraWingYawPrev = 0.0f;
    private static volatile float elytraWingRollPrev = -0.2617994f;

    public static float getElytraWingPitch() { return elytraWingPitch; }
    public static float getElytraWingYaw() { return elytraWingYaw; }
    public static float getElytraWingRoll() { return elytraWingRoll; }
    public static float getElytraWingPitchPrev() { return elytraWingPitchPrev; }
    public static float getElytraWingYawPrev() { return elytraWingYawPrev; }
    public static float getElytraWingRollPrev() { return elytraWingRollPrev; }

    /**
     * Advance the wing controller one TICK toward its target pose (same
     * 0.3/tick smoothing as vanilla's {@code ElytraFlightController}).
     * Called from {@link #onTickAlways} whenever the fake elytra is equipped.
     */
    public static void stepWingController() {
        if (!isFakeElytraEquipped()) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null) return;

        boolean gliding = isCosmeticGliding();
        boolean sneaking = false;
        try { sneaking = c.options.sneakKey.isPressed(); } catch (Throwable ignored) {}

        float tPitch, tRoll, tYaw;
        if (gliding) {
            // Vanilla: spreadFactor = 1 - (-vyNorm)^1.5 while falling. The
            // wings fold toward the idle shape on a steep dive and stay fully
            // spread in level flight. A null velocity (length ~0) reads level.
            Vec3d vel = c.player.getVelocity();
            float len = (float) vel.length();
            float vyNorm = len < 1.0e-5f ? 0.0f : (float) vel.y / len;
            float down = Math.min(1.0f, Math.max(0.0f, -vyNorm));
            float spread = 1.0f - (float) Math.pow(down, 1.5);
            tPitch = MathHelper.lerp(spread, 0.2617994f, 0.34906584f);
            tRoll = MathHelper.lerp(spread, -0.2617994f, -1.5707964f);
            tYaw = 0.0f;
        } else if (sneaking) {
            tPitch = 0.6981317f;
            tRoll = -0.7853982f;
            tYaw = 0.08726646f;
        } else {
            tPitch = 0.2617994f;
            tRoll = -0.2617994f;
            tYaw = 0.0f;
        }

        elytraWingPitchPrev = elytraWingPitch;
        elytraWingYawPrev = elytraWingYaw;
        elytraWingRollPrev = elytraWingRoll;
        elytraWingPitch += (tPitch - elytraWingPitch) * 0.3f;
        elytraWingYaw += (tYaw - elytraWingYaw) * 0.3f;
        elytraWingRoll += (tRoll - elytraWingRoll) * 0.3f;
    }

    /**
     * Maximum forward body-lean of the cosmetic glide (degrees).
     */
    private static final float MAX_BODY_LEAN_DEG = 75.0f;

    /**
     * Base forward body-lean of the cosmetic glide at a level glance (degrees).
     */
    private static final float BASE_BODY_LEAN_DEG = 45.0f;

    /**
     * The glide body-lean angle in DEGREES. Vanilla's superman formula is
     * {@code progress · (-90° - pitchDeg)} — but that overshoots: a player
     * glancing only 30° down gets a 120° lean, pitching the whole figure
     * straight into the dirt (the "nose dive" / "head looks down" reads). Our
     * lean instead starts at a gentle 45° at a level glance and deepens by
     * only 0.5° for every degree the player looks down, hard-capped at 75°:
     *
     * <p>{@code lean = -progress · clamp(45 + pitchDeg/2, 0, 75)}
     *
     * <p>Shared by the renderer body-lean hook and the head-compensation hook
     * so both stay in lock-step — the head holds the horizon relative to
     * whatever lean the body reaches, never pointing down at the ground.
     */
    public static float bodyLeanDeg(float progress, float pitchDeg) {
        float raw = -(BASE_BODY_LEAN_DEG + pitchDeg * 0.5f);
        if (raw > 0.0f) raw = 0.0f;                      // never lean backward
        if (raw < -MAX_BODY_LEAN_DEG) raw = -MAX_BODY_LEAN_DEG;
        return progress * raw;
    }

    // ── Cosmetic glide (double-tap space) state ──
    private static volatile boolean cosmeticGlide = false;
    private static volatile boolean jumpWasDown = false;
    private static volatile long lastJumpTapTick = Long.MIN_VALUE;
    private static volatile long tickNow = 0;

    /** True while the cosmetic glide pose is engaged (double-tap space, until landing). */
    public static boolean isCosmeticGliding() { return cosmeticGlide; }

    /** True when the fake elytra is actually equipped in the chest armor slot. */
    public static boolean isFakeElytraEquipped() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            if (!isFakeElytra()) return false;
            ItemStack chest = c.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
            return stackEquals(chest, getFakeStack());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Consecutive getEntityById misses per tracked drop, for the un-track grace period. */
    private static final java.util.Map<Integer, Integer> dropLookupMisses =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** One-line snapshot of the FakeItem tracker state for log context. */
    public static String snapshot() {
        try {
            return "active=" + active
                + " dedicated=" + isDedicatedServer()
                + " ghostSlot=" + ghostSlot
                + " lastGhostSlot=" + lastGhostSlot
                + " cursorIsGhost=" + cursorIsGhost
                + " containerSlot=" + ghostContainerSlot
                + " containerSyncId=" + ghostContainerSyncId
                + " simKind=" + currentSimKind
                + " fake=" + (fakeStack == null ? "null"
                              : fakeStack.getItem().getName().getString());
        } catch (Throwable t) {
            return "snapshot-threw:" + t;
        }
    }

    /** Debug log: tag + snapshot + message. Cheap because gated + one-line. */
    public static void dbg(String tag, String msg) {
        if (!DEBUG) return;
        try {
            com.anormal.client.lucid.LucidLog.LOGGER.info("[FakeItem-DBG] {} :: {} :: {}", tag, snapshot(), msg);
        } catch (Throwable ignored) {}
    }

    /** Debug log for hot-path events (no snapshot, single arg). */
    public static void dbgLight(String tag, String msg) {
        if (!DEBUG) return;
        try {
            com.anormal.client.lucid.LucidLog.LOGGER.info("[FakeItem-DBG] {} :: {}", tag, msg);
        } catch (Throwable ignored) {}
    }

    /**
     * Write the fake into the integrated server's authoritative player inventory
     * too, scheduled on the server thread (via {@link
     * net.minecraft.server.MinecraftServer#execute}). Pass a null stack to
     * clear. No-op on a dedicated server or when no integrated server is running.
     */
    private static void syncToIntegratedServer(int slot, ItemStack stack) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            net.minecraft.server.MinecraftServer server = c.getServer();
            if (server == null || c.player == null) return;
            java.util.UUID uuid = c.player.getUuid();
            server.execute(() -> {
                try {
                    net.minecraft.server.network.ServerPlayerEntity sp = server.getPlayerManager().getPlayer(uuid);
                    if (sp == null) return;
                    sp.getInventory().setStack(slot, stack == null ? ItemStack.EMPTY : stack);
                } catch (Throwable t) {
                    com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem syncToIntegratedServer write threw", t);
                }
            });
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem syncToIntegratedServer schedule threw", t);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Dropped-entity tracking (DonutSMP "drop and re-absorb")
    // ────────────────────────────────────────────────────────────

    /**
     * Entity IDs of item entities we spawned locally to drop the fake (Q-press
     * or "frame pop") on a dedicated server. Tracked so the per-tick
     * {@link #tickWalkoverPickup} pass can re-absorb them into the player's
     * inventory when the player walks over them — same gesture as picking up a
     * real dropped item. Cleared on {@code ;clear} / disable.
     */
    private static final java.util.Set<Integer> fakeDropped =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void registerDropped(int id) { fakeDropped.add(id); }
    public static void unregisterDropped(int id) { fakeDropped.remove(id); }
    public static void clearFakeDropped() { fakeDropped.clear(); }
    public static boolean isDroppedTracked(int id) { return fakeDropped.contains(id); }

    /**
     * Discard every tracked fake-dropped entity currently in the mc world
     * (and clear the tracking set). Used by {@code ;clear} / module-disable so a
     * pending walkover pickup doesn't outlive the module. Safe to call when the
     * world is null (e.g. just before disconnect).
     */
    public static void clearFakeDroppedEntities() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.world != null) {
                for (int id : fakeDropped) {
                    try {
                        net.minecraft.entity.Entity e = c.world.getEntityById(id);
                        if (e != null && !e.isRemoved()) e.discard();
                    } catch (Throwable ignored) {
                        // Per-entity failure shouldn't abort the sweep.
                    }
                }
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem clearFakeDroppedEntities threw", t);
        }
        fakeDropped.clear();
        dropLookupMisses.clear();
    }

    /**
     * Per-tick walkover pickup: scan tracked fake-dropped entities and, if the
     * local player walks within pickup distance of one, re-absorb its stack
     * into the player's inventory (re-establishing the ghost slot) and discard
     * the dropped entity. Mimics the vanilla "walk over an item to pick it up"
     * gesture purely clientside — the server never sees the item, but the
     * gesture feels real to the user. Runs only on a dedicated server (on the
     * integrated server, drops are real and vanilla handles the pickup itself).
     */
    private static void tickWalkoverPickup() {
        try {
            if (!isDedicatedServer()) return;  // singleplayer uses real drops
            if (fakeDropped.isEmpty()) return;
            if (mc.player == null || mc.world == null) return;

            PlayerInventory inv = mc.player.getInventory();
            java.util.List<Integer> toRemove = new java.util.ArrayList<>();
            net.minecraft.util.math.Vec3d playerPos = mc.player.getLerpedPos(1.0f);

            for (int id : fakeDropped) {
                net.minecraft.entity.Entity e = mc.world.getEntityById(id);
                if (e == null) {
                    // Transient lookup miss — the entity-manager's id lookup
                    // can lag the addEntity commit by a few ticks. Only stop
                    // tracking after ~2s of CONSECUTIVE misses (entity really
                    // despawned / unloaded), never on the first one.
                    int misses = dropLookupMisses.merge(id, 1, Integer::sum);
                    if (misses > 40) {
                        toRemove.add(id);
                        dropLookupMisses.remove(id);
                    }
                    continue;
                }
                dropLookupMisses.remove(id);
                if (e.isRemoved() || !(e instanceof net.minecraft.entity.ItemEntity)) {
                    // Entity gone (despawned / unloaded) — stop tracking it.
                    toRemove.add(id);
                    continue;
                }
                // Kill the rubber-ball bounce: clientside-spawned ItemEntities
                // were observed bouncing ~1.3 blocks back up on landing
                // (drop-sweep showed vy=+0.157 while rising AFTER hitting the
                // floor) — real dropped items settle almost immediately. When
                // a tracked drop is on the ground but still moving upward,
                // cancel the vertical velocity so it settles like a real item.
                try {
                    if (e.isOnGround() && e.getVelocity().y > 0.05) {
                        net.minecraft.util.math.Vec3d vel = e.getVelocity();
                        e.setVelocity(vel.x, 0.0, vel.z);
                    }
                } catch (Throwable ignored) {}
                net.minecraft.entity.ItemEntity ie = (net.minecraft.entity.ItemEntity) e;
                // Respect the ItemEntity's pickup delay (we set 20 ticks at
                // spawn, mirroring vanilla) — without this check a fresh drop
                // spawns inside the 1.5-block walkover radius and gets
                // re-absorbed on the very next tick ("instantly picks it up
                // as soon as I drop them"). cannotPickup() is vanilla's own
                // delay gate.
                if (ie.cannotPickup()) continue;
                double dist = ie.getLerpedPos(1.0f).squaredDistanceTo(playerPos);                // Vanilla pickup radius is ~1.0 (squared ~1.0); use ~1.5^2 so
                // the user has to be close-ish but the sweep is forgiving.
                if (dist <= 1.5 * 1.5) {
                    ItemStack dropStack = ie.getStack();
                    if (dropStack == null || dropStack.isEmpty()) {
                        toRemove.add(id);
                        continue;
                    }
                    // MERGE-first pickup: if the fake is ALREADY in the
                    // inventory with room, add the picked-up copies onto that
                    // stack (63 → 64) instead of allocating a new hotbar slot
                    // for a stack that already exists. Mirrors vanilla stacking.
                    int mergeTarget = -1;
                    if (ghostSlot >= 0 && ghostSlot < inv.size()
                            && isFakeIdentity(inv.getStack(ghostSlot))
                            && !inv.getStack(ghostSlot).isEmpty()) {
                        mergeTarget = ghostSlot;
                    } else {
                        for (int mi = 0; mi < inv.size(); mi++) {
                            if (isFakeIdentity(inv.getStack(mi)) && !inv.getStack(mi).isEmpty()) {
                                mergeTarget = mi;
                                break;
                            }
                        }
                    }
                    if (mergeTarget >= 0) {
                        ItemStack cur = inv.getStack(mergeTarget);
                        int max = cur.getMaxCount();
                        if (cur.getCount() < max) {
                            int mergedCount = Math.min(max, cur.getCount() + dropStack.getCount());
                            ItemStack merged = dropStack.copy();  // same identity; use the entity's stack
                            merged.setCount(mergedCount);
                            inv.setStack(mergeTarget, merged);
                            ghostSlot = mergeTarget;
                            lastGhostSlot = mergeTarget;
                            cursorIsGhost = false;
                            ie.discard();
                            toRemove.add(id);
                            playLocalSound(net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP,
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                                0.2f, (mc.world.getRandom().nextFloat() - mc.world.getRandom().nextFloat()) * 0.7F + 1.0F);
                            dbg("walkover-pickup", "MERGE id=" + id + " target=" + mergeTarget
                                + " count=" + mergedCount + " dist^2=" + (float) dist);
                            continue;
                        }
                    }
                    // No existing fake stack to merge into (or it's full) —
                    // fall back to a fresh slot: the original held slot if it's
                    // still empty (the fake returns where it was dropped from),
                    // else the first empty hotbar slot, else any empty slot.
                    int target = (lastGhostSlot >= 0
                        && lastGhostSlot < inv.size()
                        && inv.getStack(lastGhostSlot).isEmpty())
                        ? lastGhostSlot : firstEmptyHotbar(inv);
                    if (target < 0) target = firstEmpty(inv);
                    if (target < 0) {
                        // Inventory full — leave the dropped entity on the
                        // ground for now; the user can clear out space and
                        // walk over it again next tick.
                        continue;
                    }
                    inv.setStack(target, dropStack.copy());
                    ghostSlot = target;
                    lastGhostSlot = target;
                    cursorIsGhost = false;
                    ie.discard();
                    toRemove.add(id);
                    // Vanilla's item-pickup "pop" — mirror locally at the player.
                    playLocalSound(net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP,
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        0.2f, (mc.world.getRandom().nextFloat() - mc.world.getRandom().nextFloat()) * 0.7F + 1.0F);
                    dbg("walkover-pickup", "id=" + id + " target=" + target + " dist^2=" + (float) dist);
                }
            }
            // Delayed diagnostic: every 20 ticks (~1s), log each still-tracked
            // dropped entity's live position + vertical velocity. A healthy
            // falling item shows y decreasing and vy negative then onGround
            // flipping true when it lands; a constant y across sweeps would
            // mean clientside-spawned ItemEntities are not being ticked at
            // all (which would explain "drops disappear / never fall").
            if (dropSweepCnt++ % 20 == 0 && !fakeDropped.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int id : fakeDropped) {
                    net.minecraft.entity.Entity e2 = mc.world.getEntityById(id);
                    if (e2 == null) { sb.append(" id=").append(id).append(":GONE"); continue; }
                    net.minecraft.util.math.Vec3d p = e2.getLerpedPos(1.0f);
                    sb.append(" id=").append(id)
                        .append(":y=").append(String.format("%.2f", p.y))
                        .append(",vy=").append(String.format("%.3f", e2.getVelocity().y))
                        .append(",vx=").append(String.format("%.3f", e2.getVelocity().x))
                        .append(",vz=").append(String.format("%.3f", e2.getVelocity().z))
                        .append(",onGround=").append(e2.isOnGround());
                }
                dbgLight("drop-sweep", "tracked=" + fakeDropped.size() + sb);
            }
            for (int id : toRemove) fakeDropped.remove(id);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem tickWalkoverPickup threw", t);
        }
    }

    /** First empty hotbar slot index (0..8), or {@code -1}. */
    private static int firstEmptyHotbar(PlayerInventory inv) {
        try {
            for (int i = 0; i < PlayerInventory.getHotbarSize() && i < inv.size(); i++) {
                if (inv.getStack(i).isEmpty()) return i;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /** First empty slot anywhere in the player's inventory, or {@code -1}. */
    private static int firstEmpty(PlayerInventory inv) {
        try {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStack(i).isEmpty()) return i;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /**
     * First empty inventory slot that isn't one of the {@link #extraGhostSlots}
     * (the primary ghost slot is already occupied — i.e. non-empty — so the
     * isEmpty check covers it). Used to lay out extra stacks.
     */
    private static int firstEmptyAvail(PlayerInventory inv) {
        try {
            for (int i = 0; i < inv.size(); i++) {
                if (extraGhostSlots.contains(i)) continue;
                if (inv.getStack(i).isEmpty()) return i;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    // ────────────────────────────────────────────────────────────
    //  DonutSMP-only drop / frame-break simulation
    // ────────────────────────────────────────────────────────────

    /**
     * Spawn a clientside fake dropped {@link net.minecraft.entity.ItemEntity}
     * at the given (world-relative) position + a small upward/forward bias,
     * holding the fake stack, and register it for walkover pickup by
     * {@link #tickWalkoverPickup}. Returns immediately on singleplayer (real
     * drops are used there). Also re-establishes the "last held slot" so a
     * later pickup places the fake back as close as possible to its origin.
     *
     * <p>Caller is responsible for having already cleared the fake's prior
     * slot (so the inventory-side shows the drop, and the ItemEntity is the
     * lone copy of the fake). This method only spawns the world entity.
     *
     * @param x  world X to spawn at
     * @param y  world Y to spawn at
     * @param z  world Z to spawn at
     * @param vx outward velocity X (e.g. forward bias for a Q-drop)
     * @param vy outward velocity Y (typically slight up)
     * @param vz outward velocity Z
     * @param originSlot the slot the fake just left, recorded as
     *                   {@code lastGhostSlot} so walkover pickup returns it there
     */
    public static void spawnFakeDrop(double x, double y, double z,
                                     double vx, double vy, double vz,
                                     int originSlot) {
        try {
            int id = spawnFakeDropReturnId(x, y, z, vx, vy, vz, originSlot);
            if (id < 0) return;
            MinecraftClient c = MinecraftClient.getInstance();
            net.minecraft.entity.Entity ie = c != null && c.world != null ? c.world.getEntityById(id) : null;
            boolean aliveNow = false;
            try { aliveNow = ie != null && ie.isAlive(); } catch (Throwable ignored) {}
            dbg("spawnFakeDrop", "OK eid=" + id + " pos=(" + (int)x + "," + (int)y + "," + (int)z + ") origin=" + originSlot + " isAlive=" + aliveNow + " trackedTotal=" + fakeDropped.size());
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem spawnFakeDrop threw", t);
        }
    }

    /**
     * Q-drop simulation on a dedicated server: take the fake OUT of its held
     * hotbar slot (ONE copy for Q, the whole stack for Ctrl+Q), spawn a
     * clientside {@link ItemEntity} with a slight forward/up toss at the
     * player's eye position, and register it for walkover pickup. The outgoing
     * drop packet is suppressed by the caller
     * ({@link java.lang.Object}).
     *
     * <p>Vanilla's mc prediction mutates the SELECTED slot BEFORE the
     * outgoing packet reaches our hook: Q decrements it by 1, Ctrl+Q clears it.
     * This method therefore never assumes the slot still holds the untouched
     * stack — it distinguishes "vanilla already decremented this slot" from
     * "nobody touched it" via {@link #liveFakeCount} (synced from the slot on
     * the previous tick).
     *
     * <p>Singleplayer: vanilla handles the drop natively (server agrees, so the
     * drop is real + pickup-able).
     *
     * @param dropAll true for Ctrl+Q (the entire stack leaves, one merged entity),
     *                false for plain Q (exactly one copy leaves)
     */
    public static boolean dropFakeFromHeld(boolean dropAll) {
        try {
            if (!isDedicatedServer()) {
                dbg("dropFakeFromHeld", "SKIP not-dedicated");
                return false;
            }
            if (fakeStack == null || ghostSlot < 0) {
                dbg("dropFakeFromHeld", "SKIP fakeStack=" + (fakeStack == null) + " ghostSlot=" + ghostSlot);
                return false;
            }
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) {
                dbg("dropFakeFromHeld", "SKIP no-client/player");
                return false;
            }
            PlayerInventory inv = c.player.getInventory();
            if (ghostSlot >= inv.size()) {
                dbg("dropFakeFromHeld", "SKIP ghostSlot out-of-range");
                return false;
            }
            int origin = ghostSlot;
            int selected = inv.getSelectedSlot();
            ItemStack held = inv.getStack(origin);
            boolean slotHoldsFake = isFakeIdentity(held);
            boolean vanillaClearedSlot = !slotHoldsFake
                && (held == null || held.isEmpty())
                && origin == selected;
            if (!slotHoldsFake && !vanillaClearedSlot) {
                dbg("dropFakeFromHeld", "FAIL held-slot-mismatch held="
                    + (held == null ? "null" : held.getItem().getName().getString())
                    + " selectedSlot=" + selected);
                return false;
            }

            // How many fake items leave the inventory in this gesture.
            //   Q      → exactly ONE copy, always.
            //   Ctrl+Q → the whole stack. If vanilla's prediction already
            //            cleared the slot, the pre-drop count is gone from the
            //            slot itself — fall back to liveFakeCount (last tick's
            //            authoritative count).
            int dropCount;
            if (dropAll) {
                dropCount = slotHoldsFake ? held.getCount() : liveFakeCount;
                if (dropCount < 1) dropCount = fakeStack.getMaxCount();
                if (dropCount < 1) dropCount = 1;
            } else {
                dropCount = 1;
            }

            if (slotHoldsFake) {
                int remaining;
                if (dropAll) {
                    remaining = 0;
                } else if (origin == selected && held.getCount() < liveFakeCount) {
                    // Vanilla already decremented this selected slot for the Q
                    // press; keep its result and only add the visual entity.
                    remaining = held.getCount();
                } else {
                    // Nobody touched this slot yet (fallback from a non-selected
                    // fake slot) — WE do the decrement.
                    remaining = held.getCount() - 1;
                }
                if (remaining <= 0) {
                    inv.setStack(origin, ItemStack.EMPTY);
                    ghostSlot = -1;
                } else {
                    inv.setStack(origin, held.copyWithCount(remaining));
                    ghostSlot = origin;
                }
            } else {
                // Vanilla's prediction already cleared the selected slot.
                ghostSlot = -1;
            }

            // Vanilla item-drop physics (PlayerEntity.dropItem): yaw-only
            // horizontal velocity from just below eye height (pitch is NOT
            // applied). Power scaled down from vanilla's 0.3/0.1 — the user
            // reported the throw had "way too much power", so a gentler toss.
            float yawRad = c.player.getYaw() * ((float) Math.PI / 180f);
            double fx = -Math.sin(yawRad) * 0.2;
            double fz = Math.cos(yawRad) * 0.2;
            net.minecraft.util.math.Vec3d eye = c.player.getEyePos();
            spawnFakeDropReturnId(eye.x + fx, eye.y - 0.3, eye.z + fz,
                                  fx, 0.05, fz, origin, dropCount);
            lastGhostSlot = origin;
            cursorIsGhost = false;
            liveFakeCount = dropAll ? 0 : Math.max(0, liveFakeCount - 1);
            int remainingNow = (ghostSlot >= 0 && ghostSlot < inv.size()) ? inv.getStack(ghostSlot).getCount() : 0;
            dbg("dropFakeFromHeld", "OK origin=" + origin + " dropAll=" + dropAll
                + " dropCount=" + dropCount + " remaining=" + remainingNow);
            return true;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem dropFakeFromHeld threw", t);
            return false;
        }
    }

    /**
     * Frame-break simulation on a dedicated server: un-mark a faked frame (the
     * fake no longer renders there) and pop a clientside ItemEntity at the frame
     * holding the fake, registering it for walkover pickup. The outgoing
     * attack packet is suppressed by the caller
     * ({@link java.lang.Object}).
     *
     * <p>Singleplayer: vanilla handles the frame break natively (the server
     * agrees, so the popped item is real + pickup-able).
     */
    public static boolean popFakeFromFrame(int entityId) {
        try {
            if (!isDedicatedServer()) {
                dbg("popFakeFromFrame", "SKIP not-dedicated");
                return false;
            }
            if (!fakeFrames.contains(entityId)) {
                dbg("popFakeFromFrame", "SKIP not-faked id=" + entityId);
                return false;
            }
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null) {
                dbg("popFakeFromFrame", "SKIP no-world");
                return false;
            }
            net.minecraft.entity.Entity e = c.world.getEntityById(entityId);
            if (e == null) {
                dbg("popFakeFromFrame", "SKIP entity-not-found id=" + entityId);
                return false;
            }
            if (!(e instanceof net.minecraft.entity.decoration.ItemFrameEntity frame)) {
                dbg("popFakeFromFrame", "SKIP not-a-frame id=" + entityId);
                return false;
            }
            fakeFrames.remove(entityId);
            // Vanilla frame-break drop: Entity.dropStack(world, stack)
            // -> new ItemEntity(world, frame.x, frame.y, frame.z, stack)
            // whose 5-arg constructor applies exactly:
            //   vx = random*0.2 - 0.1, vy = 0.2, vz = random*0.2 - 0.1
            // BUT spawning at the frame's exact position is wrong for
            // WALL-mounted frames: the entity sits only ~0.03 blocks out
            // from the wall (frame pos = block center + 0.46875 * facing),
            // so a 0.25-wide ItemEntity born there overlaps the wall and
            // can resolve half-inside it — the pop goes through the wall
            // or gets stuck. Push the spawn outward along the frame's
            // facing (up for floor frames, down for ceiling, into the room
            // for walls) and bias the toss slightly outward so it always
            // pops clear instead of clipping the block behind the frame.
            net.minecraft.util.math.Vec3d pos = e.getLerpedPos(1.0f);
            net.minecraft.util.math.Direction face = frame.getFacing();
            double ox = face.getOffsetX() * 0.18;
            double oy = face.getOffsetY() * 0.18;
            double oz = face.getOffsetZ() * 0.18;
            // The 0.18 position offset already clears the wall, so the pop
            // needs NO outward velocity — it should plop down right next to
            // the frame like a real break, not fling away from the wall.
            double rx = Math.random() * 0.12 - 0.06;
            double rz = Math.random() * 0.12 - 0.06;
            spawnFakeDrop(pos.x + ox, pos.y + oy, pos.z + oz, rx, 0.15, rz, -1);
            // Vanilla plays the frame's remove-item "pop" when an item is
            // punched out — mirror it locally.
            playLocalSound(net.minecraft.sound.SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM,
                pos.x, pos.y, pos.z, 1.0f, 1.0f);
            dbg("popFakeFromFrame", "OK id=" + entityId + " facing=" + face + " pop=(+" + ox + "," + oy + "," + oz + ")");
            return true;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem popFakeFromFrame threw", t);
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────
    //  DonutSMP spawner "look + function": place fakes on the
    //  ground (clientside blocks), mine them back, and the fake
    //  spawner's right-click storage GUI.
    // ────────────────────────────────────────────────────────────

    /** A clientside fake block placed on the ground (right-click while holding the fake). */
    public static final class PlacedFakeBlock {
        public final ItemStack stack;
        /** Number of copies the placement actually consumed — exactly what the mine-back returns. */
        public final int placedCount;
        /** True when the fake is a spawner item → renders as a spawner cage with the rotating mob. */
        public final boolean isSpawner;
        public final String mobId;
        /** The block state that was here BEFORE we overrode it — restored on break/clear. */
        public final net.minecraft.block.BlockState originalState;
        /** 0..1 hold-to-break progress (crack stages advance with it). */
        public float breakProgress;
        /**
         * Non-zero while the spawner is in its "denied break" window: the block
         * was visually broken (set to Air) but must REAPPEAR once this wall-clock
         * deadline passes. Used to mimic DonutSMP's no-silk-touch spawner deny —
         * the block vanishes for ~a second and pops back, with the red denial
         * text. 0 when not in that state.
         */
        public long restoreAfterMs;
        PlacedFakeBlock(ItemStack stack, int placedCount, boolean isSpawner, String mobId,
                        net.minecraft.block.BlockState originalState) {
            this.stack = stack;
            this.placedCount = placedCount;
            this.isSpawner = isSpawner;
            this.mobId = mobId;
            this.originalState = originalState;
            this.breakProgress = 0.0f;
            this.restoreAfterMs = 0L;
        }
    }

    /** True when {@code pos} currently has a client-placed fake block. */
    public static boolean isFakeBlock(net.minecraft.util.math.BlockPos pos) {
        return pos != null && fakeBlocks.containsKey(pos);
    }

    /** True when {@code pos} hosts a fake spawner (opens the storage GUI on right-click). */
    public static boolean isSpawnerFakeBlock(net.minecraft.util.math.BlockPos pos) {
        PlacedFakeBlock fb = pos == null ? null : fakeBlocks.get(pos);
        return fb != null && fb.isSpawner;
    }

    /** The placed-fake record at {@code pos}, or null. */
    public static PlacedFakeBlock getPlacedFakeBlock(net.minecraft.util.math.BlockPos pos) {
        return pos == null ? null : fakeBlocks.get(pos);
    }

    /** The block state the given fake stack renders as when placed (null = non-block fake). */
    private static net.minecraft.block.BlockState resolveFakePlaceState(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) return null;
            if (stack.getItem() instanceof net.minecraft.item.BlockItem bi) {
                net.minecraft.block.Block b = bi.getBlock();
                if (b != null && b != net.minecraft.block.Blocks.AIR) return b.getDefaultState();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Read the fake's spawner mob from its custom data (spawner fakes only); defaults to skeleton. */
    private static String getPlacedMobId(ItemStack stack) {
        try {
            NbtComponent nc = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (nc != null) {
                NbtCompound custom = nc.copyNbt();
                if (custom != null && custom.contains("spawner_mob")) {
                    java.util.Optional<String> mob = custom.getString("spawner_mob");
                    if (mob != null && mob.isPresent()) return mob.get();
                }
            }
        } catch (Throwable ignored) {}
        return "skeleton";
    }

    /** Map a spawner-custom mob id to an EntityType (unknown → skeleton). */
    private static net.minecraft.entity.EntityType<?> resolveSpawnerMobType(String mobId) {
        try {
            if (mobId != null && !mobId.isEmpty()) {
                net.minecraft.util.Identifier id =
                    net.minecraft.util.Identifier.tryParse("minecraft:" + mobId);
                if (id != null) {
                    java.util.Optional<net.minecraft.entity.EntityType<?>> opt =
                        net.minecraft.registry.Registries.ENTITY_TYPE.getOptionalValue(id);
                    if (opt.isPresent()) return opt.get();
                }
            }
        } catch (Throwable ignored) {}
        return net.minecraft.entity.EntityType.SKELETON;
    }

    /**
     * Make sure the spawner block entity exists at {@code pos} with the
     * requested mob (drives the rotating cage mob). Critical detail: setting
     * the SPAWNER block state already auto-creates a MobSpawnerBlockEntity,
     * and THAT one is what the renderer reads — so we resolve the existing
     * entity first and set the type on it, only creating our own when the
     * block itself didn't. The per-tick re-assert re-runs this, so the type is
     * continuously guaranteed even if something (a chunk reload / a re-place)
     * re-created the block entity without a mob.
     */
    private static void ensureSpawnerEntity(MinecraftClient c, net.minecraft.util.math.BlockPos pos,
                                            String mobId, net.minecraft.block.BlockState state) {
        try {
            net.minecraft.block.entity.BlockEntity existing = c.world.getBlockEntity(pos);
            net.minecraft.entity.EntityType<?> type = resolveSpawnerMobType(mobId);
            boolean existed = existing instanceof net.minecraft.block.entity.MobSpawnerBlockEntity;
            net.minecraft.block.entity.MobSpawnerBlockEntity spawner;
            if (existed) {
                spawner = (net.minecraft.block.entity.MobSpawnerBlockEntity) existing;
            } else {
                spawner = new net.minecraft.block.entity.MobSpawnerBlockEntity(pos, state);
                c.world.addBlockEntity(spawner);
            }
            // setEntity on the LIVE logic — this is what MobSpawnerLogic
            // getRenderedEntity()/the renderer's updateRenderState() read every
            // frame to draw the rotating mob in the cage. clientTick advances
            // the logic's rotation accumulator (vanilla drives it through the
            // block-entity ticker, which a runtime-created BE may not have).
            spawner.setEntityType(type, c.world.getRandom());
            try { spawner.getLogic().clientTick(c.world, pos); } catch (Throwable ignored) {}
            net.minecraft.entity.Entity rendered = null;
            try { rendered = spawner.getLogic().getRenderedEntity(c.world, pos); } catch (Throwable ignored) {}
            if (!existed || rendered == null) {
                dbg("spawner-entity", "pos=" + pos + " mob=" + mobId + " existed=" + existed
                    + " typeSet=" + (type != null) + " rendered=" + (rendered != null));
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem ensureSpawnerEntity threw", t);
        }
    }

    /**
     * Place the held fake on the ground at {@code pos} as a clientside block
     * and consume one item from the held stack (mirrors a real placement).
     * Spawner fakes render the cage + rotating mob; other block items render
     * their own block. The server has no record of the place (the outgoing
     * placement packet is suppressed by the caller), so the per-tick driver
     * re-asserts it.
     *
     * @param pos  the world position to place at (the clicked block face offset)
     * @param hand the hand the fake is held in (for consumption)
     * @return {@code true} when a block was placed
     */
    public static boolean placeFakeBlock(net.minecraft.util.math.BlockPos pos, Hand hand) {
        try {
            if (!active) return false;
            if (!isDedicatedServer()) return false;
            if (!canPlaceOnGround()) return false;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.world == null) return false;
            if (pos == null) return false;
            ItemStack fake = getFakeStack();
            if (fake.isEmpty()) return false;
            net.minecraft.block.BlockState want = resolveFakePlaceState(fake);
            if (want == null) return false;
            net.minecraft.block.BlockState prev = c.world.getBlockState(pos);
            boolean isSpawner = want.getBlock() == net.minecraft.block.Blocks.SPAWNER;
            String mobId = getPlacedMobId(fake);
            c.world.setBlockState(pos, want, 3);
            fakeBlocks.put(pos, new PlacedFakeBlock(fake.copy(), 1, isSpawner, mobId, prev));
            if (isSpawner) ensureSpawnerEntity(c, pos, mobId, want);
            // Consume one item from the held stack, mirroring a real placement.
            PlayerInventory inv = c.player.getInventory();
            int slot = hand == Hand.OFF_HAND ? PlayerInventory.OFF_HAND_SLOT : inv.getSelectedSlot();
            ItemStack held = slot >= 0 && slot < inv.size() ? inv.getStack(slot) : ItemStack.EMPTY;
            if (held != null && !held.isEmpty()) {
                if (held.getCount() <= 1) {
                    inv.setStack(slot, ItemStack.EMPTY);
                    if (ghostSlot == slot) { ghostSlot = -1; lastGhostSlot = -1; }
                    liveFakeCount = 0;
                } else {
                    inv.setStack(slot, held.copyWithCount(held.getCount() - 1));
                    liveFakeCount = held.getCount() - 1;
                }
            }
            playLocalSound(net.minecraft.sound.SoundEvents.BLOCK_STONE_PLACE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0f, 1.0f);
            dbg("place", "pos=" + pos + " block=" + want.getBlock().getName().getString()
                + " spawner=" + isSpawner + " mob=" + mobId);
            return true;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem placeFakeBlock threw", t);
            return false;
        }
    }

    /** Place a fake NON-block item on the ground as a resting clientside {@link ItemEntity} (right-click placement). */
    public static void placeFakeDropOnGround(net.minecraft.util.math.BlockPos faceOffset, int originSlot) {
        try {
            ItemStack fake = getFakeStack();
            if (fake.isEmpty()) return;
            int id = spawnFakeDropReturnId(faceOffset.getX() + 0.5, faceOffset.getY() + 0.6,
                faceOffset.getZ() + 0.5, 0.0, 0.08, 0.0, originSlot);
            if (id >= 0) placedDrops.add(id);
            playLocalSound(net.minecraft.sound.SoundEvents.BLOCK_STONE_PLACE,
                faceOffset.getX() + 0.5, faceOffset.getY() + 0.5, faceOffset.getZ() + 0.5, 0.5f, 1.0f);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem placeFakeDropOnGround threw", t);
        }
    }

    /** True when {@code entityId} is a placed resting drop (click-to-mine-back, not walkover). */
    public static boolean isPlacedDrop(int entityId) {
        return placedDrops.contains(entityId);
    }

    /** Click-mine-back a placed non-block fake drop: pop it into the inventory and discard the entity. */
    public static boolean mineBackPlacedDrop(int entityId) {
        try {
            if (!active) return false;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.world == null) return false;
            net.minecraft.entity.Entity e = c.world.getEntityById(entityId);
            if (!(e instanceof net.minecraft.entity.ItemEntity ie)) return false;
            ItemStack dropStack = ie.getStack();
            placedDrops.remove(entityId);
            unregisterDropped(entityId);
            ie.discard();
            if (dropStack == null || dropStack.isEmpty()) return false;
            if (grantFakeStack(dropStack.copy())) {
                dbg("mine-back-drop", "id=" + entityId);
                return true;
            }
            return false;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem mineBackPlacedDrop threw", t);
            return false;
        }
    }

    /**
     * Per-tick fake-block maintenance: re-assert every placed fake block (a
     * server resync / chunk reload would otherwise erase it and the spawner
     * block entity) and drive the hold-to-break progress + crack overlay when
     * the player holds left-click on a fake block. Called from onTickAlways.
     */
    private static void tickFakeBlockWorld(MinecraftClient c) {
        try {
            if (!active) return;
            if (!isDedicatedServer()) return;
            if (c == null || c.player == null || c.world == null) return;

            // 1) Re-assert placed fake blocks + their spawner block entities.
            if (!fakeBlocks.isEmpty()) {
                long nowMs = System.currentTimeMillis();
                for (java.util.Map.Entry<net.minecraft.util.math.BlockPos, PlacedFakeBlock> e
                         : new java.util.ArrayList<>(fakeBlocks.entrySet())) {
                    net.minecraft.util.math.BlockPos pos = e.getKey();
                    PlacedFakeBlock fb = e.getValue();
                    if (fb == null) { fakeBlocks.remove(pos); continue; }
                    net.minecraft.block.BlockState want = resolveFakePlaceState(fb.stack);
                    if (want == null) { fakeBlocks.remove(pos); continue; }
                    // Silk-touch DENIED break window: the spawner was visually
                    // broken (set to Air) and must stay "gone" until its restore
                    // deadline passes, then it reappears — DonutSMP-style "it
                    // disappears for a second then comes back".
                    if (fb.restoreAfterMs > 0) {
                        if (nowMs < fb.restoreAfterMs) continue;   // still "broken"
                        fb.restoreAfterMs = 0L;                    // time's up → reappear
                    }
                    net.minecraft.block.BlockState cur = c.world.getBlockState(pos);
                    if (cur.getBlock() != want.getBlock()) {
                        c.world.setBlockState(pos, want, 3);
                    }
                    if (fb.isSpawner) ensureSpawnerEntity(c, pos, fb.mobId, want);
                }
            }

            // 2) Hold-to-break: attack key held + crosshair on a fake block.
            boolean atk = false;
            try { atk = c.options.attackKey.isPressed(); } catch (Throwable ignored) {}
            net.minecraft.util.math.BlockPos minePos = null;
            if (atk && c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr
                    && isFakeBlock(bhr.getBlockPos())) {
                minePos = bhr.getBlockPos();
            }
            if (minePos == null) {
                if (mineActivePos != null) {
                    c.world.setBlockBreakingInfo(c.player.getId(), mineActivePos, -1);
                    PlacedFakeBlock old = fakeBlocks.get(mineActivePos);
                    if (old != null) old.breakProgress = 0.0f;
                    mineActivePos = null;
                }
                return;
            }
            PlacedFakeBlock fb = fakeBlocks.get(minePos);
            if (fb == null) { mineActivePos = null; return; }

            // While the spawner is in its "denied break" vanish window the block
            // is invisible (Air) — it can't be re-mined until it reappears.
            if (fb.restoreAfterMs > 0) {
                if (mineActivePos != null) {
                    c.world.setBlockBreakingInfo(c.player.getId(), mineActivePos, -1);
                    mineActivePos = null;
                }
                fb.breakProgress = 0.0f;
                return;
            }

            if (mineActivePos == null || !mineActivePos.equals(minePos)) {
                mineActivePos = minePos;
                fb.breakProgress = 0.0f;
            }
            fb.breakProgress += 0.16f;   // ~7 ticks of holding to break
            if (fb.breakProgress > 1.0f) fb.breakProgress = 1.0f;
            int stage = Math.min(9, (int) (fb.breakProgress * 10));
            c.world.setBlockBreakingInfo(c.player.getId(), minePos, stage);
            try { c.player.swingHand(Hand.MAIN_HAND); } catch (Throwable ignored) {}
            dbg("mine-fake", "pos=" + minePos + " prog=" + String.format("%.2f", fb.breakProgress)
                + " stage=" + stage);
            if (fb.breakProgress >= 1.0f) {
                c.world.setBlockBreakingInfo(c.player.getId(), minePos, -1);
                mineActivePos = null;
                fb.breakProgress = 0.0f;
                // Silk-touch gate fires on COMPLETION, DonutSMP-style: a fake
                // spawner mined without Silk Touch "breaks" (particles + it
                // vanishes) but yields no item; the red denial text appears and
                // the block pops back ~1 second later.
                if (fb.isSpawner && !hasSilkTouchMainHand(c)) {
                    denySpawnerMine(minePos, fb);
                } else {
                    removeFakeBlock(minePos, true);
                }
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem tickFakeBlockWorld threw", t);
        }
    }

    /**
     * Deny mined fake spawner without Silk Touch after a FULL hold-to-break:
     * the block visually breaks (stone particles + sound, then set to Air so it
     * vanishes for ~a second), the red denial text is shown, and the fake is
     * scheduled to REAPPEAR on the next re-assert sweep once its
     * {@link PlacedFakeBlock#restoreAfterMs} deadline passes — mirroring
     * DonutSMP's "it disappears for a second then reappears" plugin deny. No
     * item is dropped. The fake stays registered during the window; only its
     * rendered block is gone.
     */
    private static void denySpawnerMine(net.minecraft.util.math.BlockPos pos, PlacedFakeBlock fb) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.world == null || pos == null || fb == null) return;
        long now = System.currentTimeMillis();
        net.minecraft.block.BlockState breaking = c.world.getBlockState(pos);
        c.world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
        try { c.world.addBlockBreakParticles(pos, breaking); } catch (Throwable ignored) {}
        // A real spawner uses BlockSoundGroup.SPAWNER, so its break sound is
        // BLOCK_SPAWNER_BREAK — not the stone one a normal block makes.
        // Match it for the deny pop so it sounds like the genuine break.
        playLocalSound(net.minecraft.sound.SoundEvents.BLOCK_SPAWNER_BREAK,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0f, 0.9f);
        fb.restoreAfterMs = now + 150L;   // quick blink vanish, then reappear
        fb.breakProgress = 0.0f;
        if (now - lastSpawnerDenyMs > 1500L) {
            lastSpawnerDenyMs = now;
            try {
                c.inGameHud.setOverlayMessage(
                    net.minecraft.text.Text.literal("You need a tool with Silk Touch to mine this spawner.")
                        .formatted(net.minecraft.util.Formatting.RED),
                    true);
            } catch (Throwable ignored) {}
            dbg("mine-fake", "DENIED no-silk-touch pos=" + pos + " vanished, restores after 1s");
        }
    }

    /**
     * True when the player's MAIN HAND holds a tool enchanted with Silk Touch.
     * Used to gate mining of fake spawner blocks (DonutSMP's
     * {@code require_silk_touch} behaviour). Returns {@code false} on any
     * registry hiccup so an unreadable state never accidentally unlocks the
     * gate.
     */
    private static boolean hasSilkTouchMainHand(MinecraftClient c) {
        try {
            if (c == null || c.player == null || c.world == null) return false;
            net.minecraft.item.ItemStack tool = c.player.getMainHandStack();
            if (tool == null || tool.isEmpty()) return false;
            net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> reg =
                c.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> silk =
                reg.getOrThrow(net.minecraft.enchantment.Enchantments.SILK_TOUCH);
            return net.minecraft.enchantment.EnchantmentHelper.getLevel(silk, tool) > 0;
        } catch (Throwable t) {
            return false; // registry not ready — assume no silk touch
        }
    }

    /**
     * Remove a placed fake block: restore the original block state, clear the
     * crack overlay, and — when {@code returnItem} — drop the item onto the
     * ground exactly where it was broken, like vanilla block breaking. The
     * dropped entity is walkover-pickable, so walking over it re-absorbs it
     * (merging into the existing fake stack).
     */
    public static void removeFakeBlock(net.minecraft.util.math.BlockPos pos, boolean returnItem) {
        try {
            PlacedFakeBlock fb = fakeBlocks.remove(pos);
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null || pos == null) return;
            net.minecraft.block.BlockState original = (fb != null && fb.originalState != null)
                ? fb.originalState : net.minecraft.block.Blocks.AIR.getDefaultState();
            net.minecraft.block.BlockState cur = c.world.getBlockState(pos);
            boolean changed = false;
            if (cur.getBlock() == net.minecraft.block.Blocks.SPAWNER
                    || (fb != null && cur.getBlock() != original.getBlock())) {
                c.world.setBlockState(pos, original, 3);
                changed = true;
            }
            if (changed) {
                // A removed spawner (silk-touch break, or the restored fake
                // was a spawner) sounds like a real spawner break; anything
                // else uses the stone break sound.
                boolean wasSpawner = cur.getBlock() == net.minecraft.block.Blocks.SPAWNER
                    || (fb != null && fb.isSpawner);
                playLocalSound(wasSpawner
                        ? net.minecraft.sound.SoundEvents.BLOCK_SPAWNER_BREAK
                        : net.minecraft.sound.SoundEvents.BLOCK_STONE_BREAK,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0f, 0.9f);
            }
            if (c.player != null) c.world.setBlockBreakingInfo(c.player.getId(), pos, -1);
            if (returnItem && fb != null) {
                // Always DROP the consumed copy onto the ground at the broken
                // block — never grant it straight back into the inventory stack.
                // The entity is registered for walkover pickup, which merges it
                // back onto the existing fake stack (63 → 64) when walked over.
                int dropCount = Math.max(1, fb.placedCount);
                spawnFakeDropReturnId(pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                    0.0, 0.15, 0.0, -1, dropCount);
                dbg("removeFakeBlock", "dropped=" + dropCount + " at " + pos);
            }
            dbg("removeFakeBlock", "pos=" + pos + " returned=" + returnItem + " changed=" + changed);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem removeFakeBlock threw", t);
        }
    }

    /** Wipe every placed fake block (restoring the prior block states) + any placed drops. */
    public static void clearFakeBlocks() {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c != null && c.world != null) {
                for (net.minecraft.util.math.BlockPos pos : new java.util.ArrayList<>(fakeBlocks.keySet())) {
                    PlacedFakeBlock fb = fakeBlocks.get(pos);
                    net.minecraft.block.BlockState original = (fb != null && fb.originalState != null)
                        ? fb.originalState : net.minecraft.block.Blocks.AIR.getDefaultState();
                    net.minecraft.block.BlockState cur = c.world.getBlockState(pos);
                    if (cur.getBlock() != original.getBlock()) {
                        c.world.setBlockState(pos, original, 3);
                    }
                    if (c.player != null) c.world.setBlockBreakingInfo(c.player.getId(), pos, -1);
                }
            }
            fakeBlocks.clear();
            mineActivePos = null;
            placedDrops.clear();
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem clearFakeBlocks threw", t);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Fake stack granting (mine-back / item return) — the primary
    //  fake is merged back onto the live inventory stack, never a
    //  new slot.
    // ────────────────────────────────────────────────────────────

    /**
     * Grant a fake stack (mine-back / item return) into the inventory. MERGES
     * into an existing stack of the same identity with room first — e.g. the
     * one copy returned by mining a placed fake merges onto the 63 already in
     * hand (63 → 64), never a brand-new hotbar slot. Falls back to a fresh
     * empty slot when no merge target exists.
     */
    public static boolean grantFakeStack(ItemStack stack) {
        try {
            if (!active || stack == null || stack.isEmpty()) return false;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            PlayerInventory inv = c.player.getInventory();
            // Merge-first: find an inventory slot of the same fake identity that
            // still has room.
            for (int i = 0; i < inv.size(); i++) {
                ItemStack cur = inv.getStack(i);
                if (isFakeIdentity(cur) && cur.getCount() < cur.getMaxCount()) {
                    int merged = Math.min(cur.getMaxCount(), cur.getCount() + stack.getCount());
                    ItemStack mergedStack = stack.copy();
                    mergedStack.setCount(merged);
                    inv.setStack(i, mergedStack);
                    if (ghostSlot < 0) { ghostSlot = i; lastGhostSlot = i; }
                    playLocalSound(net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP,
                        c.player.getX(), c.player.getY(), c.player.getZ(), 0.2f,
                        (c.world.getRandom().nextFloat() - c.world.getRandom().nextFloat()) * 0.7F + 1.0F);
                    dbg("grant", "MERGE slot=" + i + " x" + merged
                        + " item=" + stack.getItem().getName().getString());
                    return true;
                }
            }
            int slot = firstEmptyHotbar(inv);
            if (slot < 0) slot = firstEmpty(inv);
            if (slot < 0) return false;
            inv.setStack(slot, stack.copy());
            extraFakeStacks.put(slot, stack.copy());
            playLocalSound(net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP,
                c.player.getX(), c.player.getY(), c.player.getZ(), 0.2f,
                (c.world.getRandom().nextFloat() - c.world.getRandom().nextFloat()) * 0.7F + 1.0F);
            dbg("grant", "slot=" + slot + " item=" + stack.getItem().getName().getString()
                + " x" + stack.getCount() + " total=" + extraFakeStacks.size());
            return true;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem grantFakeStack threw", t);
            return false;
        }
    }

    /**
     * Upgrade the PLACABLE_UNSUPPORTED right-click-air path: block fakes now
     * place as a mc fake block in front of the player instead of warning.
     */
    public static boolean placeFakeBlockInFront(Hand hand) {
        try {
            if (!canPlaceOnGround()) return false;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.world == null) return false;
            ItemStack fake = getFakeStack();
            if (fake.isEmpty()) return false;
            net.minecraft.block.BlockState want = resolveFakePlaceState(fake);
            if (want == null) return false;   // non-block fakes: not placeable this way
            net.minecraft.util.math.BlockPos front =
                c.player.getBlockPos().offset(c.player.getHorizontalFacing());
            net.minecraft.block.BlockState at = c.world.getBlockState(front);
            if (at.getBlock() != net.minecraft.block.Blocks.AIR
                    && at.getBlock() != net.minecraft.block.Blocks.WATER
                    && at.getBlock() != net.minecraft.block.Blocks.LAVA) {
                front = front.up();
            }
            return placeFakeBlock(front, hand);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem placeFakeBlockInFront threw", t);
            return false;
        }
    }

    /**
     * Like {@link #spawnFakeDrop} but RETURNS the spawned entity id so the
     * caller can additionally mark it as a placed resting drop. {@link
     * #spawnFakeDrop} delegates here.
     *
     * @return the spawned entity id, or {@code -1} when nothing was spawned
     */
    public static int spawnFakeDropReturnId(double x, double y, double z,
                                            double vx, double vy, double vz,
                                            int originSlot) {
        return spawnFakeDropReturnId(x, y, z, vx, vy, vz, originSlot, 1);
    }

    /**
     * Like {@link #spawnFakeDropReturnId(double,double,double,double,double,double,int)}
     * but lets the caller set the dropped entity's stack count. Ordinary drops
     * pass {@code 1} (a single item visual); Ctrl+Q passes the full stack count
     * so the whole fake lands in one merged entity.
     */
    public static int spawnFakeDropReturnId(double x, double y, double z,
                                            double vx, double vy, double vz,
                                            int originSlot, int dropCount) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null) return -1;
            ItemStack fake = getFakeStack();
            if (fake.isEmpty()) return -1;
            if (dropCount < 1) dropCount = 1;
            net.minecraft.entity.ItemEntity ie =
                new net.minecraft.entity.ItemEntity(c.world, x, y, z, fake.copyWithCount(dropCount), 0, 0, 0);
            try {
                ie.setVelocity(new net.minecraft.util.math.Vec3d(vx, vy, vz));
            } catch (Throwable ignored) {}
            try { ie.setPickupDelay(20); } catch (Throwable ignored) {}
            c.world.addEntity(ie);
            registerDropped(ie.getId());
            lastGhostSlot = originSlot;
            return ie.getId();
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem spawnFakeDropReturnId threw", t);
            return -1;
        }
    }

    /**
     * Drop the fake OUT of an open container slot (or the cursor) into the
     * world as a clientside {@link ItemEntity}, mirroring vanilla's
     * {@code SlotActionType.THROW} (Q-while-hovering-a-slot outside the player
     * inv, or click-release outside the GUI to throw the cursor). Called by
     * {@link java.lang.Object} after it cancels the
     * outgoing THROW click. The server's slot is left untouched (the fake never
     * existed there), so no resync is needed from us.
     *
     * @param syncId  the screen handler whose slot is being thrown
     * @param slot    the slot index in that handler, or {@code -999} for "drop
     *                the cursor outside the window"
     * @param dropAll true for full-stack drop (Q-while-sneaking) — for a fake
     *                there is no count, so this is cosmetic
     * @return {@code true} when we successfully popped a fake into the world
     */
    public static boolean dropFakeFromScreenSlot(int syncId, int slot, boolean dropAll) {
        try {
            if (!isDedicatedServer()) {
                dbg("dropFakeFromScreenSlot", "SKIP not-dedicated");
                return false;
            }
            if (fakeStack == null) {
                dbg("dropFakeFromScreenSlot", "SKIP fakeStack=null");
                return false;
            }
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.world == null) {
                dbg("dropFakeFromScreenSlot", "SKIP no-world/player");
                return false;
            }

            if (slot == -999) {
                // Throwing the cursor (fake on cursor) outside the window.
                if (!cursorIsGhost) {
                    dbg("dropFakeFromScreenSlot", "FAIL cursor-not-ghost for -999");
                    return false;
                }
                // Vanilla yaw-only drop physics, gentler toss — see dropFakeFromHeld.
                float yawRad = c.player.getYaw() * ((float) Math.PI / 180f);
                double fx = -Math.sin(yawRad) * 0.2;
                double fz = Math.cos(yawRad) * 0.2;
                net.minecraft.util.math.Vec3d eye = c.player.getEyePos();
                spawnFakeDrop(eye.x + fx, eye.y - 0.3, eye.z + fz,
                              fx, 0.05, fz, lastGhostSlot);
                // Clear the cursor locally so the drag visually completes.
                try { c.player.currentScreenHandler.setCursorStack(ItemStack.EMPTY); } catch (Throwable ignored) {}
                cursorIsGhost = false;
                dbg("dropFakeFromScreenSlot", "OK -999 cursor-drop");
                return true;
            }

            // Throwing a specific slot. Verify that slot actually holds the fake.
            if (slot < 0 || c.player.currentScreenHandler == null) {
                dbg("dropFakeFromScreenSlot", "FAIL bad-slot or no-handler slot=" + slot);
                return false;
            }
            net.minecraft.screen.ScreenHandler handler = c.player.currentScreenHandler;
            if (handler.syncId != syncId || slot >= handler.slots.size()) {
                dbg("dropFakeFromScreenSlot", "FAIL syncId/slot mismatch syncId=" + syncId + " slot=" + slot + " handler.syncId=" + (handler.syncId) + " handlerSize=" + handler.slots.size());
                return false;
            }
            net.minecraft.screen.slot.Slot targetSlot = handler.slots.get(slot);
            ItemStack cur = targetSlot.getStack();
            if (cur == null || !stackEquals(cur, fakeStack)) {
                dbg("dropFakeFromScreenSlot", "FAIL slot-does-not-hold-fake slot=" + slot + " stack=" + (cur == null ? "null" : cur.getItem().getName().getString()));
                return false;
            }

            // Determine where this slot is so we can update our tracking +
            // pick an origin to record for walkover pickup.
            int origin = lastGhostSlot;
            int playerSyncId;
            try { playerSyncId = c.player.playerScreenHandler.syncId; }
            catch (Throwable ignored) { playerSyncId = -1; }

            // Clear the slot locally + update tracking so reassertGhost doesn't
            // immediately re-spawn the fake back into it.
            targetSlot.setStackNoCallbacks(ItemStack.EMPTY);
            if (syncId == playerSyncId && slot == ghostSlot) {
                ghostSlot = -1;
            } else if (isContainerFakeSlot(syncId, slot)) {
                // Only unlink the ONE slot that was thrown — other fake copies
                // may still sit in the chest and must keep their protection.
                removeContainerFakeSlots(syncId, java.util.Collections.singletonList(slot));
                // The fake is now visually in-flight (cursor → world) — temporarily
                // park lastGhostSlot so walkover picks it up to where the fake
                // was in the player inv.
                origin = lastGhostSlot;
            } else if (targetSlot.inventory instanceof PlayerInventory
                       && targetSlot.inventory == c.player.getInventory()) {
                // Thrown from a fake in the player grid, addressed under the
                // open chest window (chest syncId + chest slot offset) — unlink
                // that exact inventory index (primary or clone stack) so the
                // per-tick heal doesn't resurrect it.
                int invIdx = targetSlot.getIndex();
                if (invIdx == ghostSlot) {
                    ghostSlot = -1;
                } else {
                    extraGhostSlots.remove(invIdx);
                }
                origin = invIdx;
            }

            net.minecraft.util.math.Vec3d look = c.player.getRotationVec(1.0f);
            net.minecraft.util.math.Vec3d pos = c.player.getEyePos();
            spawnFakeDrop(pos.x + look.x * 0.4, pos.y + look.y * 0.4 - 0.2, pos.z + look.z * 0.4,
                          look.x * 0.25, 0.2, look.z * 0.25, origin);
            dbg("dropFakeFromScreenSlot", "OK syncId=" + syncId + " slot=" + slot + " origin=" + origin);
            return true;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem dropFakeFromScreenSlot threw", t);
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════
    //  DonutSMP use-simulation — cosmetic parity for "use item".
    //
    //  On a dedicated server the server has no record of the fake, so the
    //  vanilla use hand-shake (interactItem → PlayerInteractItemC2SPacket →
    //  server processes use → server sends back effects) never produces the
    //  cosmetic. We serve the cosmetic entirely clientside:
    //
    //    * HELD_USE (eat/drink/shield/spyglass) — the pose + (for food) the
    //      particle/sound cadence come FREE from vanilla once we call
    //      player.setCurrentHand(): the renderer reads isUsingItem() +
    //      getActiveItem(), and ItemStack.usageTick() drives the fake's
    //      ConsumableComponent.spawnParticlesAndPlaySound. The only thing
    //      vanilla does NOT do on the mc is END the pose when the eat
    //      timer hits 0 (the consume path is world.isClient-gated) — so the
    //      LivingEntityUseMixin catches timer==0 and finishes it for us.
    //    * HELD_BOW/CROSSBOW/SPEAR — the draw pose is free (setCurrentHand);
    //      on release we spawn a clientside fake projectile (the server would
    //      have spawned it, but doesn't know we drew) + play the shoot sound.
    //    * INSTANT_THROW — snowball/egg/pearl/xp-bottle/potions/wind-charge:
    //      spawn a fake projectile + throw sound + swing on right-click.
    //    * INSTANT_HORN — goat horn: play the horn sound on right-click.
    //
    //  Server-authoritative OUTCOMES (damage, hunger, potion, teleport,
    //  XP, real elytra flight, totem revival, block/fluid placement that
    //  STAYS) are impossible from the mc alone and stay out of scope per
    //  the agreed "cosmetic parity only" choice.
    // ════════════════════════════════════════════════════════════

    /**
     * The vanilla "use" family of the spawned fake. Drives which cosmetic we
     * serve clientside on a dedicated server.
     */
    public enum SimKind {
        /** Plain item with no right-click behaviour (sword/pickaxe/etc.) — nothing to simulate. */
        NONE,
        /** Food / golden apple / chorus fruit — held eat pose + particle/sound cadence + finish. */
        HELD_EAT,
        /** Drinkable potions / milk bucket / honey bottle — held drink pose + finish. */
        HELD_DRINK,
        /** Shield — block pose from setCurrentHand (renderer reads getUseAction()==BLOCK). */
        HELD_SHIELD,
        /** Bow — draw pose + on release spawn a fake arrow + shoot sound. */
        HELD_BOW,
        /** Crossbow — draw pose + on release spawn a fake bolt + shoot sound. */
        HELD_CROSSBOW,
        /** Trident — draw pose (UseAction SPEAR) + on release spawn a fake trident + throw sound. */
        HELD_SPEAR,
        /** Spyglass — zoom + scope overlay (GameRenderer reads isUsingItem + active item). */
        HELD_SPYGLASS,
        /** Throwable (snowball/egg/pearl/xp-bottle/splash+lingering potion/wind-charge/fishing-rod). */
        INSTANT_THROW,
        /** Goat horn — play the horn toot sound on right-click. */
        INSTANT_HORN,
        /** Block-items/buckets/flint-and-steel/hoe/axe/shovel/spawn-egg/minecart/boat —
         *  server-authoritative; the cosmetic flicker is the accepted outcome. */
        PLACABLE_UNSUPPORTED
    }

    /** The use kind currently in progress on a held use (NONE when idle). */
    private static volatile SimKind currentSimKind = SimKind.NONE;
    /** The hand a held use is active on, or {@code null} when idle. */
    private static volatile Hand currentSimHand = null;
    /** Tracked clientside fishing bobber entity ID (so a re-cast discards the old line). */
    private static volatile int fakeBobberId = -1;

    /** One-shot gate so we log the dedicated-server connection once, not per-tick. */
    private static volatile boolean wasDedicatedLogged = false;

    /** True when the fake is currently mid held-use on the local player. */
    public static boolean isUseSimActive() { return currentSimKind != SimKind.NONE; }
    public static SimKind getCurrentSimKind() { return currentSimKind; }
    public static Hand getCurrentSimHand() { return currentSimHand; }

    /** Reset all use-sim state (disable / ;clear / disconnect / mid-use). */
    private static void resetUseSim() {
        currentSimKind = SimKind.NONE;
        currentSimHand = null;
        fakeBobberId = -1;
    }

    /**
     * Classify the fake's vanilla "use" family. Primary signal is
     * {@link ItemStack#getUseAction()} (1.21.11 resolves it via the fake's
     * {@link ConsumableComponent} for food/drink, or the item type's static
     * {@code getUseAction} for bow/shield/spyglass/crossbow/trident/horn).
     * The {@link UseAction#NONE} bucket falls back to an item-type check for
     * instant-use throwables and server-authoritative placeables.
     */
    public static SimKind classifyUse(Hand hand) {
        try {
            ItemStack fake = getFakeStack();
            if (fake.isEmpty()) return SimKind.NONE;
            UseAction action = fake.getUseAction();
            if (action != null) {
                switch (action) {
                    case EAT:       return SimKind.HELD_EAT;
                    case DRINK:     return SimKind.HELD_DRINK;
                    case BLOCK:     return SimKind.HELD_SHIELD;
                    case BOW:       return SimKind.HELD_BOW;
                    case CROSSBOW:  return SimKind.HELD_CROSSBOW;
                    case SPEAR:      // fallthrough
                    case TRIDENT:   return SimKind.HELD_SPEAR;
                    case SPYGLASS:   return SimKind.HELD_SPYGLASS;
                    case TOOT_HORN: return SimKind.INSTANT_HORN;
                    default:        break; // NONE / BRUSH / BUNDLE → item-type check
                }
            }
            Item item = fake.getItem();
            if (item == null) return SimKind.NONE;
            // Instant-use throwables (UseAction NONE — thrown on right-click).
            if (item instanceof net.minecraft.item.FishingRodItem) return SimKind.INSTANT_THROW;
            if (item instanceof net.minecraft.item.SnowballItem
                || item instanceof net.minecraft.item.EggItem
                || item instanceof net.minecraft.item.EnderPearlItem
                || item instanceof net.minecraft.item.ExperienceBottleItem
                || item instanceof net.minecraft.item.SplashPotionItem
                || item instanceof net.minecraft.item.LingeringPotionItem
                || item instanceof net.minecraft.item.WindChargeItem) {
                return SimKind.INSTANT_THROW;
            }
            // Server-authoritative placeables / entity-summon — cosmetic flicker only.
            if (item instanceof net.minecraft.item.BlockItem
                || item instanceof net.minecraft.item.BucketItem
                || item instanceof net.minecraft.item.FlintAndSteelItem
                || item instanceof net.minecraft.item.HoeItem
                || item instanceof net.minecraft.item.AxeItem
                || item instanceof net.minecraft.item.ShovelItem
                || item instanceof net.minecraft.item.SpawnEggItem
                || item instanceof net.minecraft.item.MinecartItem
                || item instanceof net.minecraft.item.BoatItem) {
                return SimKind.PLACABLE_UNSUPPORTED;
            }
            return SimKind.NONE;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem classifyUse threw", t);
            return SimKind.NONE;
        }
    }

    /**
     * Determine whether the fake in {@code hand} is the one being "used".
     * The fake may be in either hand (off-hand shield, etc.), so compare the
     * hand's stack to the fake AND to the recorded currentSimHand.
     */
    private static boolean isFakeInHand(Hand hand) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            ItemStack held = c.player.getStackInHand(hand);
            // Count-insensitive identity: after a frame placement consumes one
            // copy, the held remainder's COUNT no longer matches the fakeStack
            // template, so the USE gate must ignore count like the frame gate.
            return isFakeIdentity(held);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Entry point from {@link java.lang.Object}:
     * called at HEAD of {@code interactItem(player, hand)} when the fake is the
     * held item on a dedicated server. Classifies the use and either starts a
     * held pose locally, fires an instant use, or lets vanilla fall through.
     *
     * @return {@code true} to CANCEL {@code interactItem} (no outgoing use
     *         packet, no vanilla {@code stack.use}); {@code false} to let
     *         vanilla proceed untouched.
     */
    public static boolean onInteractItemStart(Hand hand) {
        try {
            if (!active) return false;
            if (!isDedicatedServer()) return false;
            if (!isFakeInHand(hand)) return false;
            SimKind kind = classifyUse(hand);
            switch (kind) {
                case HELD_EAT: case HELD_DRINK: case HELD_SHIELD:
                case HELD_BOW: case HELD_CROSSBOW: case HELD_SPEAR:
                case HELD_SPYGLASS:
                    return startHeldUse(hand, kind);
                case INSTANT_THROW:
                    simulateInstantThrow(hand);
                    return true;
                case INSTANT_HORN:
                    simulateInstantHorn(hand);
                    return true;
                case PLACABLE_UNSUPPORTED:
                    // Block fake + right-click-AIR: place the fake as a mc
                    // block in front of the player (feet-level adjacent).
                    if (placeFakeBlockInFront(hand)) return true;
                    return false;  // let vanilla no-op on right-click-air
                default:
                    return false;  // NON_USE — vanilla no-op
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem onInteractItemStart threw", t);
            return false;
        }
    }

    /**
     * Start a held-use pose locally via {@link LivingEntity#setCurrentHand}.
     * The renderer + ItemStack.usageTick take it from here (eat particles,
     * draw animation, block pose, scope zoom). We must NOT let vanilla
     * interactItem send the use packet — the returned {@code true} cancels it,
     * and ClientConnectionMixin double-guards the outgoing packet too.
     */
    private static boolean startHeldUse(Hand hand, SimKind kind) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null) return false;
            if (c.player.isUsingItem()) {
                // Already mid-use (e.g. swapped hands) — don't restart.
                return true;
            }
            c.player.setCurrentHand(hand);
            currentSimKind = kind;
            currentSimHand = hand;
            return true;  // cancel interactItem
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem startHeldUse threw", t);
            return false;
        }
    }

    /**
     * Spawn a clientside fake projectile for an instant throwable + play the
     * throw sound + swing the hand. The projectile entity is added to the
     * ClientWorld only (no spawn packet → server unaware), so it travels +
     * ticks + plays impact sounds purely locally with NO damage/teleport/XP.
     */
    private static void simulateInstantThrow(Hand hand) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.world == null) return;
            ItemStack fake = getFakeStack();
            if (fake.isEmpty()) return;
            Item item = fake.getItem();

            Vec3d look = c.player.getRotationVec(1.0f);
            Vec3d eye = c.player.getEyePos();

            // Fishing rod: spawn a fake bobber attached to the player; discard
            // a previously-cast fake line first so casts don't stack.
            if (item instanceof net.minecraft.item.FishingRodItem) {
                discardFakeBobber();
                net.minecraft.entity.projectile.FishingBobberEntity bobber =
                    new net.minecraft.entity.projectile.FishingBobberEntity(
                        (net.minecraft.entity.player.PlayerEntity) c.player, c.world, 0, 0);
                try { bobber.setVelocity(look.x * 0.8, look.y * 0.8, look.z * 0.8); } catch (Throwable ignored) {}
                c.world.addEntity(bobber);  // addEntity = clientside commit path (NOT spawnEntity)
                fakeBobberId = bobber.getId();
                playThrowSound(item);
                c.player.swingHand(hand);
                return;
            }

            // Generic thrown projectile: pick the matching entity type by item.
            net.minecraft.entity.projectile.ProjectileEntity proj = buildThrowable(item, c.player, fake);
            if (proj == null) return;
            proj.setVelocity(look.x * 1.5, look.y * 1.5, look.z * 1.5);
            try { proj.setOwner(c.player); } catch (Throwable ignored) {}
            try { proj.setPosition(eye.x, eye.y, eye.z); } catch (Throwable ignored) {}
            c.world.addEntity(proj);  // addEntity = clientside commit path (NOT spawnEntity)
            playThrowSound(item);
            c.player.swingHand(hand);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem simulateInstantThrow threw", t);
        }
    }

    /** Construct the matching throwable entity for a fake item, or {@code null}. */
    private static net.minecraft.entity.projectile.ProjectileEntity buildThrowable(
            Item item, net.minecraft.entity.player.PlayerEntity shooter, ItemStack fakeStack) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null || !(shooter instanceof LivingEntity)) return null;
            LivingEntity living = (LivingEntity) shooter;
            ItemStack copy = fakeStack.copy();
            if (item instanceof net.minecraft.item.SnowballItem)
                return new net.minecraft.entity.projectile.thrown.SnowballEntity(c.world, living, copy);
            if (item instanceof net.minecraft.item.EggItem)
                return new net.minecraft.entity.projectile.thrown.EggEntity(c.world, living, copy);
            if (item instanceof net.minecraft.item.EnderPearlItem)
                return new net.minecraft.entity.projectile.thrown.EnderPearlEntity(c.world, living, copy);
            if (item instanceof net.minecraft.item.ExperienceBottleItem)
                return new net.minecraft.entity.projectile.thrown.ExperienceBottleEntity(c.world, living, copy);
            if (item instanceof net.minecraft.item.SplashPotionItem)
                return new net.minecraft.entity.projectile.thrown.SplashPotionEntity(c.world, living, copy);
            if (item instanceof net.minecraft.item.LingeringPotionItem)
                return new net.minecraft.entity.projectile.thrown.LingeringPotionEntity(c.world, living, copy);
            if (item instanceof net.minecraft.item.WindChargeItem && shooter instanceof net.minecraft.entity.player.PlayerEntity)
                return new net.minecraft.entity.projectile.WindChargeEntity(
                    (net.minecraft.entity.player.PlayerEntity) shooter, c.world,
                    shooter.getX(), shooter.getEyeY() - 0.2, shooter.getZ());
            return null;
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem buildThrowable threw", t);
            return null;
        }
    }

    /** Play the throw sound for a throwable item (local only via playSoundClient). */
    private static void playThrowSound(Item item) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null) return;
            SoundEvent s = null;
            if (item == Items.SNOWBALL) s = SoundEvents.ENTITY_SNOWBALL_THROW;
            else if (item == Items.EGG) s = SoundEvents.ENTITY_EGG_THROW;
            else if (item == Items.ENDER_PEARL) s = SoundEvents.ENTITY_ENDER_PEARL_THROW;
            else if (item == Items.EXPERIENCE_BOTTLE) s = SoundEvents.ENTITY_EXPERIENCE_BOTTLE_THROW;
            else if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) s = SoundEvents.ENTITY_SPLASH_POTION_THROW;
            else if (item == Items.WIND_CHARGE) s = SoundEvents.ENTITY_WIND_CHARGE_THROW;
            else if (item == Items.FISHING_ROD) s = SoundEvents.ENTITY_FISHING_BOBBER_THROW;
            if (s != null) c.world.playSoundClient(s, SoundCategory.PLAYERS, 0.5f, 0.4f / (c.world.random.nextFloat() * 0.4f + 0.8f));
        } catch (Throwable ignored) {}
    }

    /** Play the goat-horn toot sound locally + swing. */
    private static void simulateInstantHorn(Hand hand) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.world == null) return;
            // 1.21x stores the horn instrument as a component picking one of the
            // GOAT_HORN_SOUNDS registry entries. For a fake (no real NBT) we use
            // the first instrument sound so the user hears a toot — purely local.
            SoundEvent horn = null;
            try {
                if (!SoundEvents.GOAT_HORN_SOUNDS.isEmpty()) {
                    horn = SoundEvents.GOAT_HORN_SOUNDS.get(0).value();
                }
            } catch (Throwable ignored) {}
            if (horn != null) {
                c.world.playSoundClient(horn, SoundCategory.RECORDS, 1.0f, 1.0f);
            }
            c.player.swingHand(hand);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem simulateInstantHorn threw", t);
        }
    }

    /**
     * Called from {@link java.lang.Object} at the
     * HEAD of {@code tickItemStackUsage} for the LOCAL player on a dedicated
     * server. When the fake is an eat/drink whose use timer is about to hit
     * zero, vanilla's mc side skips the consume path (it's
     * {@code world.isClient}-gated) so the pose would persist forever — we
     * finish it here (sound + clearActiveItem) and tell the mixin to cancel
     * the vanilla tick.
     *
     * @return {@code true} to cancel {@code tickItemStackUsage}.
     */
    public static boolean onUseTickPre(LivingEntity actor, ItemStack activeStack) {
        try {
            if (!active) return false;
            if (!isDedicatedServer()) return false;
            if (actor == null) return false;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.player != actor) return false;
            if (activeStack == null || !isFakeIdentity(activeStack)) return false;
            if (activeStack.isUsedOnRelease()) return false;  // bow/crossbow/trident release via stopUsingItem
            if (currentSimKind != SimKind.HELD_EAT && currentSimKind != SimKind.HELD_DRINK) return false;
            // About to complete: the pre-decrement timer is 1 (a decrement to
            // 0 would fire vanilla's no-op-on-mc consume branch).
            int left = actor.getItemUseTimeLeft();
            if (left > 1) return false;
            completeHeldUse(actor);
            return true;  // cancel the vanilla tick (we've handled completion)
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem onUseTickPre threw", t);
            return false;
        }
    }

    /**
     * Called from {@link java.lang.Object} at the
     * HEAD of {@code stopUsingItem} for the LOCAL player on a dedicated
     * server, BEFORE vanilla clears the active item. For bow/crossbow/spear
     * we spawn a clientside fake projectile (with velocity proportional to
     * the draw charge) + play the shoot sound, so the release feels real even
     * though the server never knew we drew. For eat/drink we only play the
     * finish sound if the user held long enough for a full consume; partial
     * releases are silent (vanilla partial-eat = no consumption).
     */
    public static void onUseStopPre(LivingEntity actor, ItemStack activeStack, int useTime) {
        try {
            if (!active) return;
            if (!isDedicatedServer()) return;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.player == null || c.player != actor) return;
            if (activeStack == null || !isFakeIdentity(activeStack)) return;
            SimKind kind = currentSimKind;
            switch (kind) {
                case HELD_BOW: case HELD_CROSSBOW: case HELD_SPEAR:
                    fireChargedProjectile(actor, activeStack, useTime, kind);
                    break;
                case HELD_EAT: case HELD_DRINK:
                    // Only finish-sound if held about long enough (>= 80% of
                    // max); a quick tap-and-release is a cancelled eat, matching
                    // vanilla's partial-eat = no consume cosmetic.
                    int max = activeStack.getMaxUseTime(actor);
                    if (max > 0 && useTime >= (int) (max * 0.8f)) {
                        completeHeldUse(actor);  // play finish + burp; vanilla then clears the pose
                    }
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem onUseStopPre threw", t);
        } finally {
            resetUseSim();  // the pose is ending regardless
        }
    }

    /** Eat/drink completion: play the finish sound (+ burp for eats) + end the pose. */
    private static void completeHeldUse(LivingEntity actor) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null) return;
            SimKind kind = currentSimKind;
            SoundEvent finish = kind == SimKind.HELD_DRINK
                ? SoundEvents.ENTITY_GENERIC_DRINK.value()
                : SoundEvents.ENTITY_GENERIC_EAT.value();
            c.world.playSoundClient(finish, SoundCategory.PLAYERS, 0.8f, 1.0f);
            if (kind == SimKind.HELD_EAT) {
                c.world.playSoundClient(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.5f, 1.0f);
            }
            try { actor.clearActiveItem(); } catch (Throwable ignored) {}  // end the pose + USING_ITEM flag
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem completeHeldUse threw", t);
        }
    }

    /**
     * Spawn a clientside fake projectile on a charged-release (bow/crossbow/
     * trident) + play the matching shoot sound. Velocity follows the draw
     * charge (bow uses {@link net.minecraft.item.BowItem#getPullProgress});
     * crossbow/trident fire at their vanilla fixed speed.
     */
    private static void fireChargedProjectile(LivingEntity actor, ItemStack fakeStack, int useTime, SimKind kind) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null) return;
            Vec3d look = actor.getRotationVec(1.0f);
            Vec3d eye = actor.getEyePos();
            float speed;
            net.minecraft.entity.projectile.ProjectileEntity proj;
            if (kind == SimKind.HELD_BOW) {
                float pull = net.minecraft.item.BowItem.getPullProgress(useTime);
                speed = Math.max(0.1f, pull) * 3.0f;
                proj = new net.minecraft.entity.projectile.ArrowEntity(
                    c.world, actor, new ItemStack(Items.ARROW), ItemStack.EMPTY);
                if (pull >= 1.0f && proj instanceof net.minecraft.entity.projectile.PersistentProjectileEntity) {
                    try { ((net.minecraft.entity.projectile.PersistentProjectileEntity) proj).setCritical(true); } catch (Throwable ignored) {}
                }
            } else if (kind == SimKind.HELD_CROSSBOW) {
                speed = 3.15f;
                proj = new net.minecraft.entity.projectile.ArrowEntity(
                    c.world, actor, new ItemStack(Items.ARROW), ItemStack.EMPTY);
            } else {  // HELD_SPEAR (trident)
                speed = 2.5f;
                proj = new net.minecraft.entity.projectile.TridentEntity(c.world, actor, fakeStack.copy());
            }
            proj.setVelocity(look.x * speed, look.y * speed, look.z * speed);
            try { proj.setOwner(actor); } catch (Throwable ignored) {}
            try { proj.setPosition(eye.x, eye.y, eye.z); } catch (Throwable ignored) {}
            c.world.addEntity(proj);  // addEntity = clientside commit path (NOT spawnEntity)
            playShootSound(kind);
            // Swing the hand (vanilla bow release does).
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null && currentSimHand != null) {
                mc.player.swingHand(currentSimHand);
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem fireChargedProjectile threw", t);
        }
    }

    /** Play the bow/crossbow/trident shoot sound locally. */
    private static void playShootSound(SimKind kind) {
        try {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null) return;
            SoundEvent s = null;
            if (kind == SimKind.HELD_BOW) s = SoundEvents.ENTITY_ARROW_SHOOT;
            else if (kind == SimKind.HELD_CROSSBOW) s = SoundEvents.ITEM_CROSSBOW_SHOOT;
            else if (kind == SimKind.HELD_SPEAR) s = SoundEvents.ITEM_TRIDENT_THROW.value();  // RegistryEntry
            if (s != null) c.world.playSoundClient(s, SoundCategory.PLAYERS, 1.0f,
                1.0f / (c.world.random.nextFloat() * 0.4f + 0.8f));
        } catch (Throwable ignored) {}
    }

    /** Discard a previously-cast fake fishing bobber (if still alive + tracked). */
    private static void discardFakeBobber() {
        try {
            int id = fakeBobberId;
            if (id < 0) return;
            MinecraftClient c = MinecraftClient.getInstance();
            if (c == null || c.world == null) return;
            net.minecraft.entity.Entity e = c.world.getEntityById(id);
            if (e != null && !e.isRemoved()) e.discard();
        } catch (Throwable ignored) {
        } finally {
            fakeBobberId = -1;
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Cursor / ghost-slot tracking — called ONLY from the
    //  ScreenHandler.onSlotClick TAIL mixin (ScreenHandlerMixin) and the
    //  ClientConnectionMixin (outgoing click suppression).
    //  Everything here is called from the main render / network thread so
    //  no extra synchronization is needed beyond {@code volatile}.
    // ────────────────────────────────────────────────────────────

    /**
     * Set by {@link java.lang.Object} after every
     * onSlotClick: tracks whether the cursor currently carries the fake
     * (so outgoing clicks during a drag can be suppressed until the drop).
     */
    public static void setCursorIsGhost(boolean v) {
        cursorIsGhost = v;
        if (v && cursorFakeStack == null) {
            try {
                MinecraftClient c = MinecraftClient.getInstance();
                if (c != null && c.player != null && c.player.currentScreenHandler != null) {
                    ItemStack cur = c.player.currentScreenHandler.getCursorStack();
                    cursorFakeStack = (cur != null && !cur.isEmpty()) ? cur.copy() : null;
                }
            } catch (Throwable ignored) {}
        }
        if (!v) cursorFakeStack = null;
    }

    /**
     * Update the tracked ghost slot. Called by the onSlotClick TAIL mixin after
     * re-scanning the player inventory for the fake, or by snap-back when the
     * fake lands in a chest and the server resets it.
     */
    public static void setGhostSlot(int slot) {
        ghostSlot = slot;
        if (slot >= 0) lastGhostSlot = slot;
    }

    /**
     * Snap-back path: the ghost landed somewhere the server rejected (a
     * chest slot, or it has been cleared). Re-place the fake at
     * {@link #lastGhostSlot} and restore the cursor to empty in the local view.
     * Called by the onSlotClick TAIL mixin when the fake is missing from the
     * player's inventory after a click.
     */
    public static void snapBackGhost() {
        snapBackGhosts(1);
    }

    /**
     * Snap {@code count} fake copies back to the player's inventory. Used when
     * a container holding fake stacks is closed — the chest slots are gone
     * from the visible world, so every parked copy returns to free inventory
     * slots. Never clobbers real items (only fills empty, non-fake slots) and
     * never duplicates the fakes that legitimately remain in the inventory.
     * Singleplayer: vanilla handles the chest slots itself (the fake is real
     * there), the guard {@link #isDedicatedServer()} is not applied so reassert
     * stays out of integrated-mode chests — this path only runs while the
     * container's ghost state is tracked (dedicated-only).
     */
    private static void snapBackGhosts(int count) {
        try {
            if (fakeStack == null) return;
            
            if (mc.player == null) return;
            PlayerInventory inv = mc.player.getInventory();
            int placed = 0;
            for (int i = 0; i < inv.size() && placed < count; i++) {
                ItemStack cur = inv.getStack(i);
                if (cur != null && !cur.isEmpty()) continue;
                inv.setStack(i, fakeStack.copy());
                if (isIntegratedServer()) {
                    syncToIntegratedServer(i, fakeStack);
                }
                if (placed == 0) {
                    ghostSlot = i;
                    lastGhostSlot = i;
                }
                placed++;
            }
            if (placed == 0 && ghostSlot < 0) {
                // Inventory completely full — force a copy into the selected
                // slot so the fakes aren't lost.
                int sel = inv.getSelectedSlot();
                inv.setStack(sel, fakeStack.copy());
                ghostSlot = sel;
                lastGhostSlot = sel;
            }
            cursorIsGhost = false;
            dbg("snapBackGhosts", "count=" + count + " placed=" + placed);
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakeItem snapBackGhosts threw", t);
        }
    }

    // ────────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────────

    /** ItemStack identity-equality helper (item + components + count), null-safe. */
    public static boolean stackEquals(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return ItemStack.areEqual(a, b);
    }

    private static Identifier parseId(String itemId) {
        try {
            int colon = itemId.indexOf(':');
            if (colon >= 0) {
                return Identifier.of(itemId.substring(0, colon), itemId.substring(colon + 1));
            }
            return Identifier.of(itemId);
        } catch (Throwable t) {
            return null;
        }
    }
}
