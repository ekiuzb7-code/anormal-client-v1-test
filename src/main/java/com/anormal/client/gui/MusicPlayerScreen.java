package com.anormal.client.gui;

import com.anormal.client.module.impl.legit.MusicPlayer;
import com.anormal.client.music.MusicEngine;
import com.anormal.client.music.MusicLibrary;
import com.anormal.client.music.MusicTrack;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Playlist manager: add files or whole folders, pick a track, drop one, and set the playback modes.
 *
 * <p>Adding songs works three ways because the native dialog is not available in every JVM: the
 * file picker, a typed/pasted path, or a folder scan (which pulls in every supported audio file it
 * finds next to each other).</p>
 */
public class MusicPlayerScreen extends Screen {

    private static final int PANEL_W = 430;
    private static final int PANEL_H = 344;

    private static final int BG = 0xF0161619;
    private static final int BORDER = 0xFF3A3A42;
    private static final int BUTTON = 0xFF2C2C34;
    private static final int BUTTON_HOVER = 0xFF3A3A46;
    private static final int ACCENT = 0xFF5AA9FF;
    private static final int TEXT = 0xFFE8E8EC;
    private static final int TEXT_DIM = 0xFF8A8A96;

    private final MusicPlayer module;

    /** Keeps the last typed path across screen openings. */
    private static String lastPath = "";

    private TextField pathField;
    private String status = "";
    private int statusColor = TEXT_DIM;
    private int scroll;

    private int px, py;
    private int filesX, filesY, filesW, addH;
    private int folderX, folderW;
    private int clearX, clearW;
    private int prevX, prevW, playX, playW, nextX, nextW, transportY, transportH;
    private int scaleX, scaleY, scaleW, scaleH;
    private int shuffleX, shuffleW, loopX, loopW;
    private int volX, volY, volW, volH;
    private int listX, listY, listW, listH;
    private int closeX, closeY, closeW;

    public MusicPlayerScreen(MusicPlayer module) {
        super(Text.literal("Music Player"));
        this.module = module;
    }

