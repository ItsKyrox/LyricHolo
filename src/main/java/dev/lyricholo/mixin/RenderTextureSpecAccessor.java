package dev.lyricholo.mixin;

import net.minecraft.client.gl.GpuSampler;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

/** Accesses the package-private font atlas texture spec with remapped field names. */
@Mixin(targets = "net.minecraft.client.render.RenderSetup$TextureSpec")
public interface RenderTextureSpecAccessor {
    @Accessor("location")
    Identifier lyricholo$getLocation();

    @Accessor("sampler")
    Supplier<GpuSampler> lyricholo$getSampler();
}
