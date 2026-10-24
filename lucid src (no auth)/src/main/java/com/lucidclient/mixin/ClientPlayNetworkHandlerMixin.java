package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.suschunk.SusChunkCache;
import com.lucidclient.module.impl.FakeItem;
import com.lucidclient.module.impl.LightFinder;
import com.lucidclient.module.impl.SpawnerESP;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Packet-interception mixin — the bypass layer for SusChunkESP on hardcore
 * anti-xray servers (DonutSMP-style PaperMC Engine Mode 2).
 *
 * ── Why a mixin, not a renderer scan ────────────────────────────────
 * Anti-xray plugins obfuscate block data and cull chunks below Y=0, so the
 * game's renderer can't draw what the server refuses to send. But the server
 * is forced to leak block-entity data (chests, hoppers, spawners, shulker
 * boxes) via {@link ChunkDataS2CPacket} and {@link BlockEntityUpdateS2CPacket}
 * so the client knows these tile entities exist at all.
 *
 * This mixin intercepts those packets at HEAD — BEFORE the vanilla engine
 * touches them — and feeds the raw (pos, type, nbt) triples into
 * {@link SusChunkCache}. The ESP then reads from the cache, completely
 * ignoring visual culling limits. Cached data survives chunk unloads, so
 * even split-second leaks during lag spikes / teleports / fast flight are
 * permanently logged and can be projected back onto the world later.
 *
 * ── Injected handlers ──────────────────────────────────────────────
 *   onChunkData(ChunkDataS2CPacket)        — initial chunk load: iterate the
 *                                            packet's block-entity list,
 *                                            record each into the cache.
 *   onBlockEntityUpdate(BlockEntityUpdateS2CPacket) — single-tile update
 *                                            (chest opened, hopper lock
 *                                            changed, etc.). Catches the
 *                                            "player modified this months
 *                                            ago" leaks.
 *   onUnloadChunk(UnloadChunkS2CPacket)    — mark the chunk as unloaded so
 *                                            the cache can prune it on its
 *                                            age-based schedule (but never
 *                                            while it's still loaded).
 *
 * All handlers are HEAD-injected and DO NOT cancel — vanilla still processes
 * the packet normally. We're purely snooping the raw stream.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    /**
     * Initial chunk load — the server sends the full block-entity list for
     * the chunk here. Read the {@link ChunkData}'s private
     * {@code blockEntities} list via the {@link ChunkDataAccessor} mixin
     * (each entry packs the local XZ, Y, type, and NBT) and record every
     * container tile entity into the cache.
     */
    @Inject(method = "onChunkData", at = @At("HEAD"))
    private void lucid$onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        try {
            int cx = packet.getChunkX();
            int cz = packet.getChunkZ();
            ChunkData data = packet.getChunkData();
            if (data == null) return;
            // Mark this chunk as loaded so the cache won't prune it.
            SusChunkCache.get().setLoaded(cx, cz, true);
            // glazed-style event hooks: hand the chunk to the light-source
            // ESP (blocks are readable from the world by its next tick) —
            // the module only queues, so this stays netty-thread safe.
            LightFinder.onChunkLoaded(cx, cz);
            // Read the private blockEntities list via the accessor mixin.
            // The list element type (ChunkData.BlockEntityData) is package-
            // private, so we type as List<?> and cast each element to our
            // BlockEntityDataAccessor interface to read its fields.
            List<?> list = ((ChunkDataAccessor) data).lucid$getBlockEntities();
            if (list == null) return;
            // Each BlockEntityData packs (localXz, y, type, nbt). localXz is
            // (x & 15) | ((z & 15) << 4), so the absolute BlockPos is
            // (cx*16 + (localXz & 15), y, cz*16 + ((localXz >> 4) & 15)).
            for (Object bed : list) {
                if (bed == null) continue;
                BlockEntityDataAccessor a = (BlockEntityDataAccessor) bed;
                var type = a.lucid$getType();
                if (type == null) continue;
                // glazed SpawnerNotifier-style: dispatch spawners the moment
                // their chunk data arrives (dedupe + client-thread hop happen
                // inside the module).
                if (type == BlockEntityType.MOB_SPAWNER) {
                    int lxz = a.lucid$getLocalXz();
                    SpawnerESP.onSpawnerFound(new BlockPos(
                        cx * 16 + (lxz & 15), a.lucid$getY(), cz * 16 + ((lxz >> 4) & 15)));
                }
                if (!SusChunkCache.isContainer(type)) continue;
                int localXz = a.lucid$getLocalXz();
                int lx = localXz & 15;
                int lz = (localXz >> 4) & 15;
                int y = a.lucid$getY();
                BlockPos pos = new BlockPos(cx * 16 + lx, y, cz * 16 + lz);
                var nbt = a.lucid$getNbt();
                if (nbt == null) nbt = new net.minecraft.nbt.NbtCompound();
                SusChunkCache.get().recordFromChunkData(cx, cz, pos, type, nbt);
            }
        } catch (Throwable ignored) {
            // Never let a packet-side parse failure crash the network thread.
        }
    }

    /**
     * Single-tile block-entity update — fires after chunk load too (chest
     * opened, hopper lock changed, spawner activation, etc.). These are the
     * "player modified this area" leaks the ESP heuristics rely on.
     */
    @Inject(method = "onBlockEntityUpdate", at = @At("HEAD"))
    private void lucid$onBlockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            BlockPos pos = packet.getPos();
            if (pos == null) return;
            var type = packet.getBlockEntityType();
            if (type == null) return;
            // Spawner BE updates (spawner placed / re-synced nearby).
            if (type == BlockEntityType.MOB_SPAWNER) {
                SpawnerESP.onSpawnerFound(pos);
            }
            if (!SusChunkCache.isContainer(type)) return;
            var nbt = packet.getNbt();
            if (nbt == null) return;
            SusChunkCache.get().recordFromBlockEntityUpdate(pos, type, nbt);
        } catch (Throwable ignored) {
            // Defensive: never crash the network thread over a bad packet.
        }
    }

    /**
     * Chunk unload — mark the chunk as no longer loaded so the cache can age
     * it out via {@link SusChunkCache#prune()}. We do NOT clear the cached
     * data here: the whole point of the bypass is that cached leaks persist
     * beyond the chunk's lifetime so the ESP can still flag a base after the
     * player walks away.
     */
    @Inject(method = "onUnloadChunk", at = @At("HEAD"))
    private void lucid$onUnloadChunk(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        try {
            // UnloadChunkS2CPacket exposes chunk X/Z directly.
            SusChunkCache.get().setLoaded(packet.pos().x, packet.pos().z, false);
        } catch (Throwable ignored) {
            // Defensive: API shape may differ across patch versions.
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Event-driven leak interception
    // ════════════════════════════════════════════════════════════════

    /**
     * Sound event triangulation — the server must send sound X/Y/Z so the
     * client can pan the audio. We read the coordinates and increment the
     * chunk's sound counters. Sounds in the BLOCKS / HOSTILE / NEUTRAL
     * categories, plus a small high-value allowlist (piston, note block,
     * redstone, anvil, grindstone, smoker, blast furnace, composter,
     * campfire, brewing stand), count as "mechanical" — the strongest base
     * signature. Mob-damage sounds (zombies, skeletons) in HOSTILE also
     * flag mob farms.
     */
    @Inject(method = "onPlaySound", at = @At("HEAD"))
    private void lucid$onPlaySound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        try {
            // PlaySoundS2CPacket.getX/Y/Z() return the world-coordinate
            // doubles (already descaled from the fixed-point fields by the
            // vanilla getter). Convert to block coords then chunk coords.
            // Use Math.floor before the cast: a bare (int) cast truncates
            // toward zero, so a negative coordinate like -0.5 would round to
            // 0 (chunk 0) instead of -1 (the chunk spanning -16..-1). Flooring
            // first ensures negative coords map to the correct chunk.
            int bx = (int) Math.floor(packet.getX());
            int by = (int) Math.floor(packet.getY());
            int bz = (int) Math.floor(packet.getZ());
            int cx = bx >> 4;
            int cz = bz >> 4;
            SoundCategory cat = packet.getCategory();
            boolean mechanical = isMechanicalSound(packet.getSound(), cat, by);
            SusChunkCache.get().recordSoundEvent(cx, cz, mechanical);
        } catch (Throwable ignored) {
            // Defensive — never crash the network thread.
        }
    }

    /**
     * Block-update sniffing — the server struggles to hide CHANGES to blocks.
     * High-frequency updates in a chunk that should be static rock are a farm
     * / redstone beacon.
     */
    @Inject(method = "onBlockUpdate", at = @At("HEAD"))
    private void lucid$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            BlockPos pos = packet.getPos();
            if (pos == null) return;
            SusChunkCache.get().recordBlockUpdate(pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable ignored) {
            // Defensive.
        }
    }

    /**
     * Multi-block update (ChunkDeltaUpdateS2CPacket) — a batch of block
     * state changes in a chunk section. Each entry counts as a block update
     * for the chunk's event score. The packet exposes a {@code visitUpdates}
     * BiConsumer<BlockPos, BlockState> that we walk for the count.
     */
    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"))
    private void lucid$onChunkDeltaUpdate(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            // The packet doesn't expose chunk X/Z directly — only the section
            // pos. Unpack it, then walk the updates to count them.
            // ChunkSectionPos packs world (x>>4, y>>4, z>>4) — but we want
            // chunk X/Z (x>>4, z>>4), which is the same as the section's chunk
            // coords (section X = chunk X). visitUpdates fires once per
            // changed block; we count the chunk once but the score weight
            // scales with frequency over time.
            // Use a single-element array to capture the chunk coords from
            // the first visit (all entries share the same section → chunk).
            final int[] chunkCoords = new int[2];
            final boolean[] seen = new boolean[1];
            packet.visitUpdates((pos, state) -> {
                if (!seen[0]) {
                    chunkCoords[0] = pos.getX() >> 4;
                    chunkCoords[1] = pos.getZ() >> 4;
                    seen[0] = true;
                }
                // Each block counts as one update.
                SusChunkCache.get().recordBlockUpdate(chunkCoords[0], chunkCoords[1]);
            });
        } catch (Throwable ignored) {
            // Defensive.
        }
    }

    /**
     * Block event (piston / door / note block / redstone) — the "moving
     * parts" that force the server to drop its anti-xray obfuscation for a
     * split second (the piston-desync bypass). The packet carries a BlockPos
     * directly.
     */
    @Inject(method = "onBlockEvent", at = @At("HEAD"))
    private void lucid$onBlockEvent(BlockEventS2CPacket packet, CallbackInfo ci) {
        try {
            BlockPos pos = packet.getPos();
            if (pos == null) return;
            SusChunkCache.get().recordBlockEvent(pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable ignored) {
            // Defensive.
        }
    }

    /**
     * Particle spawn — underground torch smoke, redstone dust, mob flames,
     * portal particles. The packet carries double X/Y/Z directly.
     */
    @Inject(method = "onParticle", at = @At("HEAD"))
    private void lucid$onParticle(ParticleS2CPacket packet, CallbackInfo ci) {
        try {
            // Math.floor before cast: a bare (int) cast truncates toward zero,
            // so a negative coordinate like -0.5 would map to chunk 0 instead
            // of chunk -1. Flooring first ensures correct chunk attribution.
            int cx = ((int) Math.floor(packet.getX())) >> 4;
            int cz = ((int) Math.floor(packet.getZ())) >> 4;
            SusChunkCache.get().recordParticleEvent(cx, cz);
        } catch (Throwable ignored) {
            // Defensive.
        }
    }

    /**
     * Entity spawn — a cluster of cows packed underground, item frames /
     * armor stands (storage markers), or a sudden spawn burst (mob farm).
     * The packet carries double X/Y/Z directly.
     */
    @Inject(method = "onEntitySpawn", at = @At("HEAD"))
    private void lucid$onEntitySpawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        try {
            // Math.floor before cast: a bare (int) cast truncates toward zero,
            // so a negative coordinate like -0.5 would map to chunk 0 instead
            // of chunk -1. Flooring first ensures correct chunk attribution.
            int cx = ((int) Math.floor(packet.getX())) >> 4;
            int cz = ((int) Math.floor(packet.getZ())) >> 4;
            SusChunkCache.get().recordEntitySpawn(cx, cz);
        } catch (Throwable ignored) {
            // Defensive.
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Fake Item: incoming slot-sync suppression for the ghost
    // ════════════════════════════════════════════════════════════════

    /**
     * Cancel a server-driven overwrite of the ghost slot in the player's own
     * {@link net.minecraft.entity.player.PlayerInventory}. The server believes
     * that slot is empty (or holds the pre-fake real item) — a periodic resync
     * packet would silently revert our fake to nothing locally. Dropping the
     * packet here keeps the fake in view.
     */
    @Inject(method = "onSetPlayerInventory", at = @At("HEAD"), cancellable = true)
    private void lucid$cancelSetPlayerInventoryForGhostSlot(SetPlayerInventoryS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            // Only on a dedicated server — singleplayer's integrated server is
            // authoritative on the SAME inventory slot (we wrote the fake there
            // via syncToIntegratedServer), so its resyncs carry the fake and must
            // flow through un-cancelled; canceling them would desync the client
            // when the server-side slot later takes a real picked-up item.
            if (!FakeItem.isDedicatedServer()) return;
            // Guard EVERY fake-occupied player-inventory slot (primary ghost +
            // extras) against the server overwriting it with its "empty" truth.
            if (FakeItem.isGhostSlot(packet.slot())) {
                ci.cancel();
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onSetPlayerInventory threw", t);
        }
    }

    /**
     * Cancel a server-driven slot update for EITHER the player-inventory ghost
     * slot OR the tracked container ghost slot. The server believes those slots
     * are empty (the fake never existed there for it) — a periodic resync
     * packet would silently revert our local fake to nothing. Cancelling keeps
     * the fake in view. Out-of-scope slots flow through so honest chest truth
     * (other slots the server legitimately updated) stays intact.
     */
    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"), cancellable = true)
    private void lucid$cancelSlotUpdateForGhostSlot(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            // Only on a dedicated server — singleplayer's integrated server is
            // authoritative on the SAME inventory, so canceling its syncs
            // would fight real server-side truth.
            if (!FakeItem.isDedicatedServer()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            int syncId = packet.getSyncId();
            int slot = packet.getSlot();

            // Unified, screen-aware guard: cancels a server-driven overwrite for
            // ANY fake-occupied slot of the CURRENT open screen — the player
            // grid (primary ghost + every clone stack) whether addressed via the
            // player screen OR embedded in an open chest/shulker/enderchest
            // window at chest offsets, AND every container slot remembered in
            // the chest. The server believes those slots are empty (the fakes
            // never existed for it); a periodic resync packet would silently
            // revert each one to nothing locally. This single check treats
            // every stack identically — no more "primary survives, clones die".
            //
            // Empty-push suppression only: a server slot update that carries an
            // EMPTY stack is the lesson "wipe this slot back to truth" — the
            // exact sweep that kills a fake, so cancel it and let the heal keep
            // the fake. But an update carrying a REAL stack in a remembered fake
            // slot is the server delivering authoritative content (another real
            // item slid into that position) — that must ALWAYS render or the
            // user's container contents "disappear". The full-window path
            // (onInventory) already lets real contents through; this keeps the
            // per-slot path consistent.
            ItemStack incoming = null;
            try { incoming = packet.getStack(); } catch (Throwable ignored) {}
            boolean emptyPush = incoming == null || incoming.isEmpty();
            if (FakeItem.isFakeScreenSlot(syncId, slot) && emptyPush) {
                FakeItem.dbg("onSlotUpdate-CANCEL", "empty-push fake-slot syncId=" + syncId + " slot=" + slot);
                ci.cancel();
                return;
            }
            // Pass-through sample (every 200th) so we can see the server's
            // resync traffic shape and confirm it's not hitting us in a
            // syncId/slot pairing we didn't account for.
            if (onSlotUpdateSeenCnt++ % 200 == 0) {
                FakeItem.dbg("onSlotUpdate-pass", "syncId=" + syncId + " slot=" + slot
                    + " ghostSlot=" + FakeItem.getGhostSlot() + " cnt=" + onSlotUpdateSeenCnt);
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onScreenHandlerSlotUpdate threw", t);
        }
    }

    /** Counter to throttle the onSlotUpdate pass-through debug log. */
    private static volatile int onSlotUpdateSeenCnt = 0;

    /**
     * Absorb a server-driven FULL-WINDOW refresh ({@link InventoryS2CPacket},
     * handler {@code onInventory}) that would otherwise wipe the fake.
     * {@code onInventory} routes to {@code ScreenHandler.updateSlotStacks}
     * which wholesale overwrites every slot in that handler. Two cases:
     * <ul>
     *   <li><b>Player window</b> ({@code syncId} == player screen handler):
     *       the fake lives in the player grid, so a refresh would sweep it —
     *       cancel the whole packet and re-apply the fake to its tracked slots
     *       (player inv + container + cursor) via
     *       {@link FakeItem#reapplyAfterResync()}.</li>
     *   <li><b>Open CONTAINER window</b>: the packet carries the storage's
     *       REAL contents (the server's authoritative view). Voiding it used
     *       to blank the entire container whenever a fake sat in the player
     *       grid of the same window. We now LET the packet through — real
     *       items render — and re-assert the fake slots immediately after via
     *       the {@link #lucid$reapplyAfterInventory RETURN} inject, so fakes
     *       survive in place with no flicker.</li>
     * </ul>
     */
    @Inject(method = "onInventory", at = @At("HEAD"), cancellable = true)
    private void lucid$cancelInventoryResyncForGhost(InventoryS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            int syncId = packet.syncId();
            int ghostSlot = FakeItem.getGhostSlot();
            boolean cursorGhost = FakeItem.isCursorIsGhost();

            int playerSyncId;
            try { playerSyncId = client.player.playerScreenHandler.syncId; }
            catch (Throwable t) { playerSyncId = -1; }
            int openSyncId = (client.player.currentScreenHandler != null)
                ? client.player.currentScreenHandler.syncId : -1;

            // Wholesale window refresh. Every screen embeds the player
            // inventory, so a full-window sync of the CURRENT screen carries
            // the server's "empty" truth for BOTH the container slots AND the
            // player-grid section — sweeping every fake stack (primary +
            // clones) at once in its old shape.
            boolean playerWindowSync = syncId == playerSyncId;
            boolean containerWindowSync = !playerWindowSync && openSyncId >= 0 && syncId == openSyncId;
            boolean fakeOnScreen = FakeItem.hasFakeOnScreen(syncId);
            boolean wipesCursor = cursorGhost && syncId == openSyncId;

            if (containerWindowSync && (fakeOnScreen || wipesCursor)) {
                // Container full-window refresh while fakes are present: let the
                // packet apply (so the container's REAL items render) and
                // re-assert the fakes right after it lands — same-tick, before
                // the frame, so no flicker.
                lucid$reapplyAfterInventory = true;
                FakeItem.dbg("onInventory-LET-THROUGH", "pkt.syncId=" + syncId
                    + " playerSyncId=" + playerSyncId + " openSyncId=" + openSyncId
                    + " ghostSlot=" + ghostSlot + " cursorGhost=" + cursorGhost
                    + " fakeOnScreen=" + fakeOnScreen);
                return;
            }

            if (!playerWindowSync) return;   // unrelated window — let it through

            // Player window: a refresh sweeps the grid fake (and would also
            // blank a fake-carrying cursor) — cancel and restore everything.
            if (!fakeOnScreen && !wipesCursor) return;

            FakeItem.dbg("onInventory-CANCEL", "pkt.syncId=" + syncId
                + " playerSyncId=" + playerSyncId + " openSyncId=" + openSyncId
                + " ghostSlot=" + ghostSlot + " cursorGhost=" + cursorGhost);

            ci.cancel();
            FakeItem.reapplyAfterResync();
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onInventory threw", t);
        }
    }

    /** Set when the HEAD handler decided to let a container full-window sync
     *  through; consumed by the RETURN inject so the fake slots are re-asserted
     *  AFTER the server's real contents have applied (no flicker, container
     *  items stay visible). */
    private static volatile boolean lucid$reapplyAfterInventory = false;

    /**
     * Re-assert the fake slots right after a container {@code onInventory}
     * full-window refresh we chose to let through (see the HEAD inject,
     * {@code lucid$cancelInventoryResyncForGhost}). The packet's real contents
     * have already been written to the handler, so re-applying restores the
     * fakes in place while keeping every honest item on screen.
     */
    @Inject(method = "onInventory", at = @At("RETURN"))
    private void lucid$reapplyAfterInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        try {
            if (!lucid$reapplyAfterInventory) return;
            lucid$reapplyAfterInventory = false;
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            FakeItem.reapplyAfterResync();
            // Post-write snapshot of the OPEN storage window: shows exactly
            // what the server's real contents left behind plus what the
            // reapply restored, so the "other stacks disappear and leave 1"
            // repro is visible in the log on a single line.
            try {
                net.minecraft.screen.ScreenHandler handler = client.player.currentScreenHandler;
                if (handler != null && handler.syncId >= 0
                    && !(handler instanceof net.minecraft.screen.PlayerScreenHandler)) {
                    StringBuilder sb = new StringBuilder("container-after-reapply syncId=" + handler.syncId);
                    for (int i = 0; i < handler.slots.size(); i++) {
                        net.minecraft.screen.slot.Slot s = handler.slots.get(i);
                        if (s == null || s.inventory instanceof net.minecraft.entity.player.PlayerInventory) continue;
                        net.minecraft.item.ItemStack st = s.getStack();
                        if (st != null && !st.isEmpty()) {
                            sb.append(" | ").append(i).append('=')
                              .append(st.getItem().getName().getString()).append('x').append(st.getCount());
                        }
                    }
                    FakeItem.dbg("container-after-reapply", sb.toString());
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onInventory reapply threw", t);
        }
    }

    /**
     * Cancel a server-driven cursor overwrite
     * ({@link SetCursorItemS2CPacket}, handler {@code onSetCursorItem}) when
     * the local cursor currently carries the fake. The server believes the
     * cursor is empty (the fake never existed there for it) — this packet
     * would silently snap the cursor back to empty mid-drag. Cancelling keeps
     * the fake on the cursor so the user can finish dropping it where they
     * like.
     */
    @Inject(method = "onSetCursorItem", at = @At("HEAD"), cancellable = true)
    private void lucid$cancelCursorResyncForGhost(SetCursorItemS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            if (!FakeItem.isCursorIsGhost()) return;
            // The server can only legitimately clear or replace the cursor
            // when IT initiated the drag. For our fake the cursor was set
            // purely clientside, so any server cursor overwrite is a wipe we
            // must ignore. (Empty content → wipe; non-empty content → the
            // server trying to seed a real cursor stack we never asked for,
            // which can't happen since the fake isn't there server-side.)
            FakeItem.dbg("onSetCursorItem-CANCEL", "pkt.contents=" + (packet.contents() == null ? "null" : packet.contents().getItem().getName().getString()));
            ci.cancel();
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onSetCursorItem threw", t);
        }
    }

    /**
     * Cancel a server-driven equipment overwrite for OUR local player when
     * the update targets the fake's equipped slot (e.g. ELYTRA → CHEST) and
     * nothing else. The server believes that slot is empty (the fake elytra
     * was never equipped for it); a periodic
     * {@link EntityEquipmentUpdateS2CPacket} for our own entity-id would nuke
     * the local elytra, killing the cosmetic wing pose + the elytra gliding
     * model. Cancelling preserves the fake equipment so the wings stay open.
     *
     * <p>If the equipment update touches other slots too (it can carry a
     * list of {@code (slot, stack)} pairs), we let it through so those
     * honest slots stay current and rely on {@code FakeItem}'s periodic
     * re-assert to put the fake equipment back next tick (at most a
     * 50 ms flicker) — the alternative of partial application isn't
     * possible because {@code apply()} is one atomic call.
     */
    @Inject(method = "onEntityEquipmentUpdate", at = @At("HEAD"), cancellable = true)
    private void lucid$cancelEquipmentResyncForGhost(EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            int myId = client.player.getId();
            boolean isMe = packet.getEntityId() == myId;
            EquipmentSlot fakeSlot = FakeItem.getFakeEquipSlot();
            // Log every equipment packet for our own player (not spammy —
            // these fire on item equip/unequip, ~1/event).
            if (isMe) {
                FakeItem.dbgLight("onEntityEquipmentUpdate", "entityId=" + packet.getEntityId() + " isMe=" + isMe + " fakeEquipSlot=" + fakeSlot);
            }
            if (!isMe) return;

            if (fakeSlot == null) {
                FakeItem.dbgLight("onEntityEquipmentUpdate", "SKIP fakeSlot=null (fake item has no EQUIPPABLE component)");
                return;
            }

            java.util.List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> list =
                packet.getEquipmentList();
            if (list == null || list.isEmpty()) return;

            boolean touchesFake = false;
            boolean touchesOther = false;
            for (com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack> p : list) {
                if (p.getFirst() == fakeSlot) touchesFake = true;
                else touchesOther = true;
            }
            FakeItem.dbg("onEntityEquipmentUpdate", "touchesFake=" + touchesFake + " touchesOther=" + touchesOther + " fakeSlot=" + fakeSlot);
            if (touchesFake && !touchesOther) {
                FakeItem.dbgLight("onEntityEquipmentUpdate", "CANCEL (pure-equip-slot-wipe)");
                ci.cancel();
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.onEntityEquipmentUpdate threw", t);
        }
    }

    /**
     * Classify a sound event as "mechanical" — the high-value base signature.
     * A sound is mechanical if:
     *   - Its category is {@link SoundCategory#BLOCKS} (pistons, doors,
     *     redstone, furnaces, anvils, grindstones, smokers, etc.), OR
     *   - Its category is {@link SoundCategory#HOSTILE} (mob farms — zombies,
     *     skeletons, creepers taking damage / dying), OR
     *   - It matches a small high-value allowlist of specific sound events
     *     (piston extend/contract, note block, anvil, grindstone, composter,
     *     smoker, blast furnace, brewing stand, campfire, lantern) — these
     *     are the canonical "running machine" sounds.
     *
     * @param by the block Y of the sound — currently unused but available
     *           for future "below-Y=0 sound" filtering.
     */
    private static boolean isMechanicalSound(RegistryEntry<SoundEvent> sound,
                                             SoundCategory cat, int by) {
        if (cat == SoundCategory.BLOCKS) return true;
        if (cat == SoundCategory.HOSTILE) return true;
        if (sound == null || !sound.hasKeyAndValue()) return false;
        SoundEvent se = sound.value();
        // Direct identity checks against the canonical mechanical sounds.
        // NOTE_BLOCK_* constants are RegistryEntry<SoundEvent> in 1.21.x (the
        // sound registry uses reference entries), so compare the incoming
        // RegistryEntry directly to those constants via matches(). The other
        // BLOCK_* constants are bare SoundEvent, so compare the unwrapped
        // value.
        // Note-block family (RegistryEntry constants):
        if (sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_HARP)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BASS)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BELL)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_CHIME)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_FLUTE)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_GUITAR)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BIT)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BANJO)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_PLING)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_SNARE)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_HAT)
            || sound.matches(SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM)) {
            return true;
        }
        // Bare-SoundEvent constants:
        return se == SoundEvents.BLOCK_PISTON_EXTEND
            || se == SoundEvents.BLOCK_PISTON_CONTRACT
            || se == SoundEvents.BLOCK_ANVIL_HIT
            || se == SoundEvents.BLOCK_ANVIL_LAND
            || se == SoundEvents.BLOCK_ANVIL_USE
            || se == SoundEvents.BLOCK_GRINDSTONE_USE
            || se == SoundEvents.BLOCK_SMOKER_SMOKE
            || se == SoundEvents.BLOCK_CAMPFIRE_CRACKLE
            || se == SoundEvents.BLOCK_COMPOSTER_READY
            || se == SoundEvents.BLOCK_LANTERN_STEP
            || se == SoundEvents.BLOCK_LANTERN_HIT
            || se == SoundEvents.BLOCK_BREWING_STAND_BREW
            || se == SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT
            || se == SoundEvents.BLOCK_LADDER_STEP
            || se == SoundEvents.BLOCK_METAL_HIT
            || se == SoundEvents.BLOCK_METAL_PLACE
            || se == SoundEvents.BLOCK_METAL_STEP;
    }

    // ════════════════════════════════════════════════════════════════
    //  Freecam (Freecam-mod implementation): disables freecam when the
    //  player respawns or switches dimensions — the frozen body position
    //  is no longer meaningful after a teleport.
    // ════════════════════════════════════════════════════════════════
    @Inject(method = "onPlayerRespawn", at = @At("TAIL"))
    private void lucid$freecamOnPlayerRespawn(CallbackInfo ci) {
        try {
            if (com.lucidclient.module.impl.Freecam.instance() != null) {
                com.lucidclient.module.impl.Freecam.instance().onRespawn();
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayNetworkHandlerMixin.freecamOnPlayerRespawn threw", t);
        }
    }
}