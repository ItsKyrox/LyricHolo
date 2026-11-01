package dev.lyricholo;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import dev.lyricholo.mixin.RenderLayerAccessor;
import dev.lyricholo.mixin.RenderSetupAccessor;
import dev.lyricholo.mixin.RenderTextureSpecAccessor;

import java.util.HashMap;
import java.util.Map;

/** Fog-free text rendering that still uses the world's depth buffer for block occlusion. */
final class SkyTextRenderer {
    // Minecraft font atlas layers are stable for each loaded font; cache the custom
    // equivalents so rendering each lyric word does not rebuild render setup objects.
    private static final Map<RenderLayer, RenderLayer> DEPTH_LAYERS = new HashMap<>();
    private static final RenderPipeline COLOR_PIPELINE = createPipeline("sky_text", false);
    private static final RenderPipeline INTENSITY_PIPELINE = createPipeline("sky_text_intensity", true);

    private SkyTextRenderer() {}

    static void draw(TextRenderer renderer, OrderedText text, float x, float y, int color, boolean shadow,
                     MatrixStack matrices, VertexConsumerProvider consumers, int background, int light) {
        VertexConsumerProvider foglessDepthConsumers = new VertexConsumerProvider() {
            @Override
            public VertexConsumer getBuffer(RenderLayer requested) {
                RenderLayer depthLayer = DEPTH_LAYERS.computeIfAbsent(requested, SkyTextRenderer::depthLayer);
                return consumers.getBuffer(depthLayer);
            }
        };
        renderer.draw(text, x, y, color, shadow, matrices.peek().getPositionMatrix(),
                foglessDepthConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
    }

    private static RenderLayer depthLayer(RenderLayer original) {
        RenderSetup originalSetup = ((RenderLayerAccessor)original).lyricholo$getRenderSetup();
        RenderSetupAccessor setup = (RenderSetupAccessor)(Object)originalSetup;
        Object glyphAtlas = setup.lyricholo$getTextures().get("Sampler0");
        if (glyphAtlas == null) return original;

        RenderTextureSpecAccessor textureSpec = (RenderTextureSpecAccessor)glyphAtlas;
        Identifier atlas = textureSpec.lyricholo$getLocation();
        boolean intensity = original.getRenderPipeline().getLocation().getPath().contains("text_intensity");
        RenderPipeline pipeline = intensity ? INTENSITY_PIPELINE : COLOR_PIPELINE;
        RenderSetup.Builder builder = RenderSetup.builder(pipeline)
                .texture("Sampler0", atlas, textureSpec.lyricholo$getSampler());
        return RenderLayer.of("lyricholo:sky_text", builder.build());
    }

    private static RenderPipeline createPipeline(String path, boolean intensity) {
        String shader = intensity ? "core/rendertype_text_intensity_see_through"
                : "core/rendertype_text_see_through";
        return RenderPipeline.builder()
                .withLocation(Identifier.of("lyricholo", "pipeline/" + path))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withSampler("Sampler0")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, VertexFormat.DrawMode.QUADS)
                .build();
    }
}
