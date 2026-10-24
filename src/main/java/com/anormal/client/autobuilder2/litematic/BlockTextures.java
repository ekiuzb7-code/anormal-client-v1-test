package com.anormal.client.autobuilder2.litematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bakes each block type into the three textures an isometric cube needs.
 *
 * <p>Rather than using the block's map colour, this walks the real block model: for the up, south
 * and east faces it collects the model's quads, loads the texture behind each sprite and composites
 * the layers onto each other, applying that quad's tint from {@link BlockColors}. That is what makes
 * a grass block come out as green on top with a dirt-and-grass side, exactly as in game, and it is
 * also how multi-layer models (grass overlays, waterlogged plants, glazed terracotta patterns) end
 * up correct.
 *
 * <p>Everything is defensive. A block from a mod that is not installed, a sprite whose texture
 * cannot be read, or a model that fails to bake all fall back to the average colour of the block's
 * particle texture and finally to its map colour, so a preview always renders.
 */
public final class BlockTextures {
    /** Edge length of the baked face textures handed to the renderer. */
    public static final int SIZE = 16;

    /** The three textures of a cube, plus whether it can hide the blocks behind it. */
    public static final class Appearance {
        public final int[] top = new int[SIZE * SIZE];
        public final int[] south = new int[SIZE * SIZE];
        public final int[] east = new int[SIZE * SIZE];
        public final boolean opaque;

        Appearance(boolean opaque) {
            this.opaque = opaque;
        }

        void fill(int argb) {
            Arrays.fill(this.top, argb);
            Arrays.fill(this.south, argb);
            Arrays.fill(this.east, argb);
        }
    }

    private record Texture(int[] pixels, int width, int frameHeight) {
        /** Cached marker for "this sprite has no readable texture". */
        static final Texture EMPTY = new Texture(new int[0], 0, 0);
    }

    private final Map<String, Appearance> cache = new HashMap<>();
    private final Map<Identifier, Texture> textures = new HashMap<>();

    /** Resolves the whole palette, leaving index 0 (empty space) null. */
    public Appearance[] resolve(List<String> palette) {
        Appearance[] appearances = new Appearance[palette.size()];
        for (int i = 1; i < palette.size(); i++) {
            appearances[i] = of(palette.get(i));
        }
        return appearances;
    }

    public Appearance of(String blockName) {
        Appearance cached = this.cache.get(blockName);
        if (cached != null) {
            return cached;
        }
        Appearance appearance = bake(blockName);
        this.cache.put(blockName, appearance);
        return appearance;
    }

    // ------------------------------------------------------------------
    // Baking
    // ------------------------------------------------------------------

    private Appearance bake(String blockName) {
        Identifier id = Identifier.tryParse(blockName);
        Block block = id == null ? null : Registries.BLOCK.getOptionalValue(id).orElse(null);
        MinecraftClient client = MinecraftClient.getInstance();
        if (block == null || client == null) {
            return flat(fallbackColor(blockName), false);
        }

        BlockState state = block.getDefaultState();
        boolean opaque = false;
        try {
            opaque = state.isOpaqueFullCube();
        } catch (Throwable ignored) {
            // Some blocks need a world to answer this; assume not opaque.
        }

        int[] top = new int[SIZE * SIZE];
        int[] south = new int[SIZE * SIZE];
        int[] east = new int[SIZE * SIZE];
        boolean textured = false;
        try {
            BlockModels models = client.getBlockRenderManager().getModels();
            BlockStateModel model = models.getModel(state);
            if (model != null) {
                List<BlockModelPart> parts = model.getParts(Random.create(0L));
                textured |= bakeFace(parts, state, client, Direction.UP, top);
                textured |= bakeFace(parts, state, client, Direction.SOUTH, south);
                textured |= bakeFace(parts, state, client, Direction.EAST, east);
            }
        } catch (Throwable ignored) {
            // Fall through to a flat colour.
        }

        if (!textured) {
            return flat(fallbackColor(blockName), opaque);
        }
        // A cube can only hide the blocks behind it when all of its faces are fully opaque.
        Appearance appearance = new Appearance(opaque && isOpaque(top) && isOpaque(south) && isOpaque(east));
        System.arraycopy(top, 0, appearance.top, 0, top.length);
        System.arraycopy(south, 0, appearance.south, 0, south.length);
        System.arraycopy(east, 0, appearance.east, 0, east.length);
        return appearance;
    }

