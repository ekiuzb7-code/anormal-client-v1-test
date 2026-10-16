package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Locates mob spawners (dungeon and trial) through walls.
 *
 * <p>Three problems made this module unreliable, all fixed here:</p>
 *
 * <p><b>1. Markers appeared very late, or never.</b> The scan used the cubic
 * ellipsoid mode, which precomputes one offset per {@code (x, y, z)} cell -
 * about <b>912 000</b> entries at the default radius. At a 2500 blocks/tick
 * budget a single sweep therefore took roughly <b>18 seconds</b>, and the
 * offset list itself was tens of megabytes. It now uses
 * {@link BlockScanner#scanCylinder}, which only precomputes the 2D circle
 * (~12 800 entries at r=64), and the sweep starts at the player's own Y level
 * and alternates outward, so nearby spawners show up within the first second.</p>
 *
 * <p><b>2. Broken spawners kept showing.</b> Nothing ever re-checked an entry,
 * so a spawner that had been destroyed stayed on screen until you walked far
 * enough to trigger the distance prune. Every tracked position is now
 * re-validated against the world each tick and dropped the moment the block is
 * gone.</p>
 *
 * <p><b>3. Deep spawners were never found.</b> The scan centre's Y was only
 * updated when the player moved horizontally, so digging or flying straight
 * down left the whole search volume behind. The centre now tracks all three
 * axes, and the vertical range is a separate setting so you can search a deep
 * column without inflating the horizontal radius.</p>
 */
public class SpawnerFinder extends Module {

    // ------------------------------------------------------------------
    //  Detection
    // ------------------------------------------------------------------
    public final ModeSetting filter = new ModeSetting("Spawners", "Which mob spawners to find", "All", "All", "Dungeon", "Trial");
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 48.0, 8.0, 128.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to scan - raise this to find deep spawners", 48.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick - higher finds spawners sooner but costs more CPU", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the scan restarts around you", 8.0, 4.0, 32.0, 2.0);
    public final BooleanSetting purgeDestroyed = new BooleanSetting("Purge Destroyed", "Remove a marker as soon as the spawner is gone", true);

    // ------------------------------------------------------------------
    //  Overlay
    // ------------------------------------------------------------------
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Shows distance to the spawner on the overlay", true);
    public final BooleanSetting showMob = new BooleanSetting("Show Mob", "Shows spawner kind (Dungeon/Trial)", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coords", "Show the spawner coordinates", false);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip markers further away than this (0 = no limit)", 0.0, 0.0, 256.0, 8.0);
    public final ColorSetting color = new ColorSetting("Color", "Spawner overlay color", ColorUtils.rgba(255, 60, 220, 255));

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a 3D block outline", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Draw a translucent fill", false);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Opacity of the fill", 90.0, 0.0, 255.0, 5.0);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from the crosshair", false);
    public final NumberSetting maxRendered = new NumberSetting("Max Rendered", "Max spawners drawn at once", 150.0, 10.0, 500.0, 10.0);

    // ------------------------------------------------------------------
    //  Alerts
    // ------------------------------------------------------------------
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Announce a newly found spawner in chat", false);
    public final BooleanSetting notifySound = new BooleanSetting("Notify Sound", "Play a click when a new spawner is found", false);

    private static final long RENDER_BUDGET_NS = 5_000_000L;

    private final Map<BlockPos, String> found = new HashMap<>();
    private final Set<BlockPos> announced = new HashSet<>();
    private final BlockScanner scanner = new BlockScanner();

    private int centreX = Integer.MIN_VALUE, centreY, centreZ;
    private int scanned = 0;

    public SpawnerFinder() {
        super("SpawnerFinder", "Locates and highlights mob spawners through walls", Category.RENDER);
        addSetting(filter);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(purgeDestroyed);
        addSetting(showDistance);
        addSetting(showMob);
        addSetting(showCoords);
        addSetting(maxDistance);
        addSetting(color);
        addSetting(outline);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(outlineWidth);
        addSetting(tracer);
        addSetting(maxRendered);
        addSetting(chatLog);
        addSetting(notifySound);
    }

    @Override
    public void onDisable() {
        found.clear();
        announced.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
        scanned = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();
        double recentre = recentreDistance.getValue();

        // Re-centre on ANY axis change - digging straight down used to leave the
        // whole search volume behind, which is why deep spawners were missed.
        if (centreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - centreX) > recentre
                || Math.abs(origin.getY() - centreY) > recentre
                || Math.abs(origin.getZ() - centreZ) > recentre) {
            centreX = origin.getX();
            centreY = origin.getY();
            centreZ = origin.getZ();
            scanner.resetCylinder();
            prune(origin, r, vr);
        }

        // Re-validate everything we are currently showing, so a spawner that has
        // been broken disappears immediately instead of lingering as a ghost.
        if (purgeDestroyed.isEnabled()) {
            found.entrySet().removeIf(e -> {
                BlockPos p = e.getKey();
                if (!mc.world.isChunkLoaded(p)) return false;
                BlockState state = mc.world.getBlockState(p);
                String kind = kindOf(state.getBlock());
                return kind == null || !kind.equals(e.getValue());
            });
        }

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr,
                    scanSpeed.getValue().intValue(), scratch,
                    state -> kindOf(state.getBlock()) != null,
                    (pos, state) -> {
                        String kind = kindOf(state.getBlock());
                        if (kind == null) return;
                        BlockPos immutable = pos.toImmutable();
                        boolean isNew = found.put(immutable, kind) == null;
                        scanned++;
                        if (isNew) announce(immutable, kind);
                    });
        } catch (Throwable ignored) {
        }
    }

    private void announce(BlockPos pos, String kind) {
        if (!announced.add(pos)) return;
        if (announced.size() > 512) announced.clear();

        if (notifySound.isEnabled()) {
            try {
                mc.player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.8f);
            } catch (Throwable ignored) {
            }
        }
        if (chatLog.isEnabled() && mc.inGameHud != null) {
            try {
                mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                        String.format("§d[Spawner] §f%s at §7%d %d %d",
                                kind, pos.getX(), pos.getY(), pos.getZ())));
            } catch (Throwable ignored) {
            }
        }
    }

    private void prune(BlockPos origin, int r, int vr) {
        int rr = r * r;
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > vr + 2;
        });
    }

    /** @return "Dungeon" / "Trial", or null when the block is not a wanted spawner. */
    private String kindOf(Block block) {
        if (block == Blocks.SPAWNER) {
            return filter.is("Trial") ? null : "Dungeon";
        }
        if (block == Blocks.TRIAL_SPAWNER) {
            return filter.is("Dungeon") ? null : "Trial";
        }
        // Fallback for any modded/renamed spawner block.
        try {
            String path = Registries.BLOCK.getId(block).getPath();
            if (!path.contains("spawner")) return null;
            if (filter.is("Dungeon") && !path.equals("spawner")) return null;
            if (filter.is("Trial") && !path.contains("trial")) return null;
            return path.contains("trial") ? "Trial" : "Dungeon";
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;

        int c = color.getValue();
        int limit = maxRendered.getValue().intValue();
        int drawn = 0;
        int alpha = fillOpacity.getValue().intValue();
        int width = outlineWidth.getValue().intValue();
        double cull = maxDistance.getValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        try {
            for (Map.Entry<BlockPos, String> e : found.entrySet()) {
                if (drawn >= limit) break;
                if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

                BlockPos p = e.getKey();

                // Off-screen cull before emitting geometry.
                if (RenderUtils.projectBlockBounds(p, tickDelta) == null) continue;

                double dist = Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos()));
                if (cull > 0 && dist > cull) continue;
                drawn++;

                if (fill.isEnabled()) RenderUtils.drawBlockFill(context, p, c, tickDelta, alpha);
                if (outline.isEnabled()) RenderUtils.drawBlockOutline(context, p, c, tickDelta, width);

                if (mc.textRenderer == null) continue;

                int[] sc = ProjectionUtil.project(
                        new Vec3d(p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5), tickDelta);
                if (sc == null) continue;

                if (tracer.isEnabled()) {
                    RenderUtils.drawLine(context, w / 2, h / 2, sc[0], sc[1], c, 1.0f);
                }
                if (showDistance.isEnabled()) {
                    RenderUtils.drawText(context, mc.textRenderer, (int) dist + "m",
                            sc[0] + 3, sc[1] - 4, c, true);
                }
                if (showMob.isEnabled()) {
                    String kind = e.getValue();
                    String label = kind.equals("Trial") ? "Trial Spawner" : "Dungeon";
                    RenderUtils.drawText(context, mc.textRenderer, "§e" + label,
                            sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] + 8, c, true);
                }
                if (showCoords.isEnabled()) {
                    String coords = p.getX() + " " + p.getY() + " " + p.getZ();
                    RenderUtils.drawText(context, mc.textRenderer, "§7" + coords,
                            sc[0] - mc.textRenderer.getWidth(coords) / 2, sc[1] + 18, c, true);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
