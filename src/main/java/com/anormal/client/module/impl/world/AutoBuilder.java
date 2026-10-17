package com.anormal.client.module.impl.world;

import com.anormal.client.autobuilder.AutoBuilderConfig;
import com.anormal.client.autobuilder.MaterialAnalyzer;
import com.anormal.client.autobuilder.PlacementExecutor;
import com.anormal.client.autobuilder.Schematic;
import com.anormal.client.autobuilder.SchematicIO;
import com.anormal.client.autobuilder.SchematicTransformer;
import com.anormal.client.gui.AutoBuilderScreen;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Loads a schematic, previews it in the world and builds it one legitimate right click at a
 * time.
 *
 * <p>The module itself only owns state and rendering. Parsing lives in
 * {@link com.anormal.client.autobuilder.SchematicIO}, transforms in
 * {@link SchematicTransformer}, materials in {@link MaterialAnalyzer} and the actual placing in
 * {@link PlacementExecutor}, so the GUI never has to duplicate logic.</p>
 */
public class AutoBuilder extends Module {

    public final BooleanSetting ghost = new BooleanSetting("Ghost Preview", "Show the schematic in the world", true);
    public final NumberSetting ghostOpacity = new NumberSetting("Ghost Opacity", "Transparency of the world preview", 120.0, 20.0, 255.0, 5.0);
    public final BooleanSetting showBox = new BooleanSetting("Bounding Box", "Draw the schematic bounds", true);
    public final BooleanSetting showRemaining = new BooleanSetting("Show Remaining", "Highlight blocks that still need placing", false);

    public final NumberSetting blocksPerTick = new NumberSetting("Blocks Per Tick", "Placement budget each tick", 2.0, 1.0, 20.0, 1.0);
    public final NumberSetting delay = new NumberSetting("Place Delay", "Milliseconds between placements", 120.0, 0.0, 2000.0, 10.0);
    public final NumberSetting reach = new NumberSetting("Reach", "Maximum placement distance", 4.5, 1.0, 6.0, 0.1);
    public final BooleanSetting replaceConflicts = new BooleanSetting("Replace Conflicts", "Overwrite different blocks instead of skipping them", false);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing the hand while placing", true);

    private static final int GHOST_BUDGET = 1400;

    private final AutoBuilderConfig config = AutoBuilderConfig.load();

    private Schematic source;
    private Schematic transformed;
    private BlockPos origin;

    private int rotation;
    private boolean mirrorX;
    private boolean mirrorZ;

    private boolean loading;
    private String loadError;
    private String notice;

    public AutoBuilder() {
        super("AutoBuilder", "Load, preview and build schematics block by block", Category.WORLD);
        addSetting(ghost);
        addSetting(ghostOpacity);
        addSetting(showBox);
        addSetting(showRemaining);
        addSetting(blocksPerTick);
        addSetting(delay);
        addSetting(reach);
        addSetting(replaceConflicts);
        addSetting(swing);

        this.rotation = config.rotation;
        this.mirrorX = config.mirrorX;
        this.mirrorZ = config.mirrorZ;
        if (config.originSet) {
            this.origin = new BlockPos(config.originX, config.originY, config.originZ);
        }
    }

