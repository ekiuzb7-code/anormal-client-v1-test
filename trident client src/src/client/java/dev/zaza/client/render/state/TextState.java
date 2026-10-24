package dev.zaza.client.render.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zaza.client.render.Scissor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record TextState(Matrix3x2fc pose, TextureSetup textureSetup, List<Quad> quads, RenderPipeline pipeline,
                        ScreenRectangle scissorArea) implements GuiElementRenderState {
    private static final Map<AbstractTexture, TextureSetup> SETUPS = new HashMap<>();

    public record Quad(float left, float top, float right, float bottom,
                       float u0, float v0, float u1, float v1, int leftColor, int rightColor) {
        public Quad(float left, float top, float right, float bottom,
                    float u0, float v0, float u1, float v1, int color) {
            this(left, top, right, bottom, u0, v0, u1, v1, color, color);
        }
    }

    public static TextState of(GuiGraphicsExtractor graphics, Identifier atlas, List<Quad> quads) {
        if (quads.isEmpty()) {
            return new TextState(new Matrix3x2f(graphics.pose()), TextureSetup.noTexture(), List.of(),
                    Pipelines.TEXT, Scissor.current());
        }
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (Quad quad : quads) {
            left = Math.min(left, quad.left());
            top = Math.min(top, quad.top());
            right = Math.max(right, quad.right());
            bottom = Math.max(bottom, quad.bottom());
        }
        var pose = new Matrix3x2f(graphics.pose());
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(atlas);
        TextureSetup setup = SETUPS.computeIfAbsent(texture, source ->
                TextureSetup.singleTexture(source.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
        return new TextState(pose, setup, List.copyOf(quads), Pipelines.TEXT, Scissor.current());
    }

    @Override
    public ScreenRectangle bounds() {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (Quad quad : quads) {
            left = Math.min(left, quad.left());
            top = Math.min(top, quad.top());
            right = Math.max(right, quad.right());
            bottom = Math.max(bottom, quad.bottom());
        }
        if (left > right) {
            return new ScreenRectangle(0, 0, 0, 0).transformMaxBounds(pose);
        }
        return new ScreenRectangle((int) Math.floor(left), (int) Math.floor(top),
                (int) Math.ceil(right) - (int) Math.floor(left),
                (int) Math.ceil(bottom) - (int) Math.floor(top)).transformMaxBounds(pose);
    }

    @Override
    public void buildVertices(VertexConsumer vertices) {
        for (Quad quad : quads) {
            vertex(vertices, quad.left(), quad.top(), quad.u0(), quad.v0(), quad.leftColor());
            vertex(vertices, quad.left(), quad.bottom(), quad.u0(), quad.v1(), quad.leftColor());
            vertex(vertices, quad.right(), quad.bottom(), quad.u1(), quad.v1(), quad.rightColor());
            vertex(vertices, quad.right(), quad.top(), quad.u1(), quad.v0(), quad.rightColor());
        }
    }

    private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color) {
        vertices.addVertexWith2DPose(pose, x, y).setUv(u, v).setColor(color);
    }
}
