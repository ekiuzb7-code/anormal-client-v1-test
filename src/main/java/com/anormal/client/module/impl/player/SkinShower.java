package com.anormal.client.module.impl.player;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.KeybindSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.lucid.StringSetting;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows a custom skin on your own model, client-side only.
 *
 * <p>Two sources: a {@code .png} on disk (picked with a real file dialog) or an image URL. The
 * image is uploaded into a dynamic texture, and {@code AbstractClientPlayerEntity.getSkin()} is
 * redirected for the local player only, so the skin is visible to you in first person, third
 * person, the inventory preview and the tab list - while the server and everyone else keep seeing
 * your real skin.</p>
 *
 * <p>Loading is done off the render thread: a URL fetch or a big PNG must never stall the frame,
 * so the bytes are read on a worker and only the texture upload hops back to the client thread.</p>
 */
public class SkinShower extends Module {

    public final ModeSetting source = new ModeSetting("Source",
            "Where the skin comes from", "File", "File", "URL");

    public final ModeSetting model = new ModeSetting("Model",
            "Skin model - Classic (Steve arms) or Slim (Alex arms)", "Classic", "Classic", "Slim");

    public final StringSetting path = new StringSetting("Path",
            "Path to a .png skin, or press the Load key to pick one", "");

    public final StringSetting url = new StringSetting("Url",
            "Direct link to a skin image (https://...)", "");

    public final KeybindSetting loadKey = new KeybindSetting("Load Key",
            "Opens a file picker for the skin .png", -1);

    public final BooleanSetting reloadOnEnable = new BooleanSetting("Reload On Enable",
            "Re-apply the saved skin whenever the module is switched on", true);

    public final BooleanSetting showStatus = new BooleanSetting("Show Status",
            "Print the load result to chat", true);

    // ------------------------------------------------------------------
    //  State read by the skin mixin
    // ------------------------------------------------------------------
    private static final Identifier TEXTURE_ID = Identifier.of("anormal", "skinshower/skin");

    private static SkinShower instance;
    private static boolean active;
    private static SkinTextures customSkin;
    private static NativeImageBackedTexture currentTexture;
    private static boolean wasKeyDown;
    private static final AtomicBoolean LOADING = new AtomicBoolean(false);

    /** Last status line, polled by the picker screen so async results show up there too. */
    private static volatile String lastStatus = "";
    private static volatile int lastStatusColor = 0xFF8A8A96;
    private static volatile long lastStatusAt = 0L;

    public SkinShower() {
        super("SkinShower", "Shows a custom skin (from a .png or an image link) on your own model",
                Category.PLAYER);
        addSetting(source);
        addSetting(model);
        addSetting(path);
        addSetting(url);
        addSetting(loadKey);
        addSetting(reloadOnEnable);
        addSetting(showStatus);
        instance = this;
    }

    public static SkinShower getInstance() {
        return instance;
    }

    /**
     * @return the skin to render for the local player, or {@code null} to fall back to vanilla.
     *
     * <p>Gated on {@code isEnabled()} rather than the {@code active} mirror: a skin can be loaded
     * while the module is off (the file dialog does not check), and in that case the old check
     * reported "loaded" while the model kept the vanilla skin.</p>
     */
    public static SkinTextures override() {
        if (instance == null || !instance.isEnabled()) return null;
        return customSkin;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled() && customSkin != null;
    }

    @Override
    public void onEnable() {
        active = true;
        // Only reload when nothing is loaded yet: re-reading the source on every toggle would
        // throw away a skin that was just picked in the GUI.
        if (reloadOnEnable.isEnabled() && customSkin == null) {
            reload();
        }
    }

    @Override
    public void onDisable() {
        active = false;
        customSkin = null;
    }

