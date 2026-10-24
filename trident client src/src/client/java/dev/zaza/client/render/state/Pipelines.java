package dev.zaza.client.render.state;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.zaza.ZazaHook;
import dev.zaza.client.render.font.Fonts;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.Optional;

public final class Pipelines {
    public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .build();

    public static final RenderPipeline RECT = builder("rect", "core/rect", "core/rect")
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withVertexBinding(0, SHAPE_FORMAT)
            .build();

    public static final RenderPipeline BORDER = builder("border", "core/rect", "core/border")
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withVertexBinding(0, SHAPE_FORMAT)
            .build();

    public static final RenderPipeline QUAD = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(ZazaHook.id("pipeline/quad"))
            .withCull(false)
            .build();

    public static final RenderPipeline TEXT = builder("text", "core/text", "core/text")
            .withShaderDefine("MSDF_RANGE", Fonts.DISTANCE_RANGE)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .build();

    private static RenderPipeline.Builder builder(String name, String vertexShader, String fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(ZazaHook.id("pipeline/" + name))
                .withVertexShader(ZazaHook.id(vertexShader))
                .withFragmentShader(ZazaHook.id(fragmentShader))
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(Optional.empty())
                .withCull(false);
    }

    private Pipelines() {}
}
