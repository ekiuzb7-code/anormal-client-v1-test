package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.EnumSetting;
import com.anormal.client.lucid.IntSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.lucid.SusChunkCache;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.Map;

/**
 * RtpBaseFinder — DonutSMP base-discovery macro (glazed-style, no digging).
 *
 * <h3>Design origin</h3>
 * Ported from glazed's RTP base-finder family (Meteor addon for DonutSMP).
 * glazed's own {@code RTPBaseFinder} digs to Y=-58 and re-runs {@code /rtp
 * east} every 3s, but the actual detection design lives in its sibling
 * no-dig scanners ({@code RTPEndBaseFinder} / {@code RTPNetherBaseFinder}):
 *
 * <pre>
 *   on a fixed interval (default 10s):
 *       send "/rtp <region>"
 *   on every chunk that loads around the landing spot:
 *       count storage block entities (chests/shulkers/barrels/hoppers/...)
 *       remember any mob spawner
 *       if storage >= minimum-storage-count OR (critical-spawner AND a
 *          spawner was seen):  flag the chunk as a stash, log it, stop
 * </pre>
 *
 * This module reworks the old lucid implementation (which dug a hole to
 * bedrock at every RTP spot before scanning) onto that no-dig design:
 * the macro just keeps RTPing around the map while a passive chunk scan
 * watches every block entity the server leaks near each landing spot.
 * No rotation, no block breaking, no interaction-manager traffic — the
 * only server input is the real chat command {@code /rtp <region>}.
 *
 * <h3>What it does</h3>
 * A toggle-on hotkey macro that repeatedly:
 *   1. runs {@code /rtp <region>} — cycling through DonutSMP's RTP regions
 *      (asia, east, eu central, eu west, oceania, west) so successive hops
 *      spread across the map instead of clustering in one cluster,
 *   2. waits for the teleport to land (big position jump) and for the
 *      server to stream the surrounding chunks,
 *   3. scans the block-entity cache (see below) around the landing spot
 *      for storage containers and mob spawners — continuously for the
 *      rest of the RTP interval, so late-arriving chunks are caught,
 *   4. <b>stops the macro</b> (and prints the coordinates into chat)
 *      when a base is detected — otherwise hops to the next region.
 *
 * <h3>State machine (one phase per mc tick)</h3>
 * <pre>
 *   IDLE → SEND_RTP → WAIT_FOR_TELEPORT → SETTLE → SCANNING →
 *     BASE_FOUND (stop)  OR  back to SEND_RTP (repeat cycle)
 * </pre>
 *
 * <h3>Why the sus-chunk cache instead of digging</h3>
 * The old implementation dug to bedrock so chunks would (hopefully) render
 * and {@code WorldChunk.getBlockEntities()} would populate. That is slow,
 * loud, and unnecessary: DonutSMP-style servers run aggressive anti-xray
 * but are still forced to leak block-entity data (chests, shulkers,
 * spawners) in {@code ChunkDataS2CPacket} so the mc knows the tile
 * entities exist. Lucid's {@link SusChunkCache} is fed directly from those
 * packets by {@code ClientPlayNetworkHandlerMixin} — the exact equivalent
 * of glazed's ChunkDataEvent-driven {@code scanChunks}, but surviving
 * anti-xray and chunk culling. The macro therefore scans the CACHE, not
 * the world: no digging, faster cycles, and detection works even for
 * chunks the renderer never fully draws.
 *
 * <h3>Detection</h3>
 * Per glazed's semantics, a landing spot counts as a base when either:
 *   - <b>Critical Spawner</b> is on and any mob spawner block entity lies
 *     within the scan radius (glazed flags any spawner as an instant
 *     stash signal), OR
 *   - the number of storage containers within {@code Scan Radius} of the
 *     player reaches {@code Min Storage} (lucid threshold semantics — a
 *     radius-scoped container count rather than glazed's per-chunk count).
 * On detection the macro stops and prints a SUCCESS chat line with the
 * evidence position and container count; with {@code Disable On Find} on
 * (glazed's default behavior — it literally disconnects the mc to
 * preserve the find) the module also disables itself.
 *
 * <h3>Real inputs only</h3>
 * The only server-bound action is {@code networkHandler.sendChatCommand(
 * "rtp <region>")} — exactly the command a player would type. Everything
 * else reads client-side state.
 *
 * Settings:
 *   Toggle Key, RTP Region, RTP Interval, Min Storage, Scan Radius,
 *   Critical Spawner, Disable On Find.
 */
