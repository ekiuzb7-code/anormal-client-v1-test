package dev.autocc.client.litematic;

import net.minecraft.client.texture.NativeImage;

/**
 * Rasterises a voxel volume into a {@link NativeImage} using a 2:1 isometric projection, so a
 * decoded schematic looks like a small screenshot of the build.
 *
 * <p>The projection maps a grid step of {@code +x} to {@code (+2, +1)}, {@code +z} to
 * {@code (-2, +1)} and {@code +y} to {@code (0, -2)} screen units, scaled by {@code s}. A block
 * therefore becomes a {@code 2s}-wide top rhombus plus two vertical side faces, and the cube that
 * encloses it is {@code 4s} by {@code 4s} pixels.
 *
 * <p>Each of the three faces is drawn as a textured parallelogram: the face has an origin and two
 * vectors, one per unit of texture U and V, and the inverse of that little 2×2 basis turns every
 * covered pixel back into a texture coordinate. That keeps the projection exact while still showing
 * the real block textures, and because pixels are composited source-over the semi-transparent parts
 * of glass, ice and overlays blend with whatever was drawn behind them.
 *
 * <p>Blocks are painted back to front, ordered by {@code x + y + z}, which for this projection is
 * exactly the depth order (larger sums are closer to the viewer).
 */
public final class IsoRenderer {
    /** Longest side of the generated image, in pixels. */
    private static final int MAX_PIXELS = 512;
    private static final int MAX_SCALE = 16;

    /** Vertical face shading, mirroring the light levels Minecraft applies to the top and sides. */
    private static final float TOP_SHADE = 1.0F;
    private static final float SOUTH_SHADE = 0.80F;
    private static final float EAST_SHADE = 0.62F;

    /** A voxel grid, already rotated into the orientation that should be rendered. */
    public static final class Volume {
        public final int width;
        public final int height;
        public final int length;
        public final int[] voxels;

        Volume(int width, int height, int length, int[] voxels) {
            this.width = width;
            this.height = height;
            this.length = length;
            this.voxels = voxels;
        }
    }

    /** A rendered image together with the exact region of it that contains the build. */
    public static final class Result {
        public final NativeImage image;
        public final int width;
        public final int height;
        public final int scale;

        Result(NativeImage image, int width, int height, int scale) {
            this.image = image;
            this.width = width;
            this.height = height;
            this.scale = scale;
        }
    }

    private final double[] cornerX = new double[4];
    private final double[] cornerY = new double[4];

    /**
     * Rotates a decoded schematic 90° clockwise {@code rotation} times around the vertical axis.
     * Rotation is applied to the grid rather than to the camera so that the painter's algorithm can
     * keep working on plain grid coordinates.
     */
    public static Volume rotate(LitematicaFile file, int rotation) {
        int sourceWidth = file.width;
        int sourceLength = file.length;
        int height = file.height;
        boolean swap = (rotation & 1) == 1;
        int width = swap ? sourceLength : sourceWidth;
        int length = swap ? sourceWidth : sourceLength;
        int[] target = new int[width * height * length];
        int[] source = file.voxels;

        for (int y = 0; y < height; y++) {
            int layer = y * sourceWidth * sourceLength;
            for (int z = 0; z < sourceLength; z++) {
                for (int x = 0; x < sourceWidth; x++) {
                    int value = source[layer + z * sourceWidth + x];
                    if (value == 0) {
                        continue;
                    }
                    int nx;
                    int nz;
                    switch (rotation & 3) {
                        case 1 -> {
                            nx = z;
                            nz = sourceWidth - 1 - x;
                        }
                        case 2 -> {
                            nx = sourceWidth - 1 - x;
                            nz = sourceLength - 1 - z;
                        }
                        case 3 -> {
                            nx = sourceLength - 1 - z;
                            nz = x;
                        }
                        default -> {
                            nx = x;
                            nz = z;
                        }
                    }
                    target[(y * length + nz) * width + nx] = value;
                }
            }
        }
        return new Volume(width, height, length, target);
    }

    public Result render(Volume volume, BlockTextures.Appearance[] appearances, boolean topDown) {
        return topDown ? renderTopDown(volume, appearances) : renderIsometric(volume, appearances);
    }

    // ------------------------------------------------------------------
    // Isometric
    // ------------------------------------------------------------------

    private Result renderIsometric(Volume volume, BlockTextures.Appearance[] appearances) {
        int width = volume.width;
        int height = volume.height;
        int length = volume.length;
        int scale = chooseScale(width, height, length);

        int imageWidth = Math.max(1, 2 * (width + length) * scale);
        int imageHeight = Math.max(1, (width + length + 2 * height) * scale);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, imageWidth, imageHeight, true);