    @Override
    public void onTickAlways() {
        if (mc.player == null || mc.getWindow() == null) return;

        int key = loadKey.getKey();
        if (key < 0) {
            wasKeyDown = false;
            return;
        }

        boolean down;
        try {
            long handle = mc.getWindow().getHandle();
            down = org.lwjgl.glfw.GLFW.glfwGetKey(handle, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return;
        }
        if (down && !wasKeyDown) {
            pickFile();
        }
        wasKeyDown = down;
    }

    /** Loads whatever the current settings point at. */
    public void reload() {
        if (source.is("URL")) {
            loadFromUrl(url.get());
        } else {
            loadFromFile(path.get());
        }
    }

    /**
     * Opens a native file picker and loads the chosen .png.
     *
     * <p>AWT dialogs must be created on the AWT event thread - building one from an arbitrary
     * worker leaves the window invisible on Windows, which is why the button appeared to do
     * nothing. The dialog blocks until closed, so it also runs off the render thread.</p>
     */
    public void pickFile() {
        Thread thread = new Thread(() -> {
            try {
                java.awt.EventQueue.invokeAndWait(() -> {
                    try {
                        // An owner frame keeps the dialog in front of the game window.
                        Frame owner = new Frame();
                        owner.setAlwaysOnTop(true);
                        owner.setUndecorated(true);
                        owner.setSize(1, 1);
                        owner.setLocationRelativeTo(null);
                        owner.setVisible(true);

                        FileDialog dialog = new FileDialog(owner, "Select a skin (.png)", FileDialog.LOAD);
                        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
                        dialog.setVisible(true);

                        String dir = dialog.getDirectory();
                        String file = dialog.getFile();
                        dialog.dispose();
                        owner.dispose();

                        if (dir == null || file == null) {
                            status("Cancelled");
                            return;
                        }
                        Path chosen = Path.of(dir, file);
                        path.set(chosen.toString());
                        loadFromFile(chosen.toString());
                    } catch (Throwable t) {
                        LucidLog.LOGGER.error("skin file dialog threw", t);
                        status("\u00a7cFile dialog unavailable - paste the path in the File field");
                    }
                });
            } catch (Throwable t) {
                LucidLog.LOGGER.error("skin file picker thread threw", t);
                status("\u00a7cFile dialog unavailable - paste the path in the File field");
            }
        }, "anormal-skin-picker");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------
    //  Loading
    // ------------------------------------------------------------------

    public void loadFromFile(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            status("§cNo file selected");
            return;
        }
        if (!LOADING.compareAndSet(false, true)) return;

        Thread thread = new Thread(() -> {
            try {
                Path file = Path.of(rawPath.trim());
                if (!Files.isRegularFile(file)) {
                    status("§cFile not found: " + file.getFileName());
                    return;
                }
                byte[] bytes = Files.readAllBytes(file);
                apply(bytes, file.getFileName().toString());
            } catch (Throwable t) {
                LucidLog.LOGGER.error("skin file load threw", t);
                status("§cCould not read the file");
            } finally {
                LOADING.set(false);
            }
        }, "anormal-skin-file");
        thread.setDaemon(true);
        thread.start();
    }

    public void loadFromUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            status("§cNo URL set");
            return;
        }
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            status("§cURL must start with http:// or https://");
            return;
        }
        if (!LOADING.compareAndSet(false, true)) return;

        Thread thread = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(8))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(rawUrl.trim()))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "AnormalClient")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    status("§cHTTP " + response.statusCode());
                    return;
                }
                apply(response.body(), "url");
            } catch (Throwable t) {
                LucidLog.LOGGER.error("skin url load threw", t);
                status("§cDownload failed (no internet?)");
            } finally {
                LOADING.set(false);
            }
        }, "anormal-skin-url");
        thread.setDaemon(true);
        thread.start();
    }

    /** Decodes the bytes and hands the upload to the client thread. */
    private void apply(byte[] bytes, String origin) {
        NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Throwable t) {
            // Not every server hands out a raw PNG - fall back to ImageIO and rebuild the pixels.
            image = decodeWithImageIo(bytes);
        }
        if (image == null) {
            status("§cThat file is not a valid image");
            return;
        }

        NativeImage finalImage = image;
        mc.execute(() -> upload(finalImage, origin));
    }

    private static NativeImage decodeWithImageIo(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage buffered = ImageIO.read(in);
            if (buffered == null) return null;
            int w = buffered.getWidth();
            int h = buffered.getHeight();
            NativeImage image = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = buffered.getRGB(x, y);
                    int a = (argb >>> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    image.setColorArgb(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            return image;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Runs on the client thread: registers the texture and swaps the skin in. */
    private void upload(NativeImage image, String origin) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width != 64 || (height != 64 && height != 32)) {
                status("§cSkin must be 64x64 or 64x32 (got " + width + "x" + height + ")");
                image.close();
                return;
            }

            // Release the previous upload first: changing skins repeatedly would otherwise
            // leave one GL texture behind per load.
            if (currentTexture != null) {
                try {
                    currentTexture.close();
                } catch (Throwable ignored) {
                }
            }
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "anormal-skinshower", image);
            mc.getTextureManager().registerTexture(TEXTURE_ID, texture);
            currentTexture = texture;

            PlayerSkinType type = model.is("Slim") ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
            // SkinOverride wants a TextureAssetInfo: id + texture path, both our dynamic texture.
            AssetInfo.TextureAssetInfo asset = new AssetInfo.TextureAssetInfo(TEXTURE_ID);

            // Start from the default skin so the cape/elytra slots stay valid, then override
            // only the body texture and the arm model.
            customSkin = DefaultSkinHelper.getSteve().withOverride(
                    SkinTextures.SkinOverride.create(
                            Optional.of(asset),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(type)));

            // A skin is only visible while the module runs, so loading one switches it on.
            if (!isEnabled()) {
                setEnabled(true);
            }

            status("§aSkin loaded (" + width + "x" + height + ", " + (model.is("Slim") ? "slim" : "classic") + ")");
        } catch (Throwable t) {
            LucidLog.LOGGER.error("skin upload threw", t);
            status("§cCould not apply the skin");
        }
    }

    public static String lastStatus() {
        return lastStatus;
    }

    public static int lastStatusColor() {
        return lastStatusColor;
    }

    public static boolean isLoading() {
        return LOADING.get();
    }

    private void status(String message) {
        lastStatus = message.replace("§a", "").replace("§c", "");
        lastStatusColor = message.startsWith("§a") ? 0xFF6BD96B
                : message.startsWith("§c") ? 0xFFFF6B6B : 0xFF8A8A96;
        lastStatusAt = System.currentTimeMillis();
        try {
            if (!showStatus.isEnabled()) return;
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendMessage(net.minecraft.text.Text.literal(message), false);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Called from the chat hook: {@code ;skin <path-or-url>}. */
    public static boolean handleCommand(String argument) {
        SkinShower module = com.anormal.client.module.ModuleManager.getModule(SkinShower.class);
        if (module == null || !module.isEnabled()) return false;

        String arg = argument == null ? "" : argument.trim();
        if (arg.isEmpty()) {
            module.pickFile();
            return true;
        }
        if (arg.startsWith("http://") || arg.startsWith("https://")) {
            module.url.set(arg);
            module.source.setValue("URL");
            module.loadFromUrl(arg);
        } else {
            String cleaned = arg.replace("\"", "");
            module.path.set(cleaned);
            module.source.setValue("File");
            module.loadFromFile(cleaned);
        }
        return true;
    }
}
