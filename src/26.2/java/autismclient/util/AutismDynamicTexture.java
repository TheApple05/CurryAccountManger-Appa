package autismclient.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;

/** GPU resources use the selected Minecraft version's rendering API. */
final class AutismDynamicTexture extends AbstractTexture {
    final NativeImage pixels;
    AutismDynamicTexture(String name, NativeImage image) {
        pixels = image;
        texture = RenderSystem.getDevice().createTexture(name, 5, GpuFormat.RGBA8_UNORM, image.getWidth(), image.getHeight(), 1, 1);
        sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        textureView = RenderSystem.getDevice().createTextureView(texture);
        upload();
    }
    void upload() {
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, pixels);
    }
    @Override public void close() {
        pixels.close();
        super.close();
    }
}