public class RtpBaseFinder extends Module {

    // ── RTP regions (glazed RTPer's overworld set, declaration order) ──
    /** DonutSMP overworld /rtp arguments, cycled in glazed's enum order. */
    private static final String[] RTP_REGIONS = {
        "Asia", "East", "EU Central", "EU West", "Oceania", "West"
    };

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to start/stop the RTP base-finding macro. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final EnumSetting rtpRegion = addSetting(
        new EnumSetting("RTP Region", "Which /rtp region to hop through. Cycle rotates across all DonutSMP "
            + "overworld regions (asia, east, eu central, eu west, oceania, west) so the macro spreads "
            + "across the map instead of re-visiting one cluster.",
            new String[]{"Cycle", "Asia", "East", "EU Central", "EU West", "Oceania", "West"},
            "Cycle"));
    private final IntSetting rtpInterval = addSetting(
        new IntSetting("RTP Interval", "Seconds between /rtp sends (glazed semantics: must exceed the server's "
            + "RTP cooldown; each landing spot is scanned for this long before hopping).",
            5, 60, 10, 1));
    private final IntSetting minStorage = addSetting(
        new IntSetting("Min Storage", "Minimum number of storage containers within Scan Radius to count as a base.",
            1, 50, 5, 1));
    private final IntSetting scanRadius = addSetting(
        new IntSetting("Scan Radius", "Radius (in blocks) around the RTP landing spot scanned for storage "
            + "containers and spawners.",
            8, 64, 32, 1));
    private final BooleanSetting criticalSpawner = addSetting(
        new BooleanSetting("Critical Spawner", "Treat ANY mob spawner within Scan Radius as an instant base "
            + "(glazed default — spawners almost never occur near RTP landing spots except at farms/bases).",
            true));
    private final BooleanSetting disableOnFind = addSetting(
        new BooleanSetting("Disable On Find", "Disable the module the moment a base is found (glazed disconnects "
            + "on find by default; lucid stops the macro and keeps you connected).",
            true));
    // ── State machine ─────────────────────────────────────────────
    private enum Phase {
        IDLE,              // waiting for a toggle-key press
        SEND_RTP,          // (optional cooldown,) then send /rtp <region>
        WAIT_FOR_TELEPORT, // wait for the teleport to land (position change)
        SETTLE,            // wait a few ticks for chunks to start streaming
        SCANNING,          // scan the BE cache around the landing spot
        BASE_FOUND         // base detected — macro stopped
    }

    private Phase phase = Phase.IDLE;
    private int ticksInPhase = 0;

    /** Ticks since the last /rtp was actually sent — drives the interval. */
    private int ticksSinceSend = 0;

    /** Pre-send cooldown (only used on the teleport-timeout retry path). */
    private int cooldownTicks = 0;

    /** Cursor into {@link #RTP_REGIONS} when RTP Region = Cycle. */
    private int regionIndex = 0;

    /** Region argument used for the current cycle (for the chat alert). */
    private String currentRegion = RTP_REGIONS[0];

    /** Position captured right before /rtp; used to detect the teleport landing. */
    private Vec3d preRtpPos = null;

    /** Toggle key edge detection. */
    private boolean wasKeyDown = false;

    public RtpBaseFinder() {
        super("RtpBaseFinder",
            "Repeats /rtp across DonutSMP regions and passively scans each landing spot's leaked block "
                + "entities for storage containers / spawners — no digging — stopping when a base is found.",
            Category.WORLD);
    }

    @Override
    public void onEnable() {
        resetState();
        wasKeyDown = false;
        // Kick off the macro immediately — the toggle key turns the module on,
        // and enabling it starts the RTP cycle (standard module pattern; glazed
        // also fires its first /rtp on activation).
        phase = Phase.SEND_RTP;
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        phase = Phase.IDLE;
        ticksInPhase = 0;
        ticksSinceSend = 0;
        cooldownTicks = 0;
        regionIndex = 0;
        currentRegion = RTP_REGIONS[0];
        preRtpPos = null;
    }