    /** Composites every quad of one face onto {@code target}; returns true if anything was drawn. */
    private boolean bakeFace(List<BlockModelPart> parts, BlockState state, MinecraftClient client,
                             Direction direction, int[] target) {
        boolean drawn = false;
        BlockColors colors = client.getBlockColors();
        for (BlockModelPart part : parts) {
            List<BakedQuad> quads;
            try {
                quads = part.getQuads(direction);
            } catch (Throwable ignored) {
                continue;
            }
            if (quads == null) {
                continue;
            }
            for (BakedQuad quad : quads) {
                Sprite sprite = quad.sprite();
                if (sprite == null) {
                    continue;
                }
                Texture texture = texture(sprite);
                if (texture == null) {
                    continue;
                }
                int tint = 0xFFFFFF;
                if (quad.hasTint()) {
                    try {
                        int value = colors.getColor(state, null, null, Math.max(0, quad.tintIndex()));
                        if ((value & 0xFFFFFF) != 0) {
                            tint = value & 0xFFFFFF;
                        }
                    } catch (Throwable ignored) {
                        // Keep the untinted colour.
                    }
                }
                composite(target, texture, tint);
                drawn = true;
            }
        }
        return drawn;
    }

    private static void composite(int[] target, Texture texture, int tint) {
        int red = (tint >> 16) & 0xFF;
        int green = (tint >> 8) & 0xFF;
        int blue = tint & 0xFF;
        for (int y = 0; y < SIZE; y++) {
            int sourceY = y * texture.frameHeight / SIZE;
            for (int x = 0; x < SIZE; x++) {
                int sourceX = x * texture.width / SIZE;
                int source = texture.pixels[sourceY * texture.width + sourceX];
                int alpha = (source >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int sr = ((source >> 16) & 0xFF) * red / 255;
                int sg = ((source >> 8) & 0xFF) * green / 255;
                int sb = (source & 0xFF) * blue / 255;
                target[y * SIZE + x] = over(target[y * SIZE + x], sr, sg, sb, alpha);
            }
        }
    }

    /** Source-over composite of a straight-alpha colour onto a destination pixel. */
    private static int over(int destination, int sr, int sg, int sb, int alpha) {
        int da = (destination >>> 24) & 0xFF;
        if (da == 0) {
            return alpha << 24 | sr << 16 | sg << 8 | sb;
        }
        if (alpha == 255) {
            return 0xFF000000 | sr << 16 | sg << 8 | sb;
        }
        float sa = alpha / 255.0F;
        float base = da / 255.0F * (1.0F - sa);
        float outAlpha = sa + base;
        if (outAlpha <= 0.0F) {
            return 0;
        }
        int dr = (destination >> 16) & 0xFF;
        int dg = (destination >> 8) & 0xFF;
        int db = destination & 0xFF;
        int r = clamp((int) ((sr * sa + dr * base) / outAlpha));
        int g = clamp((int) ((sg * sa + dg * base) / outAlpha));
        int b = clamp((int) ((sb * sa + db * base) / outAlpha));
        return clamp((int) (outAlpha * 255.0F)) << 24 | r << 16 | g << 8 | b;
    }

    /** True when every pixel of a baked face is fully opaque. */
    private static boolean isOpaque(int[] pixels) {
        for (int pixel : pixels) {
            if (((pixel >>> 24) & 0xFF) < 250) {
                return false;
            }
        }
        return true;
    }

    private static Appearance flat(int argb, boolean opaque) {
        Appearance appearance = new Appearance(opaque);
        appearance.fill(argb);
        return appearance;
    }

    // ------------------------------------------------------------------
    // Textures
    // ------------------------------------------------------------------

    private Texture texture(Sprite sprite) {
        Identifier spriteId = sprite.getContents().getId();
        Texture cached = this.textures.get(spriteId);
        if (cached != null) {
            return cached == Texture.EMPTY ? null : cached;
        }
        Texture texture = load(spriteId);
        this.textures.put(spriteId, texture == null ? Texture.EMPTY : texture);
        return texture;
    }

    private static Texture load(Identifier spriteId) {
        try {
            Identifier id = Identifier.of(spriteId.getNamespace(),
                    "textures/" + spriteId.getPath() + ".png");
            Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream stream = resource.get().getInputStream();
                 NativeImage image = NativeImage.read(stream)) {
                int width = image.getWidth();
                int height = image.getHeight();
                // Animated textures stack their frames vertically; only the first frame is used.
                int frameHeight = height > width && height % width == 0 ? width : height;
                int[] pixels = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        pixels[y * width + x] = image.getColorArgb(x, y);
                    }
                }
                return new Texture(pixels, width, frameHeight);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Flat colour fallback, matching how Minecraft shades block break particles
    // ------------------------------------------------------------------

    private int fallbackColor(String blockName) {
        int fallback = hashColor(blockName);
        Identifier id = Identifier.tryParse(blockName);
        Block block = id == null ? null : Registries.BLOCK.getOptionalValue(id).orElse(null);
        if (block == null) {
            return opaque(fallback);
        }
        BlockState state = block.getDefaultState();
        int mapColor = mapColor(state, block, fallback);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return opaque(mapColor);
        }
        try {
            BlockModels models = client.getBlockRenderManager().getModels();
            Sprite sprite = models.getModelParticleSprite(state);
            if (sprite == null) {
                return opaque(mapColor);
            }
            Texture texture = texture(sprite);
            if (texture == null) {
                return opaque(mapColor);
            }
            int average = average(texture);
            if (average == 0) {
                return opaque(mapColor);
            }
            int composed = average;
            int tint = 0;
            try {
                tint = client.getBlockColors().getParticleColor(state, null, null);
            } catch (Throwable ignored) {
                // No tint.
            }
            if ((tint & 0xFFFFFF) != 0 && (tint & 0xFFFFFF) != (mapColor & 0xFFFFFF)) {
                composed = multiply(average, tint);
            }
            if (isGray(composed, 14) && !isGray(mapColor, 24)) {
                composed = tintTo(mapColor, luminance(composed));
            }
            return opaque(composed);
        } catch (Throwable ignored) {
            return opaque(mapColor);
        }
    }

