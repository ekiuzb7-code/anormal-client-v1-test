package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.StringSetting;
import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.util.RenderUtils;
import com.lucidclient.ui.util.WorldRender3D;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

/**
 * SpawnerESP (glazed SpawnerNotifier-style) — detects spawners the moment
 * their chunk data arrives, notifies you, and optionally alerts a Discord
 * webhook or force-disconnects; found spawners render as 3D boxes + labels.
 *
 * <h3>Detection (event-driven, glazed's approach)</h3>
 * Instead of rescanning loaded chunks every tick, the client's chunk mixin
 * hands every spawner block-entity to this module the instant the server
 * sends the chunk data (and on single-tile spawner updates). Each position
 * is handled exactly once per session — the chat alert fires while the
 * chunk is still loading on screen.
 *
 * <h3>Alerting</h3>
 * <ul>
 *   <li><b>Chat</b> — a chat line with coordinates + distance.</li>
 *   <li><b>Webhook</b> — a Discord webhook POST (async) with coordinates,
 *       the server address, and optionally an @everyone ping.</li>
 *   <li><b>Disconnect On Find</b> — disables the module and disconnects
 *       immediately (stop loading the area / don't act suspicious).</li>
 * </ul>
 *
 * <h3>Rendering</h3>
 * Found spawners render with depth-tested 3D world boxes + tracers + a
 * screen-space mob-type label (resolved lazily from the world's block
 * entity once the chunk is live). With {@code Only New} on (default),
 * only spawners discovered while the module is enabled render — the
 * glazed behavior. Off = a per-tick scan also merges pre-existing
 * spawners (render only, no re-announcement).
 *
 * <h3>Pruning</h3>
 * Every 3 seconds, found entries beyond {@code Range} are dropped so the
 * render set stays bounded while travelling.
 *
 * Settings:
 *   Webhook, Webhook URL, Ping On Webhook, Disconnect On Find,
 *   Only New, Range, Color, Show Label, Trace, Trace Color
 */
public class SpawnerESP extends Module {

    private static final int PRUNE_INTERVAL_TICKS = 60; // 3s

    // ── Alerting settings ─────────────────────────────────────
    private final EnumSetting notifyMode = addSetting(
        new EnumSetting("Notify", "Whether a newly-found spawner is announced in chat.",
            new String[]{ "Chat", "None" }, "Chat"));
    private final BooleanSetting webhook = addSetting(
        new BooleanSetting("Webhook", "POST an alert to a Discord webhook when a spawner is found.", false));
    private final StringSetting webhookUrl = addSetting(
        new StringSetting("Webhook URL", "Discord webhook URL used when Webhook is on.", ""));
    private final BooleanSetting pingOnWebhook = addSetting(
        new BooleanSetting("Ping On Webhook", "Prefix the webhook message with @everyone.", false));
    private final BooleanSetting disconnectOnFind = addSetting(
        new BooleanSetting("Disconnect On Find", "Disable the module and disconnect the moment a spawner is found.", false));
    private final BooleanSetting onlyNew = addSetting(
        new BooleanSetting("Only New", "Only render spawners discovered while the module is enabled (glazed behavior). Off = also render pre-existing spawners via a periodic scan.", true));

    // ── Render settings ───────────────────────────────────────
    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Render + scan radius (blocks). Found entries beyond it are pruned.", 8f, 256f, 96f, 0));
    private final ColorSetting color = addSetting(
        new ColorSetting("Color", "Box + label color. Click to cycle.", 0xFFFF0000));
    private final BooleanSetting showLabel = addSetting(
        new BooleanSetting("Show Label", "Render the spawner type name.", true));
    private final BooleanSetting trace = addSetting(
        new BooleanSetting("Trace", "Draw tracers from the camera to spawners.", true));
    private final ColorSetting traceColor = addSetting(
        new ColorSetting("Trace Color", "Tracer color. Click to cycle.", 0xFFAA00FF));