    @Override
    public void onTickAlways() {
        if (mc.player == null || mc.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = mc.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        // Standard module toggle — the key turns the module on/off, and onEnable
        // kicks off the macro (matching every other Lucid module). Pressing it
        // while running stops the macro via the normal disable path.
        if (isDown && !wasKeyDown) {
            setEnabled(!isEnabled());
        }
        wasKeyDown = isDown;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.currentScreen != null) return; // never act while a GUI is open
        if (phase == Phase.IDLE || phase == Phase.BASE_FOUND) return;

        ClientPlayerEntity player = mc.player;
        ticksInPhase++;
        ticksSinceSend++;

        switch (phase) {
            case SEND_RTP -> tickSendRtp(player);
            case WAIT_FOR_TELEPORT -> tickWaitForTeleport(player);
            case SETTLE -> tickSettle();
            case SCANNING -> tickScanning(player);
            default -> { /* unreachable — handled above */ }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase: SEND_RTP — fire /rtp <region> in chat, then wait for the
    //  teleport. glazed cycles its /rtp direction so successive hops
    //  spread across the map; we cycle the full overworld region list
    //  (or lock to the user's chosen region).
    // ════════════════════════════════════════════════════════════════
    private void tickSendRtp(ClientPlayerEntity player) {
        // Retry cooldown (only set on the teleport-timeout path so a failed
        // hop doesn't hammer the server with back-to-back /rtp commands).
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // Pick the region for this hop.
        String mode = rtpRegion.get();
        if ("Cycle".equals(mode)) {
            currentRegion = RTP_REGIONS[regionIndex % RTP_REGIONS.length];
            regionIndex++;
        } else {
            currentRegion = mode;
        }
        // Region display names map 1:1 onto glazed's /rtp command fragments
        // ("EU Central" → "eu central"). Locale.ROOT guards the Turkish-i
        // lowercasing bug.
        String command = "rtp " + currentRegion.toLowerCase(Locale.ROOT);

        // Capture the position RIGHT BEFORE the teleport so we can detect
        // the landing by a significant position change.
        preRtpPos = posOf(player);
        player.networkHandler.sendChatCommand(command);
        ticksSinceSend = 0;
        phase = Phase.WAIT_FOR_TELEPORT;
        ticksInPhase = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase: WAIT_FOR_TELEPORT — poll for a significant position change
    //  (glazed detects the teleport via the player-position packet; we
    //  use the client-visible position jump instead — same signal).
    // ════════════════════════════════════════════════════════════════
    private void tickWaitForTeleport(ClientPlayerEntity player) {
        // Safety timeout — glazed just re-sends /rtp when its timer expires
        // whether or not the teleport landed; we keep lucid's explicit
        // timeout + warning, then retry shortly after.
        if (ticksInPhase > 200) {

            cooldownTicks = 20;
            phase = Phase.SEND_RTP;
            ticksInPhase = 0;
            return;
        }
        if (preRtpPos == null) {
            preRtpPos = posOf(player);
            return;
        }
        // Detect the teleport: a jump of more than 4 blocks (well beyond
        // ordinary movement) means /rtp landed.
        double dist = posOf(player).distanceTo(preRtpPos);
        if (dist > 4.0) {
            phase = Phase.SETTLE;
            ticksInPhase = 0;
            // Reset fall distance so we don't take fall damage credit for the
            // teleport drop (vanilla usually zeroes this on teleport anyway).
            player.fallDistance = 0f;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase: SETTLE — wait 3 ticks for the server to stream the first
    //  post-teleport chunks into the block-entity cache before scanning.
    // ════════════════════════════════════════════════════════════════
    private void tickSettle() {
        if (ticksInPhase < 3) return;
        phase = Phase.SCANNING;
        ticksInPhase = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase: SCANNING — glazed's no-dig detection. Every tick until the
    //  RTP interval elapses, scan the packet-fed block-entity cache
    //  around the landing spot. If a base is detected, stop; otherwise
    //  hop to the next region. Scanning every tick (rather than once)
    //  catches chunks that stream in late after the teleport.
    // ════════════════════════════════════════════════════════════════
    private void tickScanning(ClientPlayerEntity player) {
        ScanResult result = scanAround(player);

        if (result.spawnerPos != null && criticalSpawner.get()) {
            baseFound(player, result.storageCount, result.spawnerPos);
            return;
        }
        if (result.storageCount >= minStorage.get()) {
            baseFound(player, result.storageCount, null);
            return;
        }

        // No base — keep scanning until the interval elapses, then hop.
        if (ticksSinceSend >= rtpInterval.get() * 20) {

            phase = Phase.SEND_RTP;
            ticksInPhase = 0;
            cooldownTicks = 0;
            preRtpPos = posOf(player);
        }
    }

    /** Base detected — stop the macro, log the evidence, maybe disable. */
    private void baseFound(ClientPlayerEntity player, int storageCount, BlockPos spawnerPos) {
        phase = Phase.BASE_FOUND;
        ticksInPhase = 0;
        BlockPos at = spawnerPos != null ? spawnerPos : player.getBlockPos();
        String where = at.getX() + ", " + at.getY() + ", " + at.getZ();
        String reason = spawnerPos != null
            ? "spawner + " + storageCount + " storage containers"
            : storageCount + " storage containers";

        com.anormal.client.lucid.LucidLog.LOGGER.info("[RtpBaseFinder] Base detected at {} — {}", where, reason);
        // glazed's default reaction to a find is to disconnect the mc
        // (disconnect-on-base-find); lucid's equivalent is stopping the
        // macro, and (by default) disabling the module entirely.
        if (disableOnFind.get()) {
            setEnabled(false);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Chunk scan — glazed's scanChunks on lucid's sus-chunk cache.
    //
    //  glazed scans each chunk the moment its ChunkData packet arrives
    //  (counting storage block entities + spawner blocks). Lucid already
    //  has that exact pipeline: ClientPlayNetworkHandlerMixin feeds every
    //  container/spawner block entity the server leaks into SusChunkCache,
    //  which — unlike WorldChunk.getBlockEntities() — works under
    //  DonutSMP-style anti-xray and survives chunks that render as
    //  obfuscated stone. So we read the cache for all chunks within
    //  Scan Radius of the player and apply glazed's detection rules with
    //  lucid's radius-scoped Min Storage threshold.
    // ════════════════════════════════════════════════════════════════

    /** Evidence gathered by one scan pass. */
    private static final class ScanResult {
        int storageCount = 0;
        BlockPos spawnerPos = null; // first spawner seen, if any
    }

    private ScanResult scanAround(ClientPlayerEntity player) {
        ScanResult result = new ScanResult();
        SusChunkCache cache = SusChunkCache.get();

        double r = scanRadius.get();
        double r2 = r * r;
        int chunkRadius = (int) Math.ceil(r / 16.0);
        int centerChunkX = player.getBlockPos().getX() >> 4;
        int centerChunkZ = player.getBlockPos().getZ() >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                SusChunkCache.ChunkEntry entry = cache.get(cx, cz);
                if (entry == null) continue;
                // The cache is written from the network thread — take the
                // per-entry lock while iterating (same discipline the cache's
                // own read helpers use).
                synchronized (entry) {
                    for (Map.Entry<BlockPos, SusChunkCache.BlockEntityRecord> e
                        : entry.blockEntities.entrySet()) {
                        BlockPos pos = e.getKey();
                        SusChunkCache.BlockEntityRecord record = e.getValue();
                        double dx = pos.getX() + 0.5 - player.getX();
                        double dy = pos.getY() + 0.5 - player.getY();
                        double dz = pos.getZ() + 0.5 - player.getZ();
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        // storageWeightOf > 0 == an actual storage container
                        // (chests/shulkers/barrels/... — spawners, beacons and
                        // enchanting tables are deliberately zeroed so the
                        // Min Storage threshold means real storage blocks).
                        if (SusChunkCache.storageWeightOf(record.type) > 0f) {
                            result.storageCount++;
                        } else if (record.type == BlockEntityType.MOB_SPAWNER
                            && result.spawnerPos == null) {
                            result.spawnerPos = pos;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Build a Vec3d from the player's current position. {@code getPos()} was
     * removed in 1.21.11; the individual x/y/z accessors remain, so we
     * reassemble them here (matches the pattern in StorageESP).
     */
    private static Vec3d posOf(ClientPlayerEntity player) {
        return new Vec3d(player.getX(), player.getY(), player.getZ());
    }
}