    private static int mapColor(BlockState state, Block block, int fallback) {
        try {
            MapColor color = state.getMapColor(null, null);
            if (color != null && color.color != 0) {
                return color.color;
            }
        } catch (Throwable ignored) {
            // Some blocks dereference the world/pos here.
        }
        try {
            MapColor color = block.getDefaultMapColor();
            if (color != null && color.color != 0) {
                return color.color;
            }
        } catch (Throwable ignored) {
            // Fall through.
        }
        return fallback;
    }

    private static int average(Texture texture) {
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        for (int y = 0; y < texture.frameHeight; y++) {
            for (int x = 0; x < texture.width; x++) {
                int pixel = texture.pixels[y * texture.width + x];
                if (((pixel >>> 24) & 0xFF) < 24) {
                    continue;
                }
                red += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                blue += pixel & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        return (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
    }

    private static int multiply(int rgb, int tint) {
        int red = (((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF)) / 255;
        int green = (((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF)) / 255;
        int blue = ((rgb & 0xFF) * (tint & 0xFF)) / 255;
        return red << 16 | green << 8 | blue;
    }

    private static int tintTo(int mapColor, int brightness) {
        double factor = Math.max(0.62, Math.min(1.28, brightness / 170.0));
        int red = clamp((int) (((mapColor >> 16) & 0xFF) * factor));
        int green = clamp((int) (((mapColor >> 8) & 0xFF) * factor));
        int blue = clamp((int) ((mapColor & 0xFF) * factor));
        return red << 16 | green << 8 | blue;
    }

    private static boolean isGray(int rgb, int threshold) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) < threshold;
    }

    private static int luminance(int rgb) {
        return (int) (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF));
    }

    private static int hashColor(String blockName) {
        int hash = blockName.hashCode();
        return (80 + (hash & 0x7F)) << 16 | (80 + ((hash >>> 8) & 0x7F)) << 8 | (80 + ((hash >>> 16) & 0x7F));
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
