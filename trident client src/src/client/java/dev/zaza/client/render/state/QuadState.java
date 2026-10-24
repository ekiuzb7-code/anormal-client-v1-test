package dev.zaza.client.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zaza.client.render.Scissor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;

public record QuadState(Matrix3x2fc pose, float ax, float ay, float bx, float by, float cx, float cy,
                        float dx, float dy, int color, ScreenRectangle scissorArea) implements GuiElementRenderState {
    private static final float PADDING = 1f;

    public QuadState(Matrix3x2fc pose, float ax, float ay, float bx, float by, float cx, float cy,
                     float dx, float dy, int color) {
        this(pose, ax, ay, bx, by, cx, cy, dx, dy, color, Scissor.current());
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        vertex(vertices, ax, ay, color);
        vertex(vertices, bx, by, color);
        vertex(vertices, cx, cy, color);
        vertex(vertices, dx, dy, color);
    }

    private void vertex(VertexConsumer vertices, float x, float y, int vertexColor) {
        vertices.addVertexWith2DPose(pose, x, y).setColor(vertexColor);
    }

    @Override
    public RenderPipeline pipeline() {
        return Pipelines.QUAD;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle bounds() {
        float minX = Math.min(Math.min(ax, bx), Math.min(cx, dx)) - PADDING;
        float minY = Math.min(Math.min(ay, by), Math.min(cy, dy)) - PADDING;
        float maxX = Math.max(Math.max(ax, bx), Math.max(cx, dx)) + PADDING;
        float maxY = Math.max(Math.max(ay, by), Math.max(cy, dy)) + PADDING;
        return new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY)).transformMaxBounds(pose);
    }
}
