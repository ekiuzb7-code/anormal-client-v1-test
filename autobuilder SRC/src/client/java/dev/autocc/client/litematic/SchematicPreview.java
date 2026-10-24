package dev.autocc.client.litematic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the decode + render pipeline for one {@code .litematica} file.
 *
 * <p>The NBT decode runs on a background thread because it touches no client state. Everything
 * that needs the client — resolving block colours and uploading the rendered image — happens in
 * {@link #prepare()}, which the screen calls from a client tick rather than from its render
 * method, so no GPU texture is ever created in the middle of a render pass.
 *
 * <p>All previews share a single texture identifier. Registering a texture under an identifier
 * that is already in use replaces it and releases the previous one, so switching between files
 * cannot leak GPU memory, and a resource reload that drops the dynamic texture is repaired on the
 * next prepare.
 */
public final class SchematicPreview {
    public enum State { IDLE, DECODING, READY, FAILED }

    private static final Identifier TEXTURE = Identifier.of("auto-cc", "dynamic/schematic_preview");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auto-cc litematic decoder");
        thread.setDaemon(true);
        return thread;
    });

    /** Only the preview the screen is currently showing uploads to the shared texture. */
    private static SchematicPreview owner;
    private static SchematicPreview uploaded;

    private final Path path;
    private final AtomicInteger generation = new AtomicInteger();

    private volatile State state = State.IDLE;
    private volatile LitematicaFile file;
    private volatile String error = "";

    private int rotation;
    private boolean topDown;
    private BlockTextures.Appearance[] appearances;
    private IsoRenderer.Volume volume;
    private int volumeRotation = -1;
    private int imageWidth;
    private int imageHeight;
    private boolean dirty = true;
    private boolean renderFailed;

    public SchematicPreview(Path path) {
        this.path = path;
    }

    public static Identifier texture() {
        return TEXTURE;
    }

    public static void setOwner(SchematicPreview preview) {
        owner = preview;
    }

    public Path path() {
        return this.path;
    }

    public State state() {
        return this.state;
    }

    public LitematicaFile file() {
        return this.file;
    }

    public String error() {
        return this.error;
    }

    public boolean topDown() {
        return this.topDown;
    }

    public int rotation() {
        return this.rotation;
    }

    public boolean renderFailed() {
        return this.renderFailed;
    }

    public int imageWidth() {
        return this.imageWidth;
    }

    public int imageHeight() {
        return this.imageHeight;
    }

    /** True while the currently uploaded texture still holds this preview's image. */
    public boolean hasImage() {
        return this.imageWidth > 0 && this.imageHeight > 0 && uploaded == this;
    }

    public void request() {
        if (this.state != State.IDLE) {
            return;
        }
        this.state = State.DECODING;
        int token = this.generation.incrementAndGet();
        Path target = this.path;
        CompletableFuture.runAsync(() -> {
            try {
                LitematicaFile decoded = LitematicaFile.read(target);
                if (this.generation.get() != token) {
                    return;
                }
                this.file = decoded;
                this.state = State.READY;
                this.dirty = true;
            } catch (Throwable throwable) {
                if (this.generation.get() != token) {
                    return;
                }
                this.error = message(throwable);
                this.state = State.FAILED;
            }
        }, WORKER);
    }

    public void rotate() {
        this.rotation = (this.rotation + 1) & 3;
        this.dirty = true;
    }

    public void toggleTopDown() {
        this.topDown = !this.topDown;
        this.dirty = true;
    }

    /** Runs on the client tick: resolves colours and (re)renders the image when needed. */
    public void prepare() {
        if (owner != this || !this.dirty) {
            return;
        }
        // Left dirty on purpose: the decode may finish between two ticks, and the state is only
        // read once per tick, so clearing here could swallow the freshly decoded file.
        if (this.state != State.READY || this.file == null) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }
        try {
            LitematicaFile current = this.file;
            if (this.appearances == null) {
                this.appearances = new BlockTextures().resolve(current.palette);
            }
            if (this.volume == null || this.volumeRotation != this.rotation) {
                this.volume = IsoRenderer.rotate(current, this.rotation);
                this.volumeRotation = this.rotation;
            }
            IsoRenderer.Result result = new IsoRenderer().render(this.volume, this.appearances, this.topDown);
            client.getTextureManager().registerTexture(TEXTURE,
                    new NativeImageBackedTexture(() -> "auto-cc schematic preview", result.image));
            this.imageWidth = result.width;
            this.imageHeight = result.height;
            this.renderFailed = false;
            this.dirty = false;
            uploaded = this;
        } catch (Throwable throwable) {
            this.error = message(throwable);
            this.renderFailed = true;
            this.dirty = false;
        }
    }

    /** Frees the decoded grid once the screen is closed. */
    public void release() {
        this.generation.incrementAndGet();
        if (uploaded == this) {
            uploaded = null;
        }
        if (owner == this) {
            owner = null;
        }
        this.volume = null;
        this.appearances = null;
    }

    private static String message(Throwable throwable) {
        String detail = throwable.getMessage();
        return detail == null || detail.isBlank() ? throwable.getClass().getSimpleName() : detail;
    }
}
