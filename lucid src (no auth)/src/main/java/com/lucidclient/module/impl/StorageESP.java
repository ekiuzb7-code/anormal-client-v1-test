package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.ui.util.WorldRender3D;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * StorageESP — highlights nearby storage block entities with colored
 * FILLED boxes (Argon style), rendered in 3D world space through walls.
 *
 * ── Chunk-based scan ─────────────────────────────────────────────
 * ⚠ ClientWorld.getBlockEntities() returns an EMPTY set in production
 * (it's only populated in dev). So we must iterate over loaded CHUNKS
 * and ask each chunk for its block entity map via getBlockEntities().
 *
 * ── Render ───────────────────────────────────────────────────────
 * Each storage block is drawn as a solid translucent 3D box on the
 * no-depth quad layer (visible through terrain, from any angle) — no
 * outline, no label. The per-type Color settings supply the hue and the
 * Alpha setting (Argon's, default 125) supplies the opacity.
 *
 * Settings:
 *   Range, Alpha, Show Chests, Show Shulkers, Show Others,
 *   Chest Color, EnderChest Color, Shulker Color, Trapped Color, Other Color.
 */
public class StorageESP extends Module {

    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Scan radius (blocks).", 8f, 128f, 48f, 0));
    private final IntSetting alpha = addSetting(
        new IntSetting("Alpha", "Fill opacity of the boxes (0-255). Argon default is 125.", 1, 255, 125, 1));
    private final BooleanSetting showChests = addSetting(
        new BooleanSetting("Show Chests", "Highlight chests.", true));
    private final BooleanSetting showShulkers = addSetting(
        new BooleanSetting("Show Shulkers", "Highlight shulker boxes.", true));
    private final BooleanSetting showOthers = addSetting(
        new BooleanSetting("Show Others", "Highlight barrels, hoppers, dispensers, droppers.", true));
    private final ColorSetting chestColor = addSetting(
        new ColorSetting("Chest Color", "Color for chests. Click to cycle.", 0xFFF59E0B));
    private final ColorSetting enderChestColor = addSetting(
        new ColorSetting("EnderChest Color", "Color for ender chests. Click to cycle.", 0xFF3B82F6));
    private final ColorSetting shulkerColor = addSetting(
        new ColorSetting("Shulker Color", "Color for shulker boxes. Click to cycle.", 0xFFEC4899));
    private final ColorSetting trappedColor = addSetting(
        new ColorSetting("Trapped Color", "Color for trapped chests. Click to cycle.", 0xFFEF4444));
    private final ColorSetting otherColor = addSetting(
        new ColorSetting("Other Color", "Color for barrels / hoppers / dispensers / droppers. Click to cycle.", 0xFF06B6D4));

    // Cached list of (blockPos, color) tuples updated each tick.
    private final List<StorageEntry> cached = new ArrayList<>();

    private record StorageEntry(BlockPos pos, int color) {}

    public StorageESP() {
        super("StorageESP", "Highlights nearby storage with colored filled boxes (chests, shulkers, etc.).",
            Category.RENDER);
    }

    @Override
    protected void onEnable() { cached.clear(); }
    @Override
    protected void onDisable() { cached.clear(); }

    // ── Tick: scan loaded chunks for storage BEs (20Hz) ─────
    @Override
    public void onTick(MinecraftClient client) {
        cached.clear();
        if (client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        double r = range.get();
        double r2 = r * r;
        int chunkRadius = (int) Math.ceil(r / 16.0);
        int centerChunkX = player.getBlockPos().getX() >> 4;
        int centerChunkZ = player.getBlockPos().getZ() >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                WorldChunk chunk = world.getChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getPos();
                    double dx = pos.getX() + 0.5 - player.getX();
                    double dy = pos.getY() + 0.5 - player.getY();
                    double dz = pos.getZ() + 0.5 - player.getZ();
                    if (dx * dx + dy * dy + dz * dz > r2) continue;

                    StorageEntry entry = classify(be.getType(), pos);
                    if (entry != null) cached.add(entry);
                }
            }
        }
    }

    /** Maps a block-entity type to its color using the user's settings. */
    private StorageEntry classify(BlockEntityType<?> type, BlockPos pos) {
        if (type == BlockEntityType.CHEST) {
            return showChests.isEnabled() ? new StorageEntry(pos, chestColor.getColor()) : null;
        }
        if (type == BlockEntityType.TRAPPED_CHEST) {
            return showChests.isEnabled() ? new StorageEntry(pos, trappedColor.getColor()) : null;
        }
        if (type == BlockEntityType.ENDER_CHEST) {
            return showChests.isEnabled() ? new StorageEntry(pos, enderChestColor.getColor()) : null;
        }
        if (type == BlockEntityType.SHULKER_BOX) {
            return showShulkers.isEnabled() ? new StorageEntry(pos, shulkerColor.getColor()) : null;
        }
        if (type == BlockEntityType.BARREL
            || type == BlockEntityType.HOPPER
            || type == BlockEntityType.DISPENSER
            || type == BlockEntityType.DROPPER) {
            if (!showOthers.isEnabled()) return null;
            return new StorageEntry(pos, otherColor.getColor());
        }
        return null;
    }

    // ── World render: solid translucent filled boxes (Argon style) ──
    public void renderWorld(WorldRenderContext wrc) {
        if (cached.isEmpty()) return;
        if (wrc.gameRenderer() == null) return;
        var camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;

        var matrices = wrc.matrices();
        var consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int a = alpha.get();

        WorldRender3D.begin(matrices, camera);
        try {
            for (StorageEntry entry : cached) {
                BlockPos pos = entry.pos();
                int color = (a << 24) | (entry.color() & 0x00FFFFFF);
                WorldRender3D.drawFilledBoxExactAlpha(consumers,
                    new Box(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0),
                    color);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }
}
