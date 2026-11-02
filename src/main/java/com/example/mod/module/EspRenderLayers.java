package com.example.mod.module;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** 1.21.11 line layer with depth testing disabled in its render pipeline. */
final class EspRenderLayers {
    private static final RenderPipeline THROUGH_WALLS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("limitless", "pipeline/esp_lines_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    static final RenderLayer LINES_THROUGH_WALLS = RenderLayer.of(
            "limitless_lines_through_walls",
            RenderSetup.builder(THROUGH_WALLS_PIPELINE).translucent().build()
    );

    private static final RenderPipeline FILLED_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("limitless", "pipeline/filled_through_walls"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withCull(false)
                    .build()
    );

    static final RenderLayer FILLED_THROUGH_WALLS = RenderLayer.of(
            "limitless_filled_through_walls",
            RenderSetup.builder(FILLED_PIPELINE).translucent().build()
    );

    private EspRenderLayers() {}
}

