package com.anormal.client.autobuilder2.gui;

import com.anormal.client.autobuilder2.build.BuildEngine;
import com.anormal.client.autobuilder2.build.BuildPlan;
import com.anormal.client.autobuilder2.build.GridPos;
import com.anormal.client.autobuilder2.litematic.LitematicaFile;
import com.anormal.client.autobuilder2.litematic.SchematicPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AutoBuilderV2Screen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT_WHITE = 0xFFE4E4E4;
    private static final int MUTED = 0xFF929292;
    private static final int DIM = 0xFF626262;
    private static final int PANEL = 0xF20D0D0D;
    private static final int PANEL_INNER = 0xFF111111;
    private static final int ROW = 0xFF171717;
    private static final int BORDER = 0xFF303030;
    private static final int BLACK = 0xFF090909;

    private static final int ROW_HEIGHT = 34;

    private final List<Path> files = new ArrayList<>();
    private final Map<Path, SchematicPreview> previews = new HashMap<>();
    private int selectedIndex = -1;
    private int scrollOffset;
    private boolean materialView;
    private boolean dropHover;
    private boolean previewFocus;
    private boolean viewMode;
    private String status = "Ready  ·  drop files to begin";

    private SchematicPreview selectedPreview;
    private SchematicPreview.State observedPreviewState;

    public AutoBuilderV2Screen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        // The queue used to live only in memory, so closing and reopening the screen looked like
        // the file had never been loaded. It is now restored from disk on every open.
        restoreQueue();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------
    //  Queue persistence
    // ------------------------------------------------------------------

    private static final Gson QUEUE_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path queueFile() {
        try {
            Path dir = net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath()
                    .resolve("config/anormal");
            Files.createDirectories(dir);
            return dir.resolve("autobuilder2-queue.json");
        } catch (Throwable t) {
            return null;
        }
    }

    private void saveQueue() {
        Path file = queueFile();
        if (file == null) return;
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (Path path : this.files) array.add(path.toAbsolutePath().toString());
            root.add("files", array);
            Path selected = this.selectedIndex >= 0 && this.selectedIndex < this.files.size()
                    ? this.files.get(this.selectedIndex) : null;
            if (selected != null) root.addProperty("selected", selected.toAbsolutePath().toString());
            Files.write(file, QUEUE_GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    private void restoreQueue() {
        Path file = queueFile();
        if (file == null || !Files.isRegularFile(file)) {
            if (this.files.isEmpty()) this.status = "Ready  ·  drop files to begin";
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();

            JsonElement filesElement = root.get("files");
            if (filesElement != null && filesElement.isJsonArray()) {
                for (JsonElement element : filesElement.getAsJsonArray()) {
                    try {
                        Path path = Path.of(element.getAsString());
                        if (Files.isRegularFile(path) && !this.files.contains(path)) {
                            this.files.add(path);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }

            String wanted = root.has("selected") ? root.get("selected").getAsString() : null;
            int index = -1;
            if (wanted != null) {
                for (int i = 0; i < this.files.size(); i++) {
                    if (this.files.get(i).toAbsolutePath().toString().equals(wanted)) {
                        index = i;
                        break;
                    }
                }
            }
            if (index < 0 && !this.files.isEmpty()) index = 0;
            select(index);

            if (!this.files.isEmpty()) {
                this.status = "Restored  ·  " + this.files.size() + " file"
                        + (this.files.size() == 1 ? "" : "s");
            }
        } catch (Throwable ignored) {
        }
    }

    /** Called from the client tick, so textures are never created inside a render pass. */
    public void tickPreview() {
        SchematicPreview preview = this.selectedPreview;
        SchematicPreview.setOwner(preview);
        if (preview == null) {
            this.observedPreviewState = null;
            return;
        }
        preview.prepare();
        SchematicPreview.State state = preview.state();
        if (state != this.observedPreviewState) {
            this.observedPreviewState = state;
            if (state == SchematicPreview.State.DECODING) {
                this.status = "Decoding schematic…";
            } else if (state == SchematicPreview.State.READY && preview.file() != null) {
                LitematicaFile file = preview.file();
                this.status = "Decoded " + format(file.blockCount) + " blocks  ·  "
                        + file.distinctBlocks() + " types  ·  "
                        + file.width + "×" + file.height + "×" + file.length;
            } else if (state == SchematicPreview.State.FAILED) {
                this.status = "Could not read schematic  ·  " + preview.error();
            }
        }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private static final class Layout {
        int panelX;
        int panelY;
        int panelWidth;
        int panelHeight;
        int right;
        int bottom;
        int contentTop;
        int contentBottom;
        int footerTop;
        int dropX;
        int dropY;
        int dropWidth;
        int dropHeight;
        int dropBottom;
        boolean split;
        boolean paneOnly;
        int listX;
        int listY;
        int listWidth;
        int listHeight;
        int paneX;
        int paneY;
        int paneWidth;
        int paneHeight;
        int buttonMargin;
        int buttonGap;
        int buttonWidth;
        int buttonY;
    }

    private Layout layout() {
        Layout layout = new Layout();
        layout.panelWidth = Math.min(760, Math.max(300, this.width - 32));
        layout.panelHeight = Math.min(470, Math.max(190, this.height - 28));
        layout.panelX = (this.width - layout.panelWidth) / 2;
        layout.panelY = (this.height - layout.panelHeight) / 2;
        layout.right = layout.panelX + layout.panelWidth;
        layout.bottom = layout.panelY + layout.panelHeight;
        layout.footerTop = layout.bottom - 76;
        layout.contentTop = layout.panelY + 76;
        layout.contentBottom = layout.footerTop - 13;
        layout.dropX = layout.panelX + 22;
        layout.dropY = layout.contentTop;
        layout.dropWidth = layout.panelWidth - 44;
        layout.dropHeight = Math.max(58, layout.contentBottom - layout.contentTop);
        layout.dropBottom = layout.dropY + layout.dropHeight;
        layout.buttonMargin = 22;
        layout.buttonGap = 8;
        layout.buttonWidth = (layout.panelWidth - layout.buttonMargin * 2 - layout.buttonGap * 4) / 5;
        layout.buttonY = layout.footerTop + 32;

        boolean hasPreview = !this.files.isEmpty() && this.selectedPreview != null;
        // View mode takes over the whole content area with the schematic, regardless of window size.
        layout.split = hasPreview && !this.viewMode && layout.dropWidth >= 440;
        // On a narrow window (or a high GUI scale) there is no room for both columns, so the list
        // and the preview take turns instead of the preview becoming unreachable.
        layout.paneOnly = hasPreview && (this.viewMode || (!layout.split && this.previewFocus));
        if (layout.split) {
            layout.listWidth = Math.max(176, Math.min(258, (int) (layout.dropWidth * 0.34)));
            layout.listX = layout.dropX;
            layout.listY = layout.dropY;
            layout.listHeight = layout.dropHeight;
            layout.paneX = layout.dropX + layout.listWidth + 12;
            layout.paneWidth = layout.dropWidth - layout.listWidth - 12;
            layout.paneY = layout.dropY;
            layout.paneHeight = layout.dropHeight;
        } else if (layout.paneOnly) {
            layout.paneX = layout.dropX;
            layout.paneY = layout.dropY;
            layout.paneWidth = layout.dropWidth;
            layout.paneHeight = layout.dropHeight;
            layout.listX = layout.dropX;
            layout.listY = layout.dropY;
            layout.listWidth = layout.dropWidth;
            layout.listHeight = layout.dropHeight;
        } else {
            layout.listX = layout.dropX;
            layout.listY = layout.dropY;
            layout.listWidth = layout.dropWidth;
            layout.listHeight = layout.dropHeight;
        }
        return layout;
    }

    private static final int CHIP_HEIGHT = 16;
    private static final int TOGGLE_CHIP_WIDTH = 46;
    private static final int ROTATE_CHIP_WIDTH = 52;
    private static final int FILES_CHIP_WIDTH = 46;

    /** Header chips of the preview pane, right aligned so they never collide with its title. */
    private int paneChipY(Layout layout) {
        return layout.paneY + 4;
    }

    private int paneRotateX(Layout layout) {
        return layout.paneX + layout.paneWidth - 8 - ROTATE_CHIP_WIDTH;
    }

    private int paneToggleX(Layout layout) {
        return paneRotateX(layout) - 6 - TOGGLE_CHIP_WIDTH;
    }

    private int paneFilesX(Layout layout) {
        return paneToggleX(layout) - 6 - FILES_CHIP_WIDTH;
    }

    /** Chip in the file list header that switches a narrow window to the preview. */
    private int listChipX(Layout layout) {
        return layout.listX + layout.listWidth - 10 - FILES_CHIP_WIDTH;
    }

    private static final int BUILD_CHIP_WIDTH = 48;
    private static final int BUILD_CHIP_HEIGHT = 16;

    /** Stop chip in the footer, shown beside the status line while a build is active. */
    private int buildChipX(Layout layout) {
        return layout.right - 22 - BUILD_CHIP_WIDTH;
    }

    private int buildChipY(Layout layout) {
        return layout.footerTop + 5;
    }

    private int[] paneImageRect(Layout layout, int statLines) {
        int headerHeight = 24;
        int statsHeight = Math.min(statLines * 10 + 8, Math.max(0, layout.paneHeight - headerHeight - 44));
        int top = layout.paneY + headerHeight + 4;
        int bottom = layout.paneY + layout.paneHeight - statsHeight - 4;
        return new int[] {layout.paneX + 6, top, layout.paneWidth - 12, Math.max(1, bottom - top)};
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        Layout layout = layout();
        context.fill(0, 0, this.width, this.height, 0xC9000000);

        context.fill(layout.panelX + 4, layout.panelY + 5, layout.right + 4, layout.bottom + 5, 0x60000000);
        context.fill(layout.panelX, layout.panelY, layout.right, layout.bottom, PANEL);
        context.drawStrokedRectangle(layout.panelX, layout.panelY, layout.panelWidth, layout.panelHeight, BORDER);
        context.fill(layout.panelX, layout.panelY, layout.right, layout.panelY + 2, WHITE);

        context.drawText(this.textRenderer, "Auto cc", layout.panelX + 26, layout.panelY + 22, WHITE, false);
        context.drawText(this.textRenderer, "FILE WORKSPACE", layout.panelX + 27, layout.panelY + 41, MUTED, false);

        int badgeWidth = 112;
        int badgeX = layout.right - 25 - badgeWidth;
        int badgeY = layout.panelY + 23;
        context.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + 24, ROW);
        context.drawStrokedRectangle(badgeX, badgeY, badgeWidth, 24, BORDER);
        context.fill(badgeX + 10, badgeY + 10, badgeX + 15, badgeY + 15, WHITE);
        context.drawText(this.textRenderer, "CLIENT CC", badgeX + 22, badgeY + 8, SOFT_WHITE, false);

        context.fill(layout.panelX + 1, layout.panelY + 64, layout.right - 1, layout.panelY + 65, BORDER);

        if (this.files.isEmpty()) {
            renderEmptyState(context, layout);
        } else if (layout.split) {
            renderFileList(context, layout, layout.listX, layout.listY, layout.listWidth, layout.listHeight);
            renderPreviewPane(context, layout);
        } else if (layout.paneOnly) {
            renderPreviewPane(context, layout);
        } else {
            renderFileList(context, layout, layout.listX, layout.listY, layout.listWidth, layout.listHeight);
        }

        context.fill(layout.panelX + 1, layout.footerTop, layout.right - 1, layout.footerTop + 1, BORDER);

        BuildEngine engine = BuildEngine.get();
        boolean buildKnown = engine.state() != BuildEngine.State.IDLE;
        int statusWidth = layout.panelWidth - 134;
        context.drawText(this.textRenderer, truncate(buildKnown ? engine.progress() : this.status, statusWidth),
                layout.panelX + 24, layout.footerTop + 11, MUTED, false);
        if (buildKnown && !engine.note().isEmpty()) {
            context.drawText(this.textRenderer, truncate(engine.note(), statusWidth),
                    layout.panelX + 24, layout.footerTop + 22, DIM, false);
        }
        if (engine.isActive()) {
            drawChip(context, buildChipX(layout), buildChipY(layout), BUILD_CHIP_WIDTH, BUILD_CHIP_HEIGHT,
                    "STOP", false);
        }

        drawButton(context, layout.panelX + layout.buttonMargin, layout.buttonY, layout.buttonWidth, 28,
                "REMOVE FILE", this.selectedIndex >= 0);
        drawButton(context, layout.panelX + layout.buttonMargin + layout.buttonWidth + layout.buttonGap,
                layout.buttonY, layout.buttonWidth, 28, "RELOAD SCANNER", false);
        drawButton(context, layout.panelX + layout.buttonMargin + (layout.buttonWidth + layout.buttonGap) * 2,
                layout.buttonY, layout.buttonWidth, 28, this.materialView ? "FILE QUEUE" : "MATERIAL VIEW",
                this.materialView);
        drawButton(context, layout.panelX + layout.buttonMargin + (layout.buttonWidth + layout.buttonGap) * 3,
                layout.buttonY, layout.buttonWidth, 28, this.viewMode ? "EXIT VIEW" : "VIEW MODE",
                this.viewMode && this.selectedPreview != null);
        drawButton(context, layout.panelX + layout.buttonMargin + (layout.buttonWidth + layout.buttonGap) * 4,
                layout.buttonY, layout.buttonWidth, 28, buildButtonLabel(), engine.isActive());

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderEmptyState(DrawContext context, Layout layout) {
        int dropX = layout.dropX;
        int dropY = layout.dropY;
        int dropWidth = layout.dropWidth;
        int dropBottom = layout.dropBottom;
        int dropColor = this.dropHover ? 0xFF1A1A1A : PANEL_INNER;
        context.fill(dropX, dropY, dropX + dropWidth, dropBottom, dropColor);
        context.drawStrokedRectangle(dropX, dropY, dropWidth, layout.dropHeight, this.dropHover ? SOFT_WHITE : BORDER);

        int centerX = dropX + dropWidth / 2;
        int centerY = dropY + layout.dropHeight / 2;
        drawUploadGlyph(context, centerX, centerY - 34);
        context.drawCenteredTextWithShadow(this.textRenderer, "Drop files to get started", centerX, centerY - 2, SOFT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, "Drag files here from your desktop", centerX, centerY + 17, MUTED);
        context.drawCenteredTextWithShadow(this.textRenderer, "Files are only referenced locally — nothing is uploaded",
                centerX, centerY + 34, DIM);
    }

    private void renderFileList(DrawContext context, Layout layout, int x, int y, int width, int height) {
        int bottom = y + height;
        context.fill(x, y, x + width, bottom, PANEL_INNER);
        context.drawStrokedRectangle(x, y, width, height, BORDER);

        context.drawText(this.textRenderer, this.materialView ? "MATERIAL VIEW" : "FILE QUEUE", x + 12, y + 10, MUTED, false);
        boolean canPreview = !layout.split && this.selectedPreview != null;
        String count = String.format(Locale.ROOT, "%02d  FILE%s", this.files.size(), this.files.size() == 1 ? "" : "S");
        int countX = x + width - 12 - this.textRenderer.getWidth(count);
        if (canPreview) {
            drawChip(context, listChipX(layout), y + 5, FILES_CHIP_WIDTH, CHIP_HEIGHT, "PREVIEW", false);
            countX -= FILES_CHIP_WIDTH + 8;
        }
        context.drawText(this.textRenderer, count, countX, y + 10, MUTED, false);

        int listTop = y + 28;
        int visibleRows = Math.max(1, (height - 34) / ROW_HEIGHT);
        int maxOffset = Math.max(0, this.files.size() - visibleRows);
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);
        int shown = Math.min(visibleRows, this.files.size() - this.scrollOffset);
        for (int row = 0; row < shown; row++) {
            int fileIndex = this.scrollOffset + row;
            Path path = this.files.get(fileIndex);
            int rowY = listTop + row * ROW_HEIGHT;
            int rowX = x + 8;
            int rowWidth = width - 16;
            boolean selected = fileIndex == this.selectedIndex;
            context.fill(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT - 3, selected ? 0xFF242424 : ROW);
            if (selected) {
                context.fill(rowX, rowY + 4, rowX + 2, rowY + ROW_HEIGHT - 7, WHITE);
            }
            int iconX = rowX + 8;
            int iconY = rowY + 5;
            context.fill(iconX, iconY, iconX + 28, iconY + 18, 0xFF242424);
            context.drawCenteredTextWithShadow(this.textRenderer, chipLabel(path), iconX + 14, iconY + 5, SOFT_WHITE);

            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            int nameX = iconX + 36;
            int nameMax = Math.max(30, rowWidth - 112);
            context.drawText(this.textRenderer, truncate(name, nameMax), nameX, rowY + 5, SOFT_WHITE, false);
            context.drawText(this.textRenderer, truncate(materialType(path), nameMax), nameX, rowY + 18, MUTED, false);

            String size = readableSize(path);
            context.drawText(this.textRenderer, size, rowX + rowWidth - this.textRenderer.getWidth(size) - 9,
                    rowY + 11, MUTED, false);
        }
        if (this.files.size() > visibleRows) {
            context.drawText(this.textRenderer, "SCROLL TO VIEW MORE", x + 12, bottom - 12, DIM, false);
        }
    }

    private void renderPreviewPane(DrawContext context, Layout layout) {
        SchematicPreview preview = this.selectedPreview;
        context.fill(layout.paneX, layout.paneY, layout.paneX + layout.paneWidth, layout.paneY + layout.paneHeight,
                PANEL_INNER);
        context.drawStrokedRectangle(layout.paneX, layout.paneY, layout.paneWidth, layout.paneHeight, BORDER);

        context.drawText(this.textRenderer, "SCHEMATIC PREVIEW", layout.paneX + 12, layout.paneY + 8, MUTED, false);

        int chipY = paneChipY(layout);
        drawChip(context, paneToggleX(layout), chipY, TOGGLE_CHIP_WIDTH, CHIP_HEIGHT,
                preview.topDown() ? "TOP" : "ISO", true);
        drawChip(context, paneRotateX(layout), chipY, ROTATE_CHIP_WIDTH, CHIP_HEIGHT, "ROTATE", false);
        if (layout.paneOnly) {
            drawChip(context, paneFilesX(layout), chipY, FILES_CHIP_WIDTH, CHIP_HEIGHT, "FILES", false);
        }

        LitematicaFile file = preview.file();
        List<String> stats = previewStats(preview, file);
        int[] imageRect = paneImageRect(layout, stats.size());
        int imageX = imageRect[0];
        int imageY = imageRect[1];
        int imageWidth = imageRect[2];
        int imageHeight = imageRect[3];

        context.fill(imageX, imageY, imageX + imageWidth, imageY + imageHeight, 0xFF0B0B0B);
        context.drawStrokedRectangle(imageX, imageY, imageWidth, imageHeight, 0xFF202020);

        int centerX = imageX + imageWidth / 2;
        int centerY = imageY + imageHeight / 2;
        switch (preview.state()) {
            case DECODING -> {
                context.drawCenteredTextWithShadow(this.textRenderer, "DECODING…", centerX, centerY - 6, SOFT_WHITE);
                context.drawCenteredTextWithShadow(this.textRenderer, "reading region data", centerX, centerY + 8, DIM);
            }
            case FAILED -> {
                context.drawCenteredTextWithShadow(this.textRenderer, "UNREADABLE", centerX, centerY - 6, SOFT_WHITE);
                context.drawCenteredTextWithShadow(this.textRenderer, truncate(preview.error(), imageWidth - 20),
                        centerX, centerY + 8, DIM);
            }
            default -> {
                if (preview.hasImage()) {
                    double fit = Math.min(imageWidth / (double) preview.imageWidth(),
                            imageHeight / (double) preview.imageHeight());
                    int drawWidth = Math.max(1, (int) (preview.imageWidth() * fit));
                    int drawHeight = Math.max(1, (int) (preview.imageHeight() * fit));
                    int drawX = imageX + (imageWidth - drawWidth) / 2;
                    int drawY = imageY + (imageHeight - drawHeight) / 2;
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, SchematicPreview.texture(),
                            drawX, drawY, 0.0F, 0.0F, drawWidth, drawHeight,
                            preview.imageWidth(), preview.imageHeight(),
                            preview.imageWidth(), preview.imageHeight());
                } else {
                    context.drawCenteredTextWithShadow(this.textRenderer, "RENDERING…", centerX, centerY - 6, SOFT_WHITE);
                }
            }
        }

        if (!stats.isEmpty()) {
            int statsTop = layout.paneY + layout.paneHeight - stats.size() * 10 - 6;
            context.fill(layout.paneX + 1, statsTop - 5, layout.paneX + layout.paneWidth - 1, statsTop - 4, BORDER);
            for (int i = 0; i < stats.size(); i++) {
                int lineY = statsTop + i * 10;
                if (lineY + 8 > layout.paneY + layout.paneHeight) {
                    break;
                }
                context.drawText(this.textRenderer, truncate(stats.get(i), layout.paneWidth - 24),
                        layout.paneX + 12, lineY, i == 0 ? SOFT_WHITE : MUTED, false);
            }
        }

        if (preview.hasImage()) {
            String hint = "CLICK TO ROTATE";
            context.drawText(this.textRenderer, hint, imageX + imageWidth - 6 - this.textRenderer.getWidth(hint),
                    imageY + imageHeight - 11, DIM, false);
        }
    }

    private List<String> previewStats(SchematicPreview preview, LitematicaFile file) {
        List<String> lines = new ArrayList<>();
        if (file == null) {
            return lines;
        }
        if (this.materialView) {
            int limit = Math.min(4, file.materials.size());
            for (int i = 0; i < limit; i++) {
                LitematicaFile.Entry entry = file.materials.get(i);
                lines.add(shortName(entry.id) + "   " + format(entry.count));
            }
            return lines;
        }
        String name = file.displayName == null ? "" : file.displayName.trim();
        if (name.isEmpty() || name.equalsIgnoreCase("unnamed")) {
            name = file.fileName;
        }
        lines.add(name);
        lines.add(file.width + " × " + file.height + " × " + file.length + " blocks");
        lines.add(format(file.blockCount) + " placed  ·  " + file.distinctBlocks() + " types");
        lines.add("v" + file.formatVersion + "." + file.formatSubVersion + "  ·  data " + file.dataVersion
                + "  ·  " + file.regionCount + (file.regionCount == 1 ? " region" : " regions"));
        if (file.author != null && !file.author.isBlank()) {
            lines.add("by " + file.author);
        }
        return lines;
    }

    private void drawChip(DrawContext context, int x, int y, int width, int height, String label, boolean active) {
        context.fill(x, y, x + width, y + height, active ? 0xFF2A2A2A : ROW);
        context.drawStrokedRectangle(x, y, width, height, active ? SOFT_WHITE : BORDER);
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + width / 2, y + 5,
                active ? WHITE : SOFT_WHITE);
    }

    private void drawButton(DrawContext context, int x, int y, int width, int height, String label, boolean emphasized) {
        int fill = emphasized ? WHITE : ROW;
        int foreground = emphasized ? BLACK : SOFT_WHITE;
        context.fill(x, y, x + width, y + height, fill);
        context.drawStrokedRectangle(x, y, width, height, emphasized ? WHITE : BORDER);
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + width / 2, y + 10, foreground);
    }

    private void drawUploadGlyph(DrawContext context, int centerX, int y) {
        int left = centerX - 18;
        context.drawStrokedRectangle(left, y, 36, 28, DIM);
        context.fill(centerX - 1, y + 7, centerX + 1, y + 20, SOFT_WHITE);
        context.fill(centerX - 6, y + 11, centerX - 4, y + 13, SOFT_WHITE);
        context.fill(centerX - 4, y + 9, centerX - 2, y + 11, SOFT_WHITE);
        context.fill(centerX - 2, y + 7, centerX + 2, y + 9, SOFT_WHITE);
        context.fill(centerX + 2, y + 9, centerX + 4, y + 11, SOFT_WHITE);
        context.fill(centerX + 4, y + 11, centerX + 6, y + 13, SOFT_WHITE);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public void onFilesDropped(List<Path> paths) {
        int added = 0;
        Path newest = null;
        for (Path path : paths) {
            if (path != null && Files.isRegularFile(path) && !this.files.contains(path)) {
                this.files.add(path);
                newest = path;
                added++;
            }
        }
        this.scrollOffset = Math.max(0, this.files.size() - 1);
        if (newest != null) {
            select(this.files.indexOf(newest));
        } else if (this.files.isEmpty()) {
            select(-1);
        }
        saveQueue();
        this.status = added == 0
                ? "No new files found"
                : "Added " + added + " file" + (added == 1 ? "" : "s") + "  ·  stored locally";
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_1) {
            return super.mouseClicked(click, doubleClick);
        }
        Layout layout = layout();
        int mouseX = (int) click.x();
        int mouseY = (int) click.y();

        BuildEngine engine = BuildEngine.get();
        if (engine.isActive()
                && hit(mouseX, mouseY, buildChipX(layout), buildChipY(layout), BUILD_CHIP_WIDTH, BUILD_CHIP_HEIGHT)) {
            engine.stop("Stopped by user");
            this.status = engine.progress();
            return true;
        }

        if (mouseY >= layout.buttonY && mouseY <= layout.buttonY + 28) {
            int relativeX = mouseX - layout.panelX - layout.buttonMargin;
            if (relativeX >= 0 && relativeX < layout.buttonWidth) {
                removeSelected();
                return true;
            }
            relativeX -= layout.buttonWidth + layout.buttonGap;
            if (relativeX >= 0 && relativeX < layout.buttonWidth) {
                reloadScanner();
                return true;
            }
            relativeX -= layout.buttonWidth + layout.buttonGap;
            if (relativeX >= 0 && relativeX < layout.buttonWidth) {
                this.materialView = !this.materialView;
                this.status = this.materialView ? "Material view  ·  classified by file type" : "File queue  ·  ready for files";
                return true;
            }
            relativeX -= layout.buttonWidth + layout.buttonGap;
            if (relativeX >= 0 && relativeX < layout.buttonWidth) {
                toggleViewMode();
                return true;
            }
            relativeX -= layout.buttonWidth + layout.buttonGap;
            if (relativeX >= 0 && relativeX < layout.buttonWidth) {
                toggleBuild();
                return true;
            }
        }

        if (layout.split || layout.paneOnly) {
            int chipY = paneChipY(layout);
            if (hit(mouseX, mouseY, paneRotateX(layout), chipY, ROTATE_CHIP_WIDTH, CHIP_HEIGHT)) {
                this.selectedPreview.rotate();
                return true;
            }
            if (hit(mouseX, mouseY, paneToggleX(layout), chipY, TOGGLE_CHIP_WIDTH, CHIP_HEIGHT)) {
                this.selectedPreview.toggleTopDown();
                return true;
            }
            if (layout.paneOnly
                    && hit(mouseX, mouseY, paneFilesX(layout), chipY, FILES_CHIP_WIDTH, CHIP_HEIGHT)) {
                this.previewFocus = false;
                this.viewMode = false;
                return true;
            }
            int[] imageRect = paneImageRect(layout, previewStats(this.selectedPreview, this.selectedPreview.file()).size());
            if (mouseX >= imageRect[0] && mouseX < imageRect[0] + imageRect[2]
                    && mouseY >= imageRect[1] && mouseY < imageRect[1] + imageRect[3]) {
                this.selectedPreview.rotate();
                return true;
            }
        }

        if (!layout.split && !layout.paneOnly && this.selectedPreview != null
                && hit(mouseX, mouseY, listChipX(layout), layout.listY + 5, FILES_CHIP_WIDTH, CHIP_HEIGHT)) {
            this.previewFocus = true;
            return true;
        }

        if (!this.files.isEmpty() && !layout.paneOnly
                && mouseX >= layout.listX && mouseX < layout.listX + layout.listWidth
                && mouseY >= layout.listY + 28 && mouseY < layout.listY + layout.listHeight) {
            int row = (mouseY - layout.listY - 28) / ROW_HEIGHT;
            int visibleRows = Math.max(1, (layout.listHeight - 34) / ROW_HEIGHT);
            int index = this.scrollOffset + row;
            if (row >= 0 && row < visibleRows && index < this.files.size()) {
                select(index);
                this.status = "Selected  ·  " + this.files.get(index).getFileName();
                return true;
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    private static boolean hit(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.files.isEmpty()) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int direction = verticalAmount < 0 ? 1 : -1;
        this.scrollOffset = Math.max(0, Math.min(this.files.size() - 1, this.scrollOffset + direction));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if ((input.key() == GLFW.GLFW_KEY_DELETE || input.key() == GLFW.GLFW_KEY_BACKSPACE) && this.selectedIndex >= 0) {
            removeSelected();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && this.viewMode) {
            this.viewMode = false;
            this.previewFocus = false;
            this.status = "File queue  ·  ready for files";
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        Layout layout = layout();
        this.dropHover = mouseX >= layout.dropX && mouseX <= layout.dropX + layout.dropWidth
                && mouseY >= layout.dropY && mouseY <= layout.dropBottom;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public void removed() {
        for (SchematicPreview preview : this.previews.values()) {
            preview.release();
        }
        this.previews.clear();
        this.selectedPreview = null;
        super.removed();
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void select(int index) {
        this.selectedIndex = index;
        saveQueue();
        this.selectedPreview = null;
        this.observedPreviewState = null;
        if (index < 0 || index >= this.files.size()) {
            this.viewMode = false;
            this.previewFocus = false;
            return;
        }
        Path path = this.files.get(index);
        if (!isSchematic(path)) {
            this.viewMode = false;
            this.previewFocus = false;
            return;
        }
        SchematicPreview preview = this.previews.get(path);
        if (preview == null) {
            preview = new SchematicPreview(path);
            this.previews.put(path, preview);
            preview.request();
        }
        this.selectedPreview = preview;
    }

    private void removeSelected() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.files.size()) {
            this.status = "Select a file to remove";
            return;
        }
        Path removed = this.files.remove(this.selectedIndex);
        SchematicPreview preview = this.previews.remove(removed);
        if (preview != null) {
            preview.release();
        }
        select(this.files.isEmpty() ? -1 : Math.min(this.selectedIndex, this.files.size() - 1));
        this.scrollOffset = Math.min(this.scrollOffset, Math.max(0, this.files.size() - 1));
        saveQueue();
        this.status = "Removed  ·  " + removed.getFileName();
    }

    /** Label of the footer button that starts, pauses and resumes the auto build. */
    private String buildButtonLabel() {
        return switch (BuildEngine.get().state()) {
            case RUNNING -> "PAUSE BUILD";
            case PAUSED -> "RESUME BUILD";
            default -> "AUTO BUILD";
        };
    }

    private void toggleBuild() {
        BuildEngine engine = BuildEngine.get();
        switch (engine.state()) {
            case RUNNING -> {
                engine.pause();
                this.status = engine.progress();
            }
            case PAUSED -> {
                engine.resume();
                this.status = engine.progress();
            }
            default -> startAutoBuild();
        }
    }

    /**
     * Starts the build anchored at the block in front of the player, then closes the menu.
     *
     * <p>The engine walks the player, and the game only reads movement keys while no screen is
     * open, so the menu has to get out of the way for the build to run unattended.
     */
    private void startAutoBuild() {
        BuildEngine engine = BuildEngine.get();
        SchematicPreview preview = this.selectedPreview;
        if (preview == null || preview.file() == null) {
            this.status = "Select a decoded schematic before starting a build";
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || client.world == null) {
            this.status = "Join a world before starting a build";
            return;
        }
        GridPos feet = new GridPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        Direction facing = player.getHorizontalFacing();
        // Start on the block in front, and on a genuinely flat surface: the ground under the
        // player is often uneven or just decorated, which would seat the build on the scenery.
        GridPos front = feet.offset(facing.getOffsetX(), 0, facing.getOffsetZ());
        GridPos anchor = BuildPlan.findFlatSite(preview.file(), front, client.world);
        engine.start(preview.file(), anchor, feet);
        this.status = engine.progress();
        this.close();
    }

    private void toggleViewMode() {
        if (this.selectedPreview == null) {
            this.status = "Select a decodable schematic to open view mode";
            return;
        }
        this.viewMode = !this.viewMode;
        this.previewFocus = this.viewMode;
        this.status = this.viewMode
                ? "View mode  ·  ESC or EXIT VIEW to return"
                : "File queue  ·  ready for files";
    }

    private void reloadScanner() {
        int previousCount = this.files.size();
        List<Path> missing = new ArrayList<>();
        for (Path path : this.files) {
            if (!Files.isRegularFile(path)) {
                missing.add(path);
            }
        }
        for (Path path : missing) {
            this.files.remove(path);
            SchematicPreview preview = this.previews.remove(path);
            if (preview != null) {
                preview.release();
            }
        }
        if (this.files.isEmpty()) {
            select(-1);
        } else {
            select(Math.min(Math.max(0, this.selectedIndex), this.files.size() - 1));
        }
        this.status = "Scanner reloaded  ·  " + this.files.size() + " file" + (this.files.size() == 1 ? "" : "s")
                + (previousCount == this.files.size() ? " checked" : " · unavailable files removed");
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    private String readableSize(Path path) {
        try {
            long bytes = Files.size(path);
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        } catch (IOException exception) {
            return "—";
        }
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** Extension badge. Long extensions are abbreviated so the chip never renders as "LIT…". */
    private static String chipLabel(Path path) {
        String extension = extension(path).toUpperCase(Locale.ROOT);
        return extension.length() > 4 ? extension.substring(0, 3) : extension;
    }

    private static String shortName(String id) {
        String name = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        return name.replace('_', ' ');
    }

    private static boolean isSchematic(Path path) {
        String extension = extension(path).toLowerCase(Locale.ROOT);
        return extension.equals("litematic") || extension.equals("litematica");
    }

    private static String extension(Path path) {
        Path name = path.getFileName();
        if (name == null) return "FILE";
        String value = name.toString();
        int dot = value.lastIndexOf('.');
        return dot >= 0 && dot < value.length() - 1 ? value.substring(dot + 1) : "FILE";
    }

    private static String materialType(Path path) {
        if (isSchematic(path)) {
            return "Litematica schematic  ·  decodable";
        }
        return switch (extension(path).toLowerCase(Locale.ROOT)) {
            case "obj", "fbx", "gltf", "glb", "stl" -> "3D model  ·  material asset";
            case "png", "jpg", "jpeg", "tga", "bmp", "webp" -> "Texture  ·  image asset";
            case "mtl" -> "Material definition";
            case "json" -> "Structured data";
            default -> "Other file  ·  " + extension(path).toUpperCase(Locale.ROOT);
        };
    }

    private String truncate(String value, int maxWidth) {
        if (maxWidth <= 0 || this.textRenderer.getWidth(value) <= maxWidth) return value;
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && this.textRenderer.getWidth(value.substring(0, end) + ellipsis) > maxWidth) end--;
        return value.substring(0, end) + ellipsis;
    }
}
