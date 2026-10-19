package com.anormal.client.gui;

import com.anormal.client.autobuilder.AutoBuilderConfig;
import com.anormal.client.autobuilder.GhostShell;
import com.anormal.client.autobuilder.MaterialAnalyzer;
import com.anormal.client.autobuilder.PlacementExecutor;
import com.anormal.client.autobuilder.Schematic;
import com.anormal.client.module.impl.world.AutoBuilder;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Main AutoBuilder screen: schematic library on the left, an orbitable voxel preview on the
 * right, transform and position controls underneath, and a build footer.
 */
public class AutoBuilderScreen extends Screen {

    private static final String[] EXTENSIONS = {".schem", ".schematic", ".litematic"};

    private final AutoBuilder module;
    private final AutoBuilderConfig config;

    private final List<Path> files = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();

    private Path selected;
    private String search = "";
    private boolean searchFocused;
    private int sortMode;

    private int listScroll;

    private float camYaw;
    private float camPitch;
    private float camZoom;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    private int editingAxis = -1;
    private String editBuffer = "";

    private MaterialAnalyzer.Result cachedMaterials;
    private long materialsStamp;

    private static final class Button {
        final int x, y, w, h;
        final String label;
        final Runnable action;
        final boolean active;
        final boolean primary;

        Button(int x, int y, int w, int h, String label, Runnable action, boolean active, boolean primary) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.action = action;
            this.active = active;
            this.primary = primary;
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public AutoBuilderScreen(AutoBuilder module) {
        super(Text.literal("AutoBuilder"));
        this.module = module;
        this.config = module.getConfig();
        this.camYaw = config.cameraYaw;
        this.camPitch = config.cameraPitch;
        this.camZoom = config.cameraZoom;
        refreshList();
    }

    @Override
    protected void init() {
        refreshList();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    //  Library
    // ------------------------------------------------------------------

    private Path directory() {
        if (config.directory != null && !config.directory.isEmpty()) {
            return Path.of(config.directory);
        }
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    private void refreshList() {
        files.clear();
        Path dir = directory();
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile).filter(this::isSchematic).forEach(files::add);
            }
        } catch (Throwable ignored) {
        }
        sortFiles();
    }

    private boolean isSchematic(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }

    private void sortFiles() {
        Comparator<Path> comparator = switch (sortMode) {
            case 1 -> Comparator.comparing(p -> extensionOf(p));
            case 2 -> Comparator.comparingLong(this::lastModified).reversed();
            default -> Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT));
        };
        files.sort(comparator);
    }

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sizeOf(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) return bytes + "B";
            if (bytes < 1024 * 1024) return (bytes / 1024) + "K";
            return String.format(Locale.ROOT, "%.1fM", bytes / 1048576.0);
        } catch (Throwable t) {
            return "?";
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private List<Path> filteredFiles() {
        if (search.isEmpty()) return files;
        String query = search.toLowerCase(Locale.ROOT);
        List<Path> out = new ArrayList<>();
        for (Path path : files) {
            if (path.getFileName().toString().toLowerCase(Locale.ROOT).contains(query)) out.add(path);
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        buttons.clear();

        int panelW = Math.min(760, width - 24);
        int panelH = Math.min(440, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;

        ThemeManager.renderWindow(context, panelX, panelY, panelW, panelH, "AutoBuilder");

        int leftW = 186;
        int footerH = 46;
        int leftX = panelX + 8;
        int leftY = panelY + 22;
        int leftH = panelH - 22 - footerH - 6;

        drawLibrary(context, leftX, leftY, leftW, leftH, mouseX, mouseY);

        int rightX = leftX + leftW + 8;
        int rightW = panelX + panelW - 8 - rightX;
        drawPreview(context, rightX, leftY, rightW, leftH, mouseX, mouseY);

        drawFooter(context, panelX, panelY + panelH - footerH - 2, panelW, footerH, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLibrary(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        RenderUtils.fill(context, x, y, x + w, y + h, ColorUtils.rgba(12, 14, 20, 210));
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1, ThemeManager.getBorderColor());

        RenderUtils.drawText(context, textRenderer, "§7Library", x + 6, y + 5, 0xFFCCCCCC, false);

        Path dir = directory();
        String folder = dir == null ? "?" : dir.getFileName() == null ? dir.toString() : dir.getFileName().toString();
        RenderUtils.drawText(context, textRenderer, "§8" + trim(folder, 26), x + 6, y + 16, 0xFF888888, false);

        int by = y + 28;
        addButton(context, x + 6, by, 56, 14, "Open", () -> {
            try {
                Util.getOperatingSystem().open(directory().toFile());
            } catch (Throwable ignored) {
            }
        }, false, false, mouseX, mouseY);

        addButton(context, x + 66, by, 50, 14, "Refresh", () -> {
            refreshList();
            module.setNotice("Rescanned " + files.size() + " files");
        }, false, false, mouseX, mouseY);

        addButton(context, x + 120, by, 60, 14, sortLabel(), () -> {
            sortMode = (sortMode + 1) % 3;
            sortFiles();
        }, false, false, mouseX, mouseY);

        int sy = by + 18;
        RenderUtils.fill(context, x + 6, sy, x + w - 6, sy + 14, ColorUtils.rgba(8, 9, 13, 220));
        RenderUtils.drawBorder(context, x + 6, sy, x + w - 6, sy + 14, 1,
                searchFocused ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());
        String shown = search.isEmpty() ? (searchFocused ? "§f|" : "§7Search...") : search + (searchFocused ? "§7|" : "");
        RenderUtils.drawText(context, textRenderer, shown, x + 10, sy + 3, 0xFFDDDDDD, false);

        int listY = sy + 18;
        int listH = y + h - listY - 6;

        List<Path> visible = filteredFiles();
        int rowH = 18;
        int maxRows = Math.max(1, listH / rowH);
        int maxScroll = Math.max(0, visible.size() - maxRows);
        listScroll = Math.max(0, Math.min(maxScroll, listScroll));

        try {
            context.enableScissor(x + 2, listY, x + w - 2, listY + listH);
            for (int i = 0; i < visible.size(); i++) {
                int ry = listY + i * rowH - listScroll * rowH;
                if (ry + rowH < listY || ry > listY + listH) continue;

                Path path = visible.get(i);
                boolean isSelected = path.equals(selected);
                boolean hovered = mouseX >= x + 6 && mouseX <= x + w - 6 && mouseY >= ry && mouseY <= ry + rowH - 2;

                if (isSelected) {
                    RenderUtils.fill(context, x + 6, ry, x + w - 6, ry + rowH - 2, ColorUtils.rgba(40, 60, 100, 220));
                    RenderUtils.fill(context, x + 6, ry, x + 9, ry + rowH - 2, ThemeManager.getAccentColor());
                } else if (hovered) {
                    RenderUtils.fill(context, x + 6, ry, x + w - 6, ry + rowH - 2, ColorUtils.rgba(28, 32, 44, 200));
                }

                String name = trim(path.getFileName().toString(), 20);
                RenderUtils.drawText(context, textRenderer, name, x + 9, ry + 4, 0xFFDDDDDD, false);

                String badge = extensionOf(path).toUpperCase(Locale.ROOT) + " " + sizeOf(path);
                int bw = textRenderer.getWidth(badge);
                RenderUtils.drawText(context, textRenderer, "§8" + badge, x + w - bw - 9, ry + 4, 0xFF777777, false);
            }
            context.disableScissor();
        } catch (Throwable ignored) {
        }

        if (visible.size() > maxRows) {
            int trackX = x + w - 4;
            int thumbH = Math.max(12, listH * maxRows / visible.size());
            int thumbY = listY + (int) ((listH - thumbH) * (listScroll / (float) Math.max(1, maxScroll)));
            RenderUtils.fill(context, trackX, listY, trackX + 2, listY + listH, ColorUtils.rgba(20, 22, 30, 200));
            RenderUtils.fill(context, trackX, thumbY, trackX + 2, thumbY + thumbH, ThemeManager.getAccentColor());
        }

        if (visible.isEmpty()) {
            RenderUtils.drawText(context, textRenderer, "§8No schematics found", x + 10, listY + 6, 0xFF666666, false);
        }
    }

    private String sortLabel() {
        return switch (sortMode) {
            case 1 -> "Type";
            case 2 -> "Date";
            default -> "Name";
        };
    }

    private void drawPreview(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        int infoH = 14;
        int controlsH = 74;
        int previewH = Math.max(80, h - infoH - controlsH - 4);

        RenderUtils.fill(context, x, y, x + w, y + previewH, ColorUtils.rgba(24, 28, 38, 235));
        RenderUtils.drawBorder(context, x, y, x + w, y + previewH, 1, ThemeManager.getBorderColor());

        Schematic schematic = module.getTransformed();
        if (schematic == null) {
            String message = module.isLoading() ? "§7Loading..."
                    : module.getLoadError() != null ? "§c" + trim(module.getLoadError(), 40)
                    : "§8Select a schematic on the left";
            RenderUtils.drawText(context, textRenderer, message, x + 8, y + previewH / 2 - 4, 0xFF888888, false);
        } else {
            renderVoxelPreview(context, x, y, w, previewH, schematic);
        }

        // reset view button
        addButton(context, x + w - 58, y + 4, 54, 12, "Reset view", () -> {
            camYaw = 35f;
            camPitch = 25f;
            camZoom = 1f;
        }, false, false, mouseX, mouseY);

        int iy = y + previewH + 3;
        if (schematic != null) {
            String info = "§7" + schematic.sizeX + "×" + schematic.sizeY + "×" + schematic.sizeZ
                    + "  §8|  §7" + schematic.blockCount() + " blocks"
                    + "  §8|  §7" + schematic.regionCount + " region" + (schematic.regionCount == 1 ? "" : "s")
                    + "  §8|  §7" + schematic.format;
            RenderUtils.drawText(context, textRenderer, info, x + 2, iy, 0xFFAAAAAA, false);
        }

        int cy = iy + 14;
        drawPositionControls(context, x, cy, w, mouseX, mouseY);
        drawTransformControls(context, x, cy + 38, w, mouseX, mouseY);
    }

    private void renderVoxelPreview(DrawContext context, int x, int y, int w, int h, Schematic schematic) {
        // The shell is built once on a worker thread. Iterating the schematic's HashMap every frame
        // (the old code) is what made big schematics lag and the preview look randomly sparse.
        GhostShell shell = module.getShell();
        if (shell == null) {
            RenderUtils.drawText(context, textRenderer, "§7Preparing preview...", x + 8, y + h / 2 - 4, 0xFF888888, false);
            return;
        }

        int maxDim = Math.max(1, Math.max(schematic.sizeX, Math.max(schematic.sizeY, schematic.sizeZ)));
        double centerX = x + w / 2.0;
        double centerY = y + h / 2.0;
        double camDist = maxDim * 2.2 + 8.0;
        double baseScale = Math.min(w, h) / (maxDim * 1.45 + 2.0);

        double yawRad = Math.toRadians(camYaw);
        double pitchRad = Math.toRadians(camPitch);
        double cosY = Math.cos(yawRad), sinY = Math.sin(yawRad);
        double cosP = Math.cos(pitchRad), sinP = Math.sin(pitchRad);

        double halfX = schematic.sizeX / 2.0;
        double halfY = schematic.sizeY / 2.0;
        double halfZ = schematic.sizeZ / 2.0;

        CAM_ZOOM_HOLDER[0] = camZoom;
        int count = shell.count;
        int stride = Math.max(1, count / 9000);

        record Point(double sx, double sy, double depth, int colour, double size) {
        }
        List<Point> points = new ArrayList<>(Math.min(count, 6200));

        for (int i = 0; i < count; i += stride) {
            double px = shell.x[i] + 0.5 - halfX;
            double py = shell.y[i] + 0.5 - halfY;
            double pz = shell.z[i] + 0.5 - halfZ;

            double x1 = px * cosY + pz * sinY;
            double z1 = -px * sinY + pz * cosY;
            double y1 = py * cosP - z1 * sinP;
            double z2 = py * sinP + z1 * cosP;

            double depth = z2 + camDist;
            if (depth < 0.6) continue;

            double scale = baseScale * camZoom * camDist / depth;
            double sx = centerX + x1 * scale;
            double sy = centerY - y1 * scale;

            if (sx < x - 8 || sx > x + w + 8 || sy < y - 8 || sy > y + h + 8) continue;

            int colour = shell.rgb[i];
            // Brighten instead of darken: map colours are already mid-tone and the old 0.65 floor
            // made the whole build look almost black against the dark viewport.
            double shade = 0.92 + 0.30 * Math.min(1.0, (y1 + halfY) / Math.max(1.0, schematic.sizeY));
            colour = ColorUtils.rgba(
                    (int) Math.min(255, ((colour >> 16) & 0xFF) * shade),
                    (int) Math.min(255, ((colour >> 8) & 0xFF) * shade),
                    (int) Math.min(255, (colour & 0xFF) * shade),
                    255);

            // Exact projected block size. Inflating it by sqrt(stride) was what made the preview
            // look like one blurry smear instead of individual blocks.
            points.add(new Point(sx, sy, depth, colour, Math.max(2.0, scale)));
        }

        points.sort((a, b) -> Double.compare(b.depth, a.depth));

        try {
            context.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);

            // floor grid under the build, so orientation and height are easy to read
            double fy = -halfY;
            int gridCol = ColorUtils.rgba(70, 90, 130, 150);
            double[][] corner = {
                    project(-halfX, fy, -halfZ, cosY, sinY, cosP, sinP, camDist, baseScale, centerX, centerY),
                    project(halfX, fy, -halfZ, cosY, sinY, cosP, sinP, camDist, baseScale, centerX, centerY),
                    project(halfX, fy, halfZ, cosY, sinY, cosP, sinP, camDist, baseScale, centerX, centerY),
                    project(-halfX, fy, halfZ, cosY, sinY, cosP, sinP, camDist, baseScale, centerX, centerY)};
            for (int i = 0; i < 4; i++) {
                double[] a = corner[i], b = corner[(i + 1) % 4];
                if (a == null || b == null) continue;
                RenderUtils.drawLine(context, (int) a[0], (int) a[1], (int) b[0], (int) b[1], gridCol, 1.0f);
            }

            for (Point point : points) {
                int half = Math.max(1, (int) Math.round(point.size / 2.0));
                int px = (int) Math.round(point.sx);
                int py = (int) Math.round(point.sy);

                // A darker 1px frame around every block keeps the edges crisp, which is what makes
                // the preview readable; the block colour is drawn inset by one pixel on top.
                context.fill(px - half, py - half, px + half, py + half, darken(point.colour, 0.55f));
                if (half >= 2) {
                    context.fill(px - half + 1, py - half + 1, px + half - 1, py + half - 1, point.colour);
                }

                if (half >= 3) {
                    // small top highlight so cube tops read as lit faces
                    context.fill(px - half + 1, py - half + 1, px + half - 1, py - half + 2, 0x30FFFFFF);
                }
            }
            context.disableScissor();
        } catch (Throwable ignored) {
        }

        RenderUtils.drawText(context, textRenderer, "§8Drag: rotate  |  Scroll: zoom", x + 6, y + h - 12, 0xFF666666, false);
        RenderUtils.drawText(context, textRenderer,
                "§7" + schematic.sizeX + " §8x §7" + schematic.sizeY + " §8x §7" + schematic.sizeZ,
                x + 6, y + 6, 0xFFAAAAAA, false);
    }

    private static double[] project(double px, double py, double pz, double cosY, double sinY,
                                    double cosP, double sinP, double camDist, double baseScale,
                                    double centerX, double centerY) {
        double x1 = px * cosY + pz * sinY;
        double z1 = -px * sinY + pz * cosY;
        double y1 = py * cosP - z1 * sinP;
        double z2 = py * sinP + z1 * cosP;
        double depth = z2 + camDist;
        if (depth < 0.6) return null;
        double k = baseScale * camDist / depth * CAM_ZOOM_HOLDER[0];
        return new double[]{centerX + x1 * k, centerY - y1 * k};
    }

    /** camZoom handed to the static projector. */
    private static final float[] CAM_ZOOM_HOLDER = {1f};

    private int previewColour(BlockState state) {
        try {
            var mapColor = state.getBlock().getDefaultMapColor();
            if (mapColor != null) {
                return 0xFF000000 | mapColor.color;
            }
        } catch (Throwable ignored) {
        }
        int hash = state.getBlock().hashCode();
        return 0xFF000000 | (hash & 0x7F7F7F);
    }

    /** Multiplies the RGB channels, keeping alpha. Used for the per-block edge frame. */
    private static int darken(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void drawPositionControls(DrawContext context, int x, int y, int w, int mouseX, int mouseY) {
        BlockPos origin = module.getOrigin();

        RenderUtils.drawText(context, textRenderer, "§7Position", x + 2, y + 2, 0xFFCCCCCC, false);

        int fieldW = 54;
        int startX = x + 54;
        String[] labels = {"X", "Y", "Z"};
        int[] values = origin == null ? new int[]{0, 0, 0} : new int[]{origin.getX(), origin.getY(), origin.getZ()};

        for (int axis = 0; axis < 3; axis++) {
            final int axisIndex = axis;
            int fx = startX + axis * (fieldW + 20);

            RenderUtils.drawText(context, textRenderer, "§8" + labels[axis], fx - 8, y + 9, 0xFF888888, false);

            boolean focused = editingAxis == axis;
            String text = focused ? editBuffer + "§7|" : String.valueOf(values[axis]);

            RenderUtils.fill(context, fx, y + 2, fx + fieldW, y + 16, ColorUtils.rgba(8, 9, 13, 220));
            RenderUtils.drawBorder(context, fx, y + 2, fx + fieldW, y + 16, 1,
                    focused ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());
            RenderUtils.drawText(context, textRenderer, text, fx + 4, y + 5, 0xFFEEEEEE, false);

            addButton(context, fx + fieldW + 1, y + 2, 8, 7, "+", () -> nudge(axisIndex, 1), false, false, mouseX, mouseY);
            addButton(context, fx + fieldW + 1, y + 9, 8, 7, "-", () -> nudge(axisIndex, -1), false, false, mouseX, mouseY);
        }

        int by = y + 18;
        addButton(context, x + 2, by, 58, 13, "At player", module::originAtPlayer, false, false, mouseX, mouseY);
        addButton(context, x + 64, by, 58, 13, "Crosshair", module::originAtCrosshair, false, false, mouseX, mouseY);
        addButton(context, x + 126, by, 46, 13, "Reset", () -> module.setOrigin(BlockPos.ORIGIN), false, false, mouseX, mouseY);
    }

    private void drawTransformControls(DrawContext context, int x, int y, int w, int mouseX, int mouseY) {
        RenderUtils.drawText(context, textRenderer, "§7Transform", x + 2, y + 2, 0xFFCCCCCC, false);

        int bx = x + 60;
        int[] degrees = {0, 90, 180, 270};
        for (int i = 0; i < degrees.length; i++) {
            int value = degrees[i];
            addButton(context, bx + i * 30, y, 28, 14, value + "°",
                    () -> module.setRotation(value),
                    module.getRotation() == value, false, mouseX, mouseY);
        }

        int mx = bx + 4 * 30 + 8;
        addButton(context, mx, y, 30, 14, "Mir X", () -> module.setMirror(!module.isMirrorX(), module.isMirrorZ()),
                module.isMirrorX(), false, mouseX, mouseY);
        addButton(context, mx + 32, y, 30, 14, "Mir Z", () -> module.setMirror(module.isMirrorX(), !module.isMirrorZ()),
                module.isMirrorZ(), false, mouseX, mouseY);

        addButton(context, x + 2, y + 18, 46, 13, "Reset", module::resetTransform, false, false, mouseX, mouseY);

        // ghost toggles
        int gx = x + 54;
        addButton(context, gx, y + 18, 62, 13, "Show world",
                module.ghost::toggle, module.ghost.isEnabled(), false, mouseX, mouseY);
        addButton(context, gx + 66, y + 18, 50, 13, "Box",
                module.showBox::toggle, module.showBox.isEnabled(), false, mouseX, mouseY);
        addButton(context, gx + 120, y + 18, 62, 13, "Remaining",
                module.showRemaining::toggle, module.showRemaining.isEnabled(), false, mouseX, mouseY);

        RenderUtils.drawText(context, textRenderer, "§7Opacity " + module.ghostOpacity.getValue().intValue(),
                gx + 188, y + 21, 0xFFAAAAAA, false);
        addButton(context, gx + 244, y + 18, 13, 13, "-", () -> module.ghostOpacity.setValue(
                Math.max(20.0, module.ghostOpacity.getValue() - 20.0)), false, false, mouseX, mouseY);
        addButton(context, gx + 260, y + 18, 13, 13, "+", () -> module.ghostOpacity.setValue(
                Math.min(255.0, module.ghostOpacity.getValue() + 20.0)), false, false, mouseX, mouseY);
    }

    private void drawFooter(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
        RenderUtils.fill(context, x + 8, y, x + w - 8, y + h, ColorUtils.rgba(12, 14, 20, 210));
        RenderUtils.drawBorder(context, x + 8, y, x + w - 8, y + h, 1, ThemeManager.getBorderColor());

        PlacementExecutor executor = module.executor();
        int remaining = executor.getRemaining();
        int done = executor.getPlaced();
        int total = done + remaining;
        float planning = executor.getPlanningProgress();

        String status = executor.getStatus();
        if (status == null || status.isEmpty()) {
            status = module.getNotice() != null ? module.getNotice() : "Ready";
        }
        RenderUtils.drawText(context, textRenderer, "§7" + trim(status, 52), x + 14, y + 5, 0xFFCCCCCC, false);

        int barY = y + 17;
        int barW = w - 220;
        RenderUtils.fill(context, x + 14, barY, x + 14 + barW, barY + 6, ColorUtils.rgba(24, 26, 34, 255));
        if (planning >= 0 || total > 0) {
            float fraction = planning >= 0 ? planning : done / (float) total;
            int filled = (int) (barW * fraction);
            RenderUtils.fill(context, x + 14, barY, x + 14 + Math.max(0, Math.min(barW, filled)), barY + 6,
                    planning >= 0 ? ColorUtils.rgba(255, 190, 60, 255) : ColorUtils.rgba(90, 200, 120, 255));
        }
        RenderUtils.drawText(context, textRenderer,
                "§7" + done + " / " + total + " placed   §8|   §7" + remaining + " left"
                        + (total > 0 ? "   §8|   §7" + (int) (100f * done / total) + "%" : ""),
                x + 14, barY + 9, 0xFF999999, false);

        int bx = x + w - 200;
        int by = y + 5;

        boolean running = executor.isRunning();
        boolean paused = executor.getState() == PlacementExecutor.State.PAUSED;

        addButton(context, bx, by, 44, 15, running ? "Pause" : paused ? "Resume" : "Start", () -> {
            if (executor.getState() == PlacementExecutor.State.PLANNING) {
                return; // already preparing the plan
            } else if (executor.isRunning()) {
                executor.pause("Paused by user");
            } else if (paused) {
                executor.resume();
            } else {
                module.startBuild();
            }
        }, false, true, mouseX, mouseY);

        addButton(context, bx + 48, by, 44, 15, "Cancel", module::cancelBuild, false, false, mouseX, mouseY);
        addButton(context, bx + 96, by, 52, 15, "Materials", () -> {
            if (client != null) client.setScreen(new AutoBuilderMaterialsScreen(this, module));
        }, false, false, mouseX, mouseY);

        addButton(context, bx + 152, by, 40, 15, "Reload", () -> {
            if (selected != null) module.loadAsync(selected);
        }, false, false, mouseX, mouseY);

        addButton(context, bx, by + 18, 44, 15, "Skip",
                () -> executor.skipCurrent(), false, false, mouseX, mouseY);
        addButton(context, bx + 48, by + 18, 44, 15, "Reset",
                executor::reset, false, false, mouseX, mouseY);
        addButton(context, bx + 96, by + 18, 52, 15, "Retry",
                executor::retryFailed, false, false, mouseX, mouseY);
    }

    private void addButton(DrawContext context, int x, int y, int w, int h, String label,
                           Runnable action, boolean active, boolean primary, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int background;
        if (primary) {
            background = hovered ? ColorUtils.rgba(70, 100, 170, 240) : ColorUtils.rgba(45, 65, 115, 230);
        } else if (active) {
            background = hovered ? ColorUtils.rgba(60, 90, 150, 240) : ColorUtils.rgba(40, 60, 105, 220);
        } else {
            background = hovered ? ColorUtils.rgba(34, 38, 50, 230) : ColorUtils.rgba(20, 22, 30, 210);
        }

        RenderUtils.fill(context, x, y, x + w, y + h, background);
        RenderUtils.drawBorder(context, x, y, x + w, y + h, 1,
                active || primary ? ThemeManager.getAccentColor() : ThemeManager.getBorderColor());

        int textW = textRenderer.getWidth(label);
        int tx = x + (w - textW) / 2;
        int ty = y + (h - 8) / 2 + 1;
        RenderUtils.drawText(context, textRenderer, label, tx, ty, 0xFFFFFFFF, false);

        buttons.add(new Button(x, y, w, h, label, action, active, primary));
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    private boolean handleClick(double mouseX, double mouseY, int button) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.hit(mouseX, mouseY)) {
                if (editingAxis >= 0) commitEdit();
                b.action.run();
                return true;
            }
        }

        int panelW = Math.min(760, width - 24);
        int panelH = Math.min(440, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int leftX = panelX + 8;
        int leftY = panelY + 22;
        int leftW = 186;
        int leftH = panelH - 22 - 46 - 6;

        // search field
        int sy = leftY + 46;
        if (mouseX >= leftX + 6 && mouseX <= leftX + leftW - 6 && mouseY >= sy && mouseY <= sy + 14) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        // library rows
        int listY = sy + 18;
        int rowH = 18;
        if (mouseX >= leftX + 6 && mouseX <= leftX + leftW - 6 && mouseY >= listY && mouseY <= listY + leftH - 6) {
            int row = (int) ((mouseY - listY) / rowH) + listScroll;
            List<Path> visible = filteredFiles();
            if (row >= 0 && row < visible.size()) {
                selected = visible.get(row);
                module.loadAsync(selected);
                return true;
            }
        }

        // preview drag
        int rightX = leftX + leftW + 8;
        int rightW = panelX + panelW - 8 - rightX;
        int previewH = Math.max(80, leftH - 14 - 74 - 4);
        if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= leftY && mouseY <= leftY + previewH) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        // coordinate fields
        BlockPos origin = module.getOrigin();
        int cy = leftY + previewH + 17;
        int fieldW = 54;
        int startX = rightX + 54;
        for (int axis = 0; axis < 3; axis++) {
            int fx = startX + axis * (fieldW + 20);
            if (mouseX >= fx && mouseX <= fx + fieldW && mouseY >= cy + 2 && mouseY <= cy + 16) {
                editingAxis = axis;
                editBuffer = origin == null ? "0" : String.valueOf(axis == 0 ? origin.getX() : axis == 1 ? origin.getY() : origin.getZ());
                return true;
            }
        }

        if (editingAxis >= 0) commitEdit();
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        return handleDrag(click.x(), click.y());
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return handleDrag(mouseX, mouseY);
    }

    private boolean handleDrag(double mouseX, double mouseY) {
        if (!dragging) return false;
        camYaw += (float) (mouseX - lastMouseX) * 1.2f;
        camPitch += (float) (mouseY - lastMouseY) * 1.2f;
        camPitch = Math.max(-89f, Math.min(89f, camPitch));
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelW = Math.min(760, width - 24);
        int panelH = Math.min(440, height - 24);
        int panelX = (width - panelW) / 2;
        int panelY = (height - panelH) / 2;
        int leftX = panelX + 8;
        int leftW = 186;

        if (mouseX >= leftX && mouseX <= leftX + leftW) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(verticalAmount));
        } else {
            camZoom = (float) Math.max(0.25f, Math.min(6.0f, camZoom * (verticalAmount > 0 ? 1.12 : 0.89)));
        }
        return true;
    }

    private void nudge(int axis, int delta) {
        if (axis == 0) module.nudgeOrigin(delta, 0, 0);
        else if (axis == 1) module.nudgeOrigin(0, delta, 0);
        else module.nudgeOrigin(0, 0, delta);
    }

    private void commitEdit() {
        if (editingAxis < 0) return;
        int value;
        try {
            value = Integer.parseInt(editBuffer.trim());
        } catch (Throwable t) {
            editingAxis = -1;
            editBuffer = "";
            return;
        }

        BlockPos origin = module.getOrigin();
        if (origin == null) origin = BlockPos.ORIGIN;

        BlockPos next = switch (editingAxis) {
            case 0 -> new BlockPos(value, origin.getY(), origin.getZ());
            case 1 -> new BlockPos(origin.getX(), value, origin.getZ());
            default -> new BlockPos(origin.getX(), origin.getY(), value);
        };
        module.setOrigin(next);
        editingAxis = -1;
        editBuffer = "";
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

        if (editingAxis >= 0) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editingAxis = -1;
                editBuffer = "";
                return true;
            }
            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            saveCamera();
            if (client != null) client.setScreen(null);
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String typed = input.asString();
        if (typed.length() != 1) return false;
        char chr = typed.charAt(0);

        if (editingAxis >= 0) {
            if ((chr >= '0' && chr <= '9') || chr == '-') {
                if (editBuffer.length() < 9) editBuffer += chr;
                return true;
            }
            return false;
        }

        if (searchFocused) {
            if (chr >= 32 && chr <= 126) {
                search += chr;
                listScroll = 0;
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        saveCamera();
        super.close();
    }

    private void saveCamera() {
        config.cameraYaw = camYaw;
        config.cameraPitch = camPitch;
        config.cameraZoom = camZoom;
        module.saveConfig();
    }

    private static String trim(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 2)) + "..";
    }
}
