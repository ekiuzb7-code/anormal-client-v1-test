package com.anormal.client.gui;

import com.anormal.client.module.impl.player.SkinShower;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Skin picker for {@link SkinShower}: a native file dialog, a URL field, and the module's own
 * settings below.
 *
 * <p>Opens instead of the normal module card, so the client's ClickGUI is never visible while this
 * is on screen - {@code setScreen} replaces it outright.</p>
 */
public class SkinShowerScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 300;

    private static final int BG = 0xF0161619;
    private static final int BORDER = 0xFF3A3A42;
    private static final int BUTTON = 0xFF2C2C34;
    private static final int BUTTON_HOVER = 0xFF3A3A46;
    private static final int ACCENT = 0xFF5AA9FF;
    private static final int TEXT = 0xFFE8E8EC;
    private static final int TEXT_DIM = 0xFF8A8A96;

    private final SkinShower module;

    private TextField urlField;
    private TextField pathField;
    private String localStatus = "";
    private int localStatusColor = TEXT_DIM;

    private int px, py;
    private int browseX, browseY, browseW, browseH;
    private int pathLoadX, pathLoadY, pathLoadW, pathLoadH;
    private int pathX, pathW, urlX, urlW;
    private int loadX, loadY, loadW, loadH;
    private int classicX, classicY, modelW, modelH;
    private int slimX;
    private int listX, listY, listW, listH;
    private int scroll;
    private int closeX, closeY, closeW, closeH;

    public SkinShowerScreen(SkinShower module) {
        super(Text.literal("SkinShower"));
        this.module = module;
    }

    @Override
    protected void init() {
        px = (width - PANEL_W) / 2;
        py = (height - PANEL_H) / 2;

        browseX = px + 16;
        browseY = py + 58;
        browseW = 92;
        browseH = 18;

        pathLoadW = 52;
        pathLoadH = 18;
        pathLoadX = px + PANEL_W - 16 - pathLoadW;
        pathLoadY = browseY;

        loadX = px + PANEL_W - 16 - 56;
        loadY = py + 106;
        loadW = 56;
        loadH = 18;

        modelW = 68;
        modelH = 18;
        classicX = px + 96;
        classicY = py + 30;
        slimX = classicX + modelW + 6;

        listX = px + 8;
        listY = py + 152;
        listW = PANEL_W - 16;
        listH = PANEL_H - 152 - 42;
        scroll = 0;

        closeW = 64;
        closeH = 18;
        closeX = px + PANEL_W - 16 - closeW;
        closeY = py + PANEL_H - 30;

        // The native dialog is not available in every JVM, so the path is also editable here -
        // pasting "C:////path////skin.png" and pressing Load always works.
        pathX = browseX + browseW + 6;
        pathW = pathLoadX - 6 - pathX;
        pathField = new TextField(module.path.get(), "paste a .png path", 512);
        pathField.setFocused(true);

        urlX = px + 16;
        urlW = loadX - 8 - urlX;
        urlField = new TextField(module.url.get(), "https://example.com/skin.png", 512);
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x99000000);
        ctx.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, BORDER);
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, BG);
        ctx.fill(px, py, px + PANEL_W, py + 22, 0xFF1D1D22);

        ctx.drawText(textRenderer, "SkinShower", px + 16, py + 7, ACCENT, false);

        // Model row.
        ctx.drawText(textRenderer, "Model", px + 16, py + 35, TEXT_DIM, false);
        boolean slim = module.model.is("Slim");
        drawButton(ctx, classicX, classicY, modelW, modelH, "Classic", !slim,
                inRect(mouseX, mouseY, classicX, classicY, modelW, modelH));
        drawButton(ctx, slimX, classicY, modelW, modelH, "Slim", slim,
                inRect(mouseX, mouseY, slimX, classicY, modelW, modelH));

        // File row.
        ctx.drawText(textRenderer, "File", px + 16, py + 44, TEXT_DIM, false);
        drawButton(ctx, browseX, browseY, browseW, browseH, "Choose .png", false,
                inRect(mouseX, mouseY, browseX, browseY, browseW, browseH));
        pathField.render(ctx, textRenderer, pathX, browseY, pathW, browseH);
        drawButton(ctx, pathLoadX, pathLoadY, pathLoadW, pathLoadH, "Load", false,
                inRect(mouseX, mouseY, pathLoadX, pathLoadY, pathLoadW, pathLoadH));

        // URL row.
        ctx.drawText(textRenderer, "URL", px + 16, py + 92, TEXT_DIM, false);
        urlField.render(ctx, textRenderer, urlX, loadY, urlW, loadH);
        drawButton(ctx, loadX, loadY, loadW, loadH, "Load", false,
                inRect(mouseX, mouseY, loadX, loadY, loadW, loadH));

        // Status: the module owns the message because loads finish on a worker thread.
        String shown = SkinShower.isLoading() ? "Working..." : SkinShower.lastStatus();
        int shownColor = SkinShower.isLoading() ? TEXT_DIM : SkinShower.lastStatusColor();
        if (shown == null || shown.isEmpty()) {
            shown = localStatus;
            shownColor = localStatusColor;
        }
        if (shown != null && !shown.isEmpty()) {
            ctx.drawText(textRenderer, trim(shown, PANEL_W - 32), px + 16, py + 134, shownColor, false);
        }

        // Divider + module settings.
        ctx.fill(px + 8, py + 146, px + PANEL_W - 8, py + 147, BORDER);
        SettingsPanel.render(ctx, textRenderer, module.getSettings(),
                listX, listY, listW, listH, scroll, mouseX, mouseY);

        int contentH = SettingsPanel.contentHeight(module.getSettings());
        if (contentH > listH) {
            int thumbH = Math.max(12, (int) (listH * (listH / (double) contentH)));
            int maxScroll = contentH - listH;
            int thumbY = listY + (int) ((listH - thumbH) * (scroll / (double) maxScroll));
            ctx.fill(px + PANEL_W - 5, listY, px + PANEL_W - 3, listY + listH, 0x33000000);
            ctx.fill(px + PANEL_W - 5, thumbY, px + PANEL_W - 3, thumbY + thumbH, ACCENT);
        }

        drawButton(ctx, closeX, closeY, closeW, closeH, "Close", false,
                inRect(mouseX, mouseY, closeX, closeY, closeW, closeH));

        ctx.drawText(textRenderer, "Only you see this skin",
                px + PANEL_W / 2 - textRenderer.getWidth("Only you see this skin") / 2,
                py + PANEL_H - 27, TEXT_DIM, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label,
                            boolean active, boolean hovered) {
        int fill = active ? ACCENT : (hovered ? BUTTON_HOVER : BUTTON);
        ctx.fill(x, y, x + w, y + h, fill);
        ctx.fill(x, y, x + w, y + 1, BORDER);
        ctx.fill(x, y + h - 1, x + w, y + h, BORDER);
        int textColor = active ? 0xFF0E0E12 : TEXT;
        ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2,
                y + (h - 8) / 2, textColor, false);
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trim(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String result = value;
        while (result.length() > 3 && textRenderer.getWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleClick(click.x(), click.y(), click.button(), click);
    }

    /** Kept for older dispatch paths; Screen itself only declares the Click overload. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button,
                new Click(mouseX, mouseY, new MouseInput(button, 0)));
    }

    private boolean handleClick(double rawX, double rawY, int button, Click click) {
        double mouseX = rawX > width ? rawX / client.getWindow().getScaleFactor() : rawX;
        double mouseY = rawY > height ? rawY / client.getWindow().getScaleFactor() : rawY;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (inRect(mx, my, browseX, browseY, browseW, browseH)) {
            setStatus("Opening file picker...", TEXT_DIM);
            module.pickFile();
            return true;
        }
        if (inRect(mx, my, pathLoadX, pathLoadY, pathLoadW, pathLoadH)) {
            loadPath();
            return true;
        }
        if (inRect(mx, my, loadX, loadY, loadW, loadH)) {
            loadUrl();
            return true;
        }
        if (inRect(mx, my, classicX, classicY, modelW, modelH)) {
            module.model.setValue("Classic");
            setStatus("Model: Classic", TEXT_DIM);
            return true;
        }
        if (inRect(mx, my, slimX, classicY, modelW, modelH)) {
            module.model.setValue("Slim");
            setStatus("Model: Slim", TEXT_DIM);
            return true;
        }
        if (inRect(mx, my, closeX, closeY, closeW, closeH)) {
            close();
            return true;
        }
        if (SettingsPanel.click(module.getSettings(), mouseX, mouseY,
                listX, listY, listW, listH, scroll, button)) {
            return true;
        }

        if (pathField != null && pathField.click(mouseX, mouseY, pathX, browseY, pathW, browseH)) {
            urlField.setFocused(false);
            return true;
        }
        if (urlField != null && urlField.click(mouseX, mouseY, urlX, loadY, urlW, loadH)) {
            pathField.setFocused(false);
            return true;
        }
        return super.mouseClicked(click, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int contentH = SettingsPanel.contentHeight(module.getSettings());
        if (contentH > listH) {
            scroll = Math.max(0, Math.min(contentH - listH, scroll - (int) (vertical * SettingsPanel.ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            loadUrl();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_TAB) {
            boolean toUrl = pathField.isFocused();
            pathField.setFocused(!toUrl);
            urlField.setFocused(toUrl);
            return true;
        }
        if (pathField != null && pathField.keyPressed(input)) return true;
        if (urlField != null && urlField.keyPressed(input)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (pathField != null && pathField.charTyped(input)) return true;
        if (urlField != null && urlField.charTyped(input)) return true;
        return super.charTyped(input);
    }

    /** Loads the path typed into the File field. */
    private void loadPath() {
        if (pathField == null) return;
        String value = pathField.get().trim().replace("\"", "");
        if (value.isEmpty()) {
            setStatus("Type or paste a .png path", 0xFFFF6B6B);
            return;
        }
        module.path.set(value);
        module.source.setValue("File");
        setStatus("Loading...", TEXT_DIM);
        module.loadFromFile(value);
    }

    private void loadUrl() {
        if (urlField == null) return;
        String value = urlField.get().trim();
        if (value.isEmpty()) {
            setStatus("Type a link first", 0xFFFF6B6B);
            return;
        }
        module.url.set(value);
        module.source.setValue("URL");
        setStatus("Downloading...", TEXT_DIM);
        module.loadFromUrl(value);
    }

    private void setStatus(String message, int color) {
        this.localStatus = message;
        this.localStatusColor = color;
    }

    /** Keeps the world running behind the panel, exactly like the ClickGUI. */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