    /** Found spawners: pos → label (null until the chunk BE is available). */
    private final Map<BlockPos, String> found = new LinkedHashMap<>();
    private int pruneTicks = 0;
    private int sessionCount = 0;

    public SpawnerESP() {
        super("SpawnerESP", "Detects spawners on chunk load — chat/toast/webhook alerts, optional disconnect, 3D boxes + labels.",
            Category.RENDER);
    }

    // ── Persistent seen-memory (anti re-announcement) ────────────────
    /** Every spawner position ever announced, as "x,y,z" strings. Chunk
     *  reloads, module re-toggles and relogs re-deliver the same chunk data —
     *  without this memory each redelivery re-announced (and re-webhooked /
     *  re-disconnected for) spawners that were there all along. */
    private static final Set<String> seen = new HashSet<>();
    private static final Path SEEN_FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("lucidclient").resolve("spawners_seen.json");
    private static final int MAX_SEEN = 5000;
    private static final Gson gson = new Gson();

    private static String key(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    @SuppressWarnings("unchecked")
    private static void loadSeen() {
        try {
            Path f = SEEN_FILE;
            if (Files.exists(f)) {
                java.util.List<String> list = gson.fromJson(Files.readString(f), java.util.List.class);
                if (list != null) seen.addAll(list);
            }
        } catch (Throwable ignored) {
            // Corrupt/missing log — worst case one re-announcement.
        }
    }

    private static void saveSeen() {
        try {
            Files.createDirectories(SEEN_FILE.getParent());
            // Bound the log — drop oldest (insertion order) entries.
            while (seen.size() > MAX_SEEN) {
                seen.remove(seen.iterator().next());
            }
            Files.writeString(SEEN_FILE, gson.toJson(new java.util.ArrayList<>(seen)));
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onEnable() {
        found.clear();
        pruneTicks = 0;
        sessionCount = 0;
        loadSeen();
    }

    @Override
    protected void onDisable() {
        found.clear();
        saveSeen();
    }

    // ════════════════════════════════════════════════════════════════
    //  CHUNK HOOK — called from ClientPlayNetworkHandlerMixin (netty
    //  thread) when chunk data or a BE update carries a spawner.
    // ════════════════════════════════════════════════════════════════
    public static void onSpawnerFound(BlockPos pos) {
        LucidClient instance = LucidClient.getInstance();
        if (instance == null || instance.getModuleManager() == null || pos == null) return;
        SpawnerESP esp = instance.getModuleManager().getModule(SpawnerESP.class);
        if (esp == null || !esp.isEnabled()) return;
        BlockPos immutable = pos.toImmutable();
        // Hop to the client thread — chat/webhook/disconnect are
        // main-thread only.
        MinecraftClient.getInstance().execute(() -> esp.handleFound(immutable));
    }

    /** Called once per newly-discovered spawner (client thread). */
    private void handleFound(BlockPos pos) {
        if (!isEnabled() || found.containsKey(pos)) return;
        found.put(pos, null); // label resolved lazily once the chunk BE exists
        sessionCount++;

        // Seen-memory gate: chunk reloads, module re-toggles and relogs all
        // re-deliver the same chunk data. Only the FIRST time a position is
        // ever seen announces it — past spawners stay silent.
        boolean firstTime = seen.add(key(pos));
        if (!firstTime) return;

        LucidClient instance = LucidClient.getInstance();
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        String label = null;
        if (client.world != null
                && client.world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
            label = resolveMobType(spawner, client.world);
        }
        if (label != null) found.put(pos, label);

        // Persist immediately so a relog can't re-announce this find.
        saveSeen();

        int distance = player == null ? -1
            : (int) Math.sqrt(player.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        String what = label != null ? label : "Spawner";
        String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        String msg = what + " at " + where
            + (distance >= 0 ? " (" + distance + "m away)" : "")
            + " — session total: " + sessionCount;

        // A stale saved value (an old channel name) still alerts, so removing
        // the alert channel can never silence an existing config.
        if (!"None".equals(notifyMode.get()) && player != null) {
            player.sendMessage(Text.literal("[SpawnerESP] " + msg), false);
        }

        if (webhook.isEnabled()) {
            String url = webhookUrl.get();
            if (url != null && !url.isBlank()) {
                String server = client.getCurrentServerEntry() != null
                    ? String.valueOf(client.getCurrentServerEntry().address) : "unknown";
                String content = (pingOnWebhook.isEnabled() ? "@everyone " : "")
                    + "Spawner found on " + server + ": " + msg;
                CompletableFuture.runAsync(() -> sendWebhook(url, content));
            }
        }

        if (disconnectOnFind.isEnabled()) {
            setEnabled(false);
            client.disconnect(Text.literal("SpawnerESP: " + msg));
        }
    }

    /** Minimal Discord webhook POST (best-effort, never blocks the game). */
    private static void sendWebhook(String url, String content) {
        try {
            String json = "{\"content\":" + jsonString(content) + "}";
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
            // Webhook failures must never surface in-game.
        }
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    // ════════════════════════════════════════════════════════════════
    //  TICK — legacy merge scan (Only New off), label resolution, prune
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            found.clear();
            return;
        }

        // Only New off → also merge pre-existing spawners (render only).
        if (!onlyNew.isEnabled()) {
            legacyScan(client);
        }

        // Resolve pending labels (chunk BE now exists for older finds).
        if (client.world != null) {
            int resolved = 0;
            for (Map.Entry<BlockPos, String> e : found.entrySet()) {
                if (e.getValue() != null) continue;
                if (client.world.getBlockEntity(e.getKey()) instanceof MobSpawnerBlockEntity spawner) {
                    e.setValue(resolveMobType(spawner, client.world));
                    if (++resolved >= 8) break; // cap work per tick
                }
            }
        }

        // Periodic prune of out-of-range entries.
        if (++pruneTicks >= PRUNE_INTERVAL_TICKS) {
            pruneTicks = 0;
            double r = range.get();
            double r2 = r * r;
            Iterator<Map.Entry<BlockPos, String>> it = found.entrySet().iterator();
            while (it.hasNext()) {
                BlockPos p = it.next().getKey();
                double dx = p.getX() + 0.5 - client.player.getX();
                double dy = p.getY() + 0.5 - client.player.getY();
                double dz = p.getZ() + 0.5 - client.player.getZ();
                if (dx * dx + dy * dy + dz * dz > r2) it.remove();
            }
        }
    }

    /** The old per-tick scan — merges existing spawners without alerting. */
    private void legacyScan(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        double r = range.get();
        double r2 = r * r;
        int chunkRadius = (int) Math.ceil(r / 16.0);
        int pcx = player.getBlockPos().getX() >> 4;
        int pcz = player.getBlockPos().getZ() >> 4;

        for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
            for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                WorldChunk chunk = world.getChunk(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof MobSpawnerBlockEntity spawner)) continue;
                    BlockPos pos = be.getPos();
                    double dx = pos.getX() + 0.5 - player.getX();
                    double dy = pos.getY() + 0.5 - player.getY();
                    double dz = pos.getZ() + 0.5 - player.getZ();
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    found.putIfAbsent(pos.toImmutable(), resolveMobType(spawner, world));
                }
            }
        }
    }

    /** Resolves the spawner's mob type via the cached preview entity. */
    private String resolveMobType(MobSpawnerBlockEntity spawner, ClientWorld world) {
        try {
            MobSpawnerLogic logic = spawner.getLogic();
            if (logic == null || world == null) return "Spawner";

            Entity preview = logic.getRenderedEntity(world, spawner.getPos());
            if (preview == null) return "Spawner";

            EntityType<?> type = preview.getType();
            if (type == null) return "Spawner";

            Text name = type.getName();
            if (name == null) return "Spawner";

            String s = name.getString();
            return (s == null || s.isEmpty()) ? "Spawner" : s + " Spawner";
        } catch (Throwable t) {
            // Spawners with no entity set / corrupted NBT / mid-chunk-load
            // → log + fall back safely instead of crashing the game.
            LucidClient.LOGGER.error(
                "SpawnerESP failed to resolve mob type: {}", t.getMessage());
            return "Spawner";
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  RENDER — 3D boxes + tracers (WorldRenderEvents path)
    // ════════════════════════════════════════════════════════════════
    public void renderWorld(WorldRenderContext wrc) {
        if (!isEnabled() || found.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;

        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int boxColor = color.getColor();
        int tColor = traceColor.getColor();

        WorldRender3D.begin(matrices, camera);
        try {
            for (BlockPos bp : found.keySet()) {
                Box box = new Box(bp.getX(), bp.getY(), bp.getZ(),
                                  bp.getX() + 1, bp.getY() + 1, bp.getZ() + 1);
                // Wireframe box (depth-tested so it sits in the world).
                WorldRender3D.drawBox(consumers, box, boxColor);

                // Tracer line from the camera to the spawner centre.
                if (trace.isEnabled()) {
                    Vec3d to = Vec3d.ofCenter(bp);
                    WorldRender3D.drawTracer(consumers, camera, to, tColor);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HUD render — screen-space labels
    // ════════════════════════════════════════════════════════════════
    public void draw(DrawContext ctx, float tickDelta) {
        if (!isEnabled() || !showLabel.isEnabled() || found.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;
        Vec3d camPos = camera.getCameraPos();

        Matrix4f projMatrix = client.gameRenderer.getBasicProjectionMatrix(tickDelta);
        Matrix4f viewMatrix = new Matrix4f().identity();
        Quaternionf invRot = new Quaternionf(camera.getRotation()).invert();
        viewMatrix.rotate(invRot);
        viewMatrix.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);
        Matrix4f vpMatrix = new Matrix4f(projMatrix).mul(viewMatrix);

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        int boxColor = color.getColor();

        for (Map.Entry<BlockPos, String> e : found.entrySet()) {
            BlockPos bp = e.getKey();
            // Project the spawner centre to screen space for the label anchor.
            Vec3d center = Vec3d.ofCenter(bp);
            Vector4f v = new Vector4f((float) center.x, (float) center.y, (float) center.z, 1f);
            vpMatrix.transform(v);
            if (v.w <= 0.05f) continue;
            float ndcZ = v.z / v.w;
            if (ndcZ < -1f || ndcZ > 1f) continue;
            float sx = (v.x / v.w * 0.5f + 0.5f) * screenW;
            float sy = (0.5f - v.y / v.w * 0.5f) * screenH;

            String label = e.getValue() != null ? e.getValue() : "Spawner";
            int lw = tr.getWidth(label);
            int lh = tr.fontHeight + 4;
            int tx = (int) sx - lw / 2;
            int ty = (int) sy - lh - 4; // float the label above the box
            if (tx < 2) tx = 2;
            if (ty < 2) ty = (int) sy + 4;

            int bg = Colors.withAlpha(0x111111, 210);
            RenderUtils.drawSoftShadow(ctx, tx - 4, ty, lw + 8, lh,
                com.lucidclient.ui.theme.LucidTheme.RADIUS_SMALL, 3, 50);
            RenderUtils.drawRoundedRect(ctx, tx - 4, ty, lw + 8, lh,
                com.lucidclient.ui.theme.LucidTheme.RADIUS_SMALL, bg);
            RenderUtils.drawRoundedRectBorder(ctx, tx - 4, ty, lw + 8, lh,
                com.lucidclient.ui.theme.LucidTheme.RADIUS_SMALL, boxColor, 1);
            RenderUtils.drawText(ctx, tr, label, tx, ty + 2,
                Colors.TEXT_INVERTED, false);
        }
    }
}