    @Override
    protected void init() {
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        addH = 18;
        filesX = px + 16;
        filesY = py + 50;
        filesW = 96;
        folderX = filesX + filesW + 6;
        folderW = 96;
        clearX = folderX + folderW + 6;
        clearW = 64;

        shuffleX = px + 16;
        shuffleW = 70;
        loopX = shuffleX + shuffleW + 6;
        loopW = 70;

        volX = px + 220;
        volY = py + PANEL_H - 26;
        volW = 120;
        volH = 6;

        listX = px + 10;

        // Transport gets its own row with room for the word on the button; the previous 46px
        // button could not fit "Pause" and the text ran into the scale slider.
        transportY = py + 100;
        transportH = 20;
        prevX = px + 16;
        prevW = 52;
        playX = prevX + prevW + 6;
        playW = 72;
        nextX = playX + playW + 6;
        nextW = 52;

        // Scale slider on a dedicated row underneath, so nothing overlaps.
        scaleX = px + 74;
        scaleY = py + 134;
        scaleW = PANEL_W - 16 - 74 - 16;
        scaleH = 6;

        listY = py + 150;
        listW = PANEL_W - 20;
        listH = PANEL_H - 150 - 58;

        closeW = 64;
        closeX = px + PANEL_W - 16 - closeW;
        closeY = py + PANEL_H - 30;

        pathField = new TextField(lastPath, "paste a file or folder path, then Enter", 512);
        // Without focus the field never receives key events, which is why typing into it did
        // nothing at all.
        pathField.setFocused(true);
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MusicLibrary lib = MusicLibrary.get();

        ctx.fill(0, 0, width, height, 0x99000000);
        // Same frosted panel as the HUD card, so the screen and the card read as one design.
        com.anormal.client.util.RenderUtils.drawGlassPanel(ctx, px, py, px + PANEL_W, py + PANEL_H,
                0x8C10141C, 0x3CFFFFFF,
                (com.anormal.client.theme.ThemeManager.getAccentColor() & 0xFFFFFF) | 0x2E000000);
        for (int i = 0; i < 14; i++) {
            int alpha = 18 - i;
            if (alpha <= 0) break;
            ctx.fill(px + 1, py + 1 + i, px + PANEL_W - 1, py + 2 + i, (alpha << 24) | 0xFFFFFF);
        }
        ctx.fill(px + 1, py + 22, px + PANEL_W - 1, py + 23, 0x40FFFFFF);
        ctx.drawText(textRenderer, "Music Player", px + 16, py + 7, ACCENT, false);
        ctx.fill(px + 1, py + 22, px + PANEL_W - 1, py + 23, 0x40FFFFFF);

        MusicTrack current = lib.current();
        String now = current == null ? "Nothing playing" : current.title() + "  -  " + current.artist();
        ctx.drawText(textRenderer, trim(now, PANEL_W - 32), px + 16, py + 29, TEXT, false);

        drawButton(ctx, filesX, filesY, filesW, addH, "Add Files", false,
                inRect(mouseX, mouseY, filesX, filesY, filesW, addH));
        drawButton(ctx, folderX, filesY, folderW, addH, "Add Folder", false,
                inRect(mouseX, mouseY, folderX, filesY, folderW, addH));
        drawButton(ctx, clearX, filesY, clearW, addH, "Clear", false,
                inRect(mouseX, mouseY, clearX, filesY, clearW, addH));

        // Dark field behind the text widget (the widget's own white box clashed with the panel).
        ctx.fill(px + 16, py + 74, px + PANEL_W - 16, py + 92, 0xFF1B1B21);
        ctx.fill(px + 16, py + 91, px + PANEL_W - 16, py + 92, 0xFF3A3A42);
        pathField.render(ctx, textRenderer, px + 16, py + 74, PANEL_W - 32, 18);

        // Transport row.
        boolean isPlaying = MusicEngine.get().isPlaying();
        boolean isPaused = MusicEngine.get().isPaused();
        drawButton(ctx, prevX, transportY, prevW, transportH, "Prev", false,
                inRect(mouseX, mouseY, prevX, transportY, prevW, transportH));
        drawButton(ctx, playX, transportY, playW, transportH,
                isPlaying ? "Pause" : (isPaused ? "Resume" : "Play"), isPlaying,
                inRect(mouseX, mouseY, playX, transportY, playW, transportH));
        drawButton(ctx, nextX, transportY, nextW, transportH, "Next", false,
                inRect(mouseX, mouseY, nextX, transportY, nextW, transportH));

        // Scale row, on its own line so the label never collides with the buttons.
        float cardScale = module.scale.getValue().floatValue();
        ctx.drawText(textRenderer, "SCALE", px + 16, scaleY - 2, TEXT_DIM, false);
        ctx.fill(scaleX, scaleY, scaleX + scaleW, scaleY + scaleH, 0xFF2A2A32);
        double ratio = (cardScale - module.scale.getMin()) / (module.scale.getMax() - module.scale.getMin());
        ctx.fill(scaleX, scaleY, scaleX + (int) (scaleW * ratio), scaleY + scaleH, ACCENT);
        int knob = scaleX + (int) (scaleW * ratio);
        ctx.fill(knob - 1, scaleY - 2, knob + 1, scaleY + scaleH + 2, 0xFFFFFFFF);
        String scaleText = String.format(Locale.ROOT, "%.2f", cardScale);
        ctx.drawText(textRenderer, scaleText, px + PANEL_W - 16 - textRenderer.getWidth(scaleText),
                scaleY - 2, TEXT_DIM, false);

        // Track list.
        List<MusicTrack> tracks = lib.tracks();
        int rowH = 16;
        int visible = listH / rowH;
        int maxScroll = Math.max(0, (tracks.size() - visible) * rowH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        for (int i = 0; i < visible; i++) {
            int index = i + scroll / rowH;
            if (index >= tracks.size()) break;

            int rowY = listY + i * rowH;
            boolean hovered = mouseX >= listX && mouseX <= listX + listW
                    && mouseY >= rowY && mouseY <= rowY + rowH;
            boolean playing = index == lib.index();

            if (playing) {
                ctx.fill(listX, rowY, listX + listW, rowY + rowH, 0x335AA9FF);
            } else if (hovered) {
                ctx.fill(listX, rowY, listX + listW, rowY + rowH, 0x18FFFFFF);
            }

            MusicTrack track = tracks.get(index);
            String label = (index + 1) + ". " + track.title() + "  -  " + track.artist();
            ctx.drawText(textRenderer, trim(label, listW - 12), listX + 4, rowY + 4,
                    playing ? ACCENT : TEXT, false);
        }

        if (tracks.isEmpty()) {
            ctx.drawText(textRenderer, "No tracks yet - use Add Files or Add Folder",
                    listX + 4, listY + 4, TEXT_DIM, false);
        }

        // Modes.
        drawButton(ctx, shuffleX, py + PANEL_H - 32, shuffleW, 16,
                "Shuffle: " + (lib.shuffle() ? "ON" : "OFF"), lib.shuffle(),
                inRect(mouseX, mouseY, shuffleX, py + PANEL_H - 32, shuffleW, 16));
        String loopText = switch (lib.loop()) {
            case OFF -> "Loop: OFF";
            case ALL -> "Loop: ALL";
            case ONE -> "Loop: ONE";
        };
        drawButton(ctx, loopX, py + PANEL_H - 32, loopW, 16, loopText,
                lib.loop() != MusicLibrary.Loop.OFF,
                inRect(mouseX, mouseY, loopX, py + PANEL_H - 32, loopW, 16));

        // Volume.
        float v = module.volume.getValue().floatValue();
        ctx.fill(volX, volY, volX + volW, volY + volH, 0xFF2A2A32);
        ctx.fill(volX, volY, volX + (int) (volW * v), volY + volH, ACCENT);
        ctx.drawText(textRenderer, "Vol " + (int) (v * 100) + "%", volX, volY - 10, TEXT_DIM, false);

        if (!status.isEmpty()) {
            // Sits left of the Close button on the same row - it used to overlap it.
            ctx.drawText(textRenderer, trim(status, PANEL_W - 32), px + 16, py + PANEL_H - 48,
                    statusColor, false);
        }

        drawButton(ctx, closeX, closeY, closeW, 18, "Close", false,
                inRect(mouseX, mouseY, closeX, closeY, closeW, 18));

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label,
                            boolean active, boolean hovered) {
        ctx.fill(x, y, x + w, y + h, active ? ACCENT : (hovered ? BUTTON_HOVER : BUTTON));
        ctx.fill(x, y, x + w, y + 1, BORDER);
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER);
        ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2,
                y + (h - 8) / 2, active ? 0xFF0E0E12 : TEXT, false);
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trim(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String out = value;
        while (out.length() > 3 && textRenderer.getWidth(out + "...") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button(), click);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button,
                new Click(mouseX, mouseY, new MouseInput(button, 0)));
    }

    private boolean handleClick(double rawX, double rawY, int button, Click click) {
        double mouseX = rawX > width ? rawX / client.getWindow().getScaleFactor() : rawX;
        double mouseY = rawY > height ? rawY / client.getWindow().getScaleFactor() : rawY;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        MusicLibrary lib = MusicLibrary.get();

        if (inRect(mx, my, filesX, filesY, filesW, addH)) {
            pickFiles();
            return true;
        }
        if (inRect(mx, my, folderX, filesY, folderW, addH)) {
            pickFolder();
            return true;
        }
        if (inRect(mx, my, clearX, filesY, clearW, addH)) {
            lib.clear();
            setStatus("Queue cleared", TEXT_DIM);
            return true;
        }
        if (inRect(mx, my, prevX, transportY, prevW, transportH)) {
            lib.previous();
            return true;
        }
        if (inRect(mx, my, playX, transportY, playW, transportH)) {
            if (MusicEngine.get().isPlaying() || MusicEngine.get().isPaused()) {
                MusicEngine.get().togglePause();
            } else {
                lib.playCurrentOrFirst();
            }
            return true;
        }
        if (inRect(mx, my, nextX, transportY, nextW, transportH)) {
            lib.next(true);
            return true;
        }
        if (inRect(mx, my, scaleX, scaleY - 6, scaleW, scaleH + 12)) {
            double r = Math.max(0, Math.min(1, (mouseX - scaleX) / (double) scaleW));
            module.scale.setValue(module.scale.getMin()
                    + r * (module.scale.getMax() - module.scale.getMin()));
            return true;
        }
        if (inRect(mx, my, shuffleX, py + PANEL_H - 32, shuffleW, 16)) {
            lib.toggleShuffle();
            return true;
        }
        if (inRect(mx, my, loopX, py + PANEL_H - 32, loopW, 16)) {
            lib.cycleLoop();
            return true;
        }
        if (inRect(mx, my, volX, volY - 6, volW, volH + 12)) {
            double ratio = Math.max(0, Math.min(1, (mouseX - volX) / (double) volW));
            module.volume.setValue(ratio);
            MusicEngine.get().setVolume((float) ratio);
            return true;
        }
        if (inRect(mx, my, closeX, closeY, closeW, 18)) {
            close();
            return true;
        }

        // Track rows: left plays, right removes.
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int rowH = 16;
            int index = scroll / rowH + (int) ((mouseY - listY) / rowH);
            if (index >= 0 && index < lib.tracks().size()) {
                if (button == 1) {
                    String removed = lib.tracks().get(index).title();
                    lib.remove(index);
                    setStatus("Removed: " + removed, TEXT_DIM);
                } else {
                    lib.playIndex(index);
                }
                return true;
            }
        }

        if (pathField != null && pathField.click(mouseX, mouseY, px + 16, py + 74, PANEL_W - 32, 18)) {
            return true;
        }
        // Any click that is not on a control keeps the text field usable.
        if (pathField != null) pathField.setFocused(true);
        return super.mouseClicked(click, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        List<MusicTrack> tracks = MusicLibrary.get().tracks();
        int visible = listH / 16;
        int maxScroll = Math.max(0, (tracks.size() - visible) * 16);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (vertical * 16)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            addFromPath();
            return true;
        }
        if (pathField != null && pathField.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (pathField != null && pathField.charTyped(input)) return true;
        return super.charTyped(input);
    }

    // ------------------------------------------------------------------
    //  Adding tracks
    // ------------------------------------------------------------------

    private void addFromPath() {
        if (pathField == null) return;
        String value = pathField.get().trim().replace("\"", "");
        if (value.isEmpty()) {
            setStatus("Paste a path first", 0xFFFF6B6B);
            return;
        }
        try {
            Path path = Path.of(value);
            if (java.nio.file.Files.isDirectory(path)) {
                int added = MusicLibrary.get().addFolder(path);
                setStatus("Added " + added + " file(s)", 0xFF6BD96B);
            } else if (MusicTrack.isSupported(path)) {
                MusicLibrary.get().add(path);
                setStatus("Added " + path.getFileName(), 0xFF6BD96B);
            } else {
                setStatus("Unsupported file type", 0xFFFF6B6B);
            }
        } catch (Throwable t) {
            setStatus("Could not read that path", 0xFFFF6B6B);
        }
    }

    /** Native multi-file picker; falls back to the path field when AWT is unavailable. */
    private void pickFiles() {
        runDialog(FileDialog.LOAD, false, files -> {
            int added = 0;
            for (String f : files) {
                Path p = Path.of(f);
                if (MusicTrack.isSupported(p)) {
                    MusicLibrary.get().add(p);
                    added++;
                }
            }
            setStatus("Added " + added + " file(s)", 0xFF6BD96B);
        });
    }

    private void pickFolder() {
        runDialog(FileDialog.LOAD, true, files -> {
            if (files.length == 0) return;
            Path dir = Path.of(files[0]);
            if (!java.nio.file.Files.isDirectory(dir)) {
                dir = dir.getParent();
            }
            int added = MusicLibrary.get().addFolder(dir);
            setStatus("Added " + added + " file(s)", 0xFF6BD96B);
        });
    }

    private interface DialogCallback {
        void accept(String[] files);
    }

    private void runDialog(int mode, boolean directoryOnly, DialogCallback callback) {
        Thread thread = new Thread(() -> {
            try {
                java.awt.EventQueue.invokeAndWait(() -> {
                    try {
                        Frame owner = new Frame();
                        owner.setAlwaysOnTop(true);
                        owner.setUndecorated(true);
                        owner.setSize(1, 1);
                        owner.setLocationRelativeTo(null);
                        owner.setVisible(true);

                        FileDialog dialog = new FileDialog(owner, "Select audio", mode);
                        if (mode == FileDialog.LOAD && !directoryOnly) {
                            dialog.setFilenameFilter((dir, name) -> {
                                String lower = name.toLowerCase(Locale.ROOT);
                                return lower.endsWith(".mp3") || lower.endsWith(".wav")
                                        || lower.endsWith(".aiff") || lower.endsWith(".au");
                            });
                            dialog.setMultipleMode(true);
                        }
                        dialog.setVisible(true);

                        String dir = dialog.getDirectory();
                        java.io.File[] picked = dialog.getFiles();
                        dialog.dispose();
                        owner.dispose();

                        if (dir == null || picked == null || picked.length == 0) {
                            setStatus("Cancelled", TEXT_DIM);
                            return;
                        }
                        String[] paths = new String[picked.length];
                        for (int i = 0; i < picked.length; i++) {
                            paths[i] = picked[i].getAbsolutePath();
                        }
                        callback.accept(paths);
                    } catch (Throwable t) {
                        setStatus("File dialog unavailable - paste a path", 0xFFFF6B6B);
                    }
                });
            } catch (Throwable t) {
                setStatus("File dialog unavailable - paste a path", 0xFFFF6B6B);
            }
        }, "anormal-music-picker");
        thread.setDaemon(true);
        thread.start();
    }

    private void setStatus(String message, int color) {
        this.status = message;
        this.statusColor = color;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