        double offsetX = 2.0 * length * scale;
        double offsetY = 2.0 * height * scale;
        double s = scale;

        int[] voxels = volume.voxels;
        int maxDepth = (width - 1) + (height - 1) + (length - 1);
        for (int depth = 0; depth <= maxDepth; depth++) {
            int xMin = Math.max(0, depth - (height - 1) - (length - 1));
            int xMax = Math.min(width - 1, depth);
            for (int x = xMin; x <= xMax; x++) {
                int remainder = depth - x;
                int yMin = Math.max(0, remainder - (length - 1));
                int yMax = Math.min(height - 1, remainder);
                for (int y = yMin; y <= yMax; y++) {
                    int z = remainder - y;
                    int value = voxels[(y * length + z) * width + x];
                    if (value <= 0 || value >= appearances.length || appearances[value] == null) {
                        continue;
                    }
                    if (isEnclosed(voxels, appearances, width, height, length, x, y, z)) {
                        continue;
                    }
                    BlockTextures.Appearance appearance = appearances[value];
                    double px = offsetX + 2.0 * (x - z) * s;
                    double py = offsetY + ((x + z) - 2.0 * (y + 1)) * s;

                    // Top face: origin at the block's (0,0) corner, U along +x, V along +z.
                    face(image, imageWidth, imageHeight, px, py, 2 * s, s, -2 * s, s,
                            appearance.top, TOP_SHADE);
                    // South face (+z, screen left): origin at the top-left corner, U along +x, V down.
                    face(image, imageWidth, imageHeight, px - 2 * s, py + s, 2 * s, s, 0, 2 * s,
                            appearance.south, SOUTH_SHADE);
                    // East face (+x, screen right): origin at the top-right corner, U along +z, V down.
                    face(image, imageWidth, imageHeight, px + 2 * s, py + s, -2 * s, s, 0, 2 * s,
                            appearance.east, EAST_SHADE);
                }
            }
        }
        return new Result(image, imageWidth, imageHeight, scale);
    }

    /**
     * Fills one parallelogram with a texture. {@code (ox, oy)} is the corner at texture (0,0), and
     * {@code (ux, uy)} / {@code (vx, vy)} are the screen offsets of one full unit of texture U and V.
     */
    private void face(NativeImage image, int imageWidth, int imageHeight,
                      double ox, double oy, double ux, double uy, double vx, double vy,
                      int[] texture, float shade) {
        double determinant = ux * vy - uy * vx;
        if (Math.abs(determinant) < 1e-9) {
            return;
        }
        this.cornerX[0] = ox;
        this.cornerY[0] = oy;
        this.cornerX[1] = ox + ux;
        this.cornerY[1] = oy + uy;
        this.cornerX[2] = ox + ux + vx;
        this.cornerY[2] = oy + uy + vy;
        this.cornerX[3] = ox + vx;
        this.cornerY[3] = oy + vy;

        double minY = Math.min(Math.min(this.cornerY[0], this.cornerY[1]),
                Math.min(this.cornerY[2], this.cornerY[3]));
        double maxY = Math.max(Math.max(this.cornerY[0], this.cornerY[1]),
                Math.max(this.cornerY[2], this.cornerY[3]));
        int first = Math.max(0, (int) Math.floor(minY));
        int last = Math.min(imageHeight - 1, (int) Math.ceil(maxY));
        int size = BlockTextures.SIZE;

        for (int row = first; row <= last; row++) {
            double scan = row + 0.5;
            double left = Double.MAX_VALUE;
            double right = -Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                double ay = this.cornerY[i];
                double by = this.cornerY[j];
                if (ay == by) {
                    continue;
                }
                double t = (scan - ay) / (by - ay);
                if (t < 0.0 || t > 1.0) {
                    continue;
                }
                double cx = this.cornerX[i] + (this.cornerX[j] - this.cornerX[i]) * t;
                if (cx < left) {
                    left = cx;
                }
                if (cx > right) {
                    right = cx;
                }
            }
            if (left > right) {
                continue;
            }
            int from = Math.max(0, (int) Math.floor(left));
            int to = Math.min(imageWidth - 1, (int) Math.ceil(right));
            for (int column = from; column <= to; column++) {
                double px = column + 0.5 - ox;
                double py = scan - oy;
                double u = (px * vy - py * vx) / determinant;
                double v = (ux * py - uy * px) / determinant;

                int tu = (int) (u * size);
                int tv = (int) (v * size);
                if (tu < 0) {
                    tu = 0;
                } else if (tu >= size) {
                    tu = size - 1;
                }
                if (tv < 0) {
                    tv = 0;
                } else if (tv >= size) {
                    tv = size - 1;
                }

                int source = texture[tv * size + tu];
                int alpha = (source >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }
                int sr = clamp((int) (((source >> 16) & 0xFF) * shade));
                int sg = clamp((int) (((source >> 8) & 0xFF) * shade));
                int sb = clamp((int) ((source & 0xFF) * shade));
                image.setColorArgb(column, row, over(image.getColorArgb(column, row), sr, sg, sb, alpha));
            }
        }
    }

    // ------------------------------------------------------------------
    // Top down height map
    // ------------------------------------------------------------------

    private Result renderTopDown(Volume volume, BlockTextures.Appearance[] appearances) {
        int width = volume.width;
        int length = volume.length;
        int cell = Math.max(1, Math.min(10, MAX_PIXELS / Math.max(width, length)));
        int imageWidth = Math.max(1, width * cell);
        int imageHeight = Math.max(1, length * cell);

        int[] voxels = volume.voxels;
        int[] surface = new int[width * length];
        int[] surfaceY = new int[width * length];
        int tallest = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                for (int y = volume.height - 1; y >= 0; y--) {
                    int value = voxels[(y * length + z) * width + x];
                    if (value > 0) {
                        int index = z * width + x;
                        surface[index] = value;
                        surfaceY[index] = y;
                        tallest = Math.max(tallest, y);
                        break;
                    }
                }
            }
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, imageWidth, imageHeight, true);
        int size = BlockTextures.SIZE;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int value = surface[index];
                if (value <= 0 || value >= appearances.length || appearances[value] == null) {
                    continue;
                }
                // Tint by height so a flat plan view still reads as a height map.
                float shade = tallest <= 0 ? 1.0F : 0.78F + 0.30F * (surfaceY[index] / (float) tallest);
                int[] texture = appearances[value].top;
                for (int ty = 0; ty < cell; ty++) {
                    int row = z * cell + ty;
                    int sourceY = ty * size / cell;
                    for (int tx = 0; tx < cell; tx++) {
                        int sourceX = tx * size / cell;
                        int source = texture[sourceY * size + sourceX];
                        int alpha = (source >>> 24) & 0xFF;
                        if (alpha == 0) {
                            continue;
                        }
                        int sr = clamp((int) (((source >> 16) & 0xFF) * shade));
                        int sg = clamp((int) (((source >> 8) & 0xFF) * shade));
                        int sb = clamp((int) ((source & 0xFF) * shade));
                        int column = x * cell + tx;
                        image.setColorArgb(column, row,
                                over(image.getColorArgb(column, row), sr, sg, sb, alpha));
                    }
                }
            }
        }
        return new Result(image, imageWidth, imageHeight, cell);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * True when the block is completely hidden by its three neighbours towards the camera.
     *
     * <p>This projection only ever shows a cube's {@code +x}, {@code +y} and {@code +z} faces, so
     * those three neighbours are the only possible occluders — and when all three are solid their
     * cubes provably cover this one's silhouette, because each is the same hexagon offset by
     * {@code (+2s, +s)}, {@code (0, -2s)} and {@code (-2s, +s)}. Testing the three far neighbours as
     * well would wrongly cull blocks on the lowest-x/y/z edge of the grid, which are still visible
     * from outside the build.
     */
    private static boolean isEnclosed(int[] voxels, BlockTextures.Appearance[] appearances,
                                      int width, int height, int length, int x, int y, int z) {
        return solid(voxels, appearances, width, height, length, x + 1, y, z)
                && solid(voxels, appearances, width, height, length, x, y + 1, z)
                && solid(voxels, appearances, width, height, length, x, y, z + 1);
    }

    /** Only fully opaque blocks may occlude, so glass and water never hide what is behind them. */
    private static boolean solid(int[] voxels, BlockTextures.Appearance[] appearances,
                                 int width, int height, int length, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) {
            return false;
        }
        int value = voxels[(y * length + z) * width + x];
        return value > 0 && value < appearances.length
                && appearances[value] != null && appearances[value].opaque;
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

    private static int chooseScale(int width, int height, int length) {
        int horizontal = 2 * (width + length);
        int vertical = width + length + 2 * height;
        int scale = Math.min(MAX_PIXELS / Math.max(1, horizontal), MAX_PIXELS / Math.max(1, vertical));
        return Math.max(1, Math.min(MAX_SCALE, scale));
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