    // ------------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onEnable() {
        if (mc.currentScreen == null) {
            mc.setScreen(new AutoBuilderScreen(this));
        }
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen instanceof AutoBuilderScreen) {
            mc.setScreen(null);
        }
        PlacementExecutor.get().cancel();
        saveConfig();
    }

    @Override
    public void onTick() {
        PlacementExecutor.get().tick();
    }

    // ------------------------------------------------------------------
    //  Loading
    // ------------------------------------------------------------------

    public void loadAsync(Path file) {
        if (file == null) return;
        loading = true;
        loadError = null;
        notice = null;

        Thread worker = new Thread(() -> {
            Schematic loaded = null;
            String error = null;
            try {
                loaded = SchematicIO.load(file);
            } catch (SchematicIO.SchematicException e) {
                error = e.getMessage();
            } catch (Throwable t) {
                error = t.getClass().getSimpleName() + ": " + t.getMessage();
            }

            final Schematic result = loaded;
            final String failure = error;

            mc.execute(() -> {
                loading = false;
                if (result != null) {
                    applyLoaded(result, file);
                } else {
                    loadError = failure == null ? "Unknown error" : failure;
                }
            });
        }, "Anormal-AutoBuilder-Load");

        worker.setDaemon(true);
        worker.start();
    }

    private void applyLoaded(Schematic schematic, Path file) {
        this.source = schematic;
        this.transformed = schematic;

        if (origin == null && mc.player != null) {
            origin = mc.player.getBlockPos();
            config.originSet = true;
        }

        config.lastSchematic = file.toAbsolutePath().toString();
        if (config.directory.isEmpty()) {
            Path parent = file.getParent();
            if (parent != null) config.directory = parent.toAbsolutePath().toString();
        }

        rebuild();
        saveConfig();
    }

    /** Recomputes the transformed schematic and hands it to the executor. */
    public void rebuild() {
        if (source == null) {
            transformed = null;
            PlacementExecutor.get().cancel();
            return;
        }
        transformed = SchematicTransformer.apply(source, rotation, mirrorX, mirrorZ);
        if (origin != null) {
            PlacementExecutor.get().replan(transformed, origin);
        }
    }

    // ------------------------------------------------------------------
    //  State accessors used by the GUI
    // ------------------------------------------------------------------

    public Schematic getSource() {
        return source;
    }

    public Schematic getTransformed() {
        return transformed;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getLoadError() {
        return loadError;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }

    public AutoBuilderConfig getConfig() {
        return config;
    }

    public int getRotation() {
        return rotation;
    }

    public boolean isMirrorX() {
        return mirrorX;
    }

    public boolean isMirrorZ() {
        return mirrorZ;
    }

    public void setRotation(int degrees) {
        this.rotation = Math.floorMod((degrees / 90) * 90, 360);
        config.rotation = this.rotation;
        rebuild();
        saveConfig();
    }

    public void setMirror(boolean x, boolean z) {
        this.mirrorX = x;
        this.mirrorZ = z;
        config.mirrorX = x;
        config.mirrorZ = z;
        rebuild();
        saveConfig();
    }

    public void resetTransform() {
        setRotation(0);
        setMirror(false, false);
    }

    public void setOrigin(BlockPos pos) {
        if (pos == null) return;
        this.origin = pos;
        config.originX = pos.getX();
        config.originY = pos.getY();
        config.originZ = pos.getZ();
        config.originSet = true;
        rebuild();
        saveConfig();
    }

    public void nudgeOrigin(int dx, int dy, int dz) {
        if (origin == null) return;
        setOrigin(origin.add(dx, dy, dz));
    }

    public void originAtPlayer() {
        if (mc.player == null) return;
        setOrigin(mc.player.getBlockPos());
    }

    public void originAtCrosshair() {
        if (mc.player == null || mc.world == null) return;
        try {
            var hit = mc.player.raycast(64.0, 0.0f, false);
            if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
                    && hit instanceof net.minecraft.util.hit.BlockHitResult blockHit) {
                setOrigin(blockHit.getBlockPos().offset(blockHit.getSide()));
            } else {
                originAtPlayer();
            }
        } catch (Throwable t) {
            originAtPlayer();
        }
    }

    public MaterialAnalyzer.Result materials() {
        return MaterialAnalyzer.analyze(transformed, origin);
    }

    public PlacementExecutor executor() {
        return PlacementExecutor.get();
    }

    // ------------------------------------------------------------------
    //  Building
    // ------------------------------------------------------------------

    public void startBuild() {
        if (transformed == null || origin == null) {
            notice = "Load a schematic first";
            return;
        }
        PlacementExecutor.Options options = new PlacementExecutor.Options();
        options.blocksPerTick = blocksPerTick.getValue().intValue();
        options.delayMs = delay.getValue().intValue();
        options.reach = reach.getValue();
        options.replaceConflicts = replaceConflicts.isEnabled();
        options.swing = swing.isEnabled();

        PlacementExecutor.get().start(transformed, origin, options);
        notice = PlacementExecutor.get().getStatus();
    }

    public void cancelBuild() {
        PlacementExecutor.get().cancel();
        notice = "Build cancelled";
    }

    public void saveConfig() {
        if (origin != null) {
            config.originX = origin.getX();
            config.originY = origin.getY();
            config.originZ = origin.getZ();
            config.originSet = true;
        }
        config.blocksPerTick = blocksPerTick.getValue().intValue();
        config.delayMs = delay.getValue().intValue();
        config.reach = reach.getValue();
        config.replaceConflicts = replaceConflicts.isEnabled();
        config.swing = swing.isEnabled();
        config.ghost = ghost.isEnabled();
        config.ghostOpacity = ghostOpacity.getValue().intValue();
        config.showBox = showBox.isEnabled();
        config.showRemaining = showRemaining.isEnabled();
        config.save();
    }

    // ------------------------------------------------------------------
    //  World preview
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null) return;

        if (showRemaining.isEnabled()) {
            renderRemaining(context, tickDelta);
        }

        if (!ghost.isEnabled() || transformed == null || origin == null) return;

        int alpha = ghostOpacity.getValue().intValue();
        int fill = ColorUtils.rgba(90, 170, 255, 255);
        int outline = ColorUtils.rgba(140, 210, 255, 255);

        Map<BlockPos, ?> blocks = transformed.blocks;
        int size = blocks.size();
        int stride = Math.max(1, size / GHOST_BUDGET);
        int drawn = 0;
        int index = 0;

        for (BlockPos local : blocks.keySet()) {
            if (index++ % stride != 0) continue;

            BlockPos world = origin.add(local);
            double dx = world.getX() + 0.5 - mc.player.getX();
            double dy = world.getY() + 0.5 - mc.player.getY();
            double dz = world.getZ() + 0.5 - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz > 96 * 96) continue;

            RenderUtils.drawBlockFill(context, world, fill, tickDelta, alpha);
            if (stride == 1) {
                RenderUtils.drawBlockOutline(context, world, outline, tickDelta, 1);
            }
            if (++drawn >= GHOST_BUDGET) break;
        }

        if (showBox.isEnabled()) {
            drawBounds(context, tickDelta);
        }
    }

    private void renderRemaining(DrawContext context, float tickDelta) {
        List<BlockPos> remaining = PlacementExecutor.get().remainingPositions(400);
        int colour = ColorUtils.rgba(255, 190, 60, 255);
        for (BlockPos pos : remaining) {
            double dx = pos.getX() + 0.5 - mc.player.getX();
            double dy = pos.getY() + 0.5 - mc.player.getY();
            double dz = pos.getZ() + 0.5 - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz > 64 * 64) continue;
            RenderUtils.drawBlockFill(context, pos, colour, tickDelta, 70);
        }
    }

    private void drawBounds(DrawContext context, float tickDelta) {
        if (transformed == null || origin == null) return;

        double x0 = origin.getX(), y0 = origin.getY(), z0 = origin.getZ();
        double x1 = x0 + transformed.sizeX, y1 = y0 + transformed.sizeY, z1 = z0 + transformed.sizeZ;

        int[][] corners = new int[8][];
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? x0 : x1;
            double py = (i & 2) == 0 ? y0 : y1;
            double pz = (i & 4) == 0 ? z0 : z1;
            corners[i] = ProjectionUtil.project(new Vec3d(px, py, pz), tickDelta);
        }

        int colour = ColorUtils.rgba(120, 200, 255, 220);
        int[][] edges = {
                {0, 1}, {1, 3}, {3, 2}, {2, 0},
                {4, 5}, {5, 7}, {7, 6}, {6, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        for (int[] edge : edges) {
            int[] a = corners[edge[0]];
            int[] b = corners[edge[1]];
            if (a == null || b == null) continue;
            RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], colour, 1.0f);
        }
    }
}
