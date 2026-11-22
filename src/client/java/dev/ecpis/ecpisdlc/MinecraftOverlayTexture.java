package dev.ecpis.ecpisdlc;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;

final class MinecraftOverlayTexture {
    private final Identifier id;
    private final String name;
    private DynamicTexture texture;
    private BufferedImage uploadedFrame;
    private int textureWidth;
    private int textureHeight;

    MinecraftOverlayTexture(String name) {
        this.name = name;
        id = Identifier.fromNamespaceAndPath("ecpisdlc", "overlay/" + name);
    }

    void draw(GuiGraphics graphics, BufferedImage image, int x, int y, int width, int height) {
        if (image == null || width <= 0 || height <= 0) return;
        ensureTexture(image.getWidth(), image.getHeight());
        if (uploadedFrame != image) {
            NativeImage pixels = texture.getPixels();
            int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            pixels.fillRect(0, 0, image.getWidth(), image.getHeight(), 0);
            int index = 0;
            for (int py = 0; py < image.getHeight(); py++) {
                for (int px = 0; px < image.getWidth(); px++) {
                    int color = argb[index++];
                    if ((color >>> 24) != 0) pixels.setPixel(px, py, color);
                }
            }
            texture.upload();
            uploadedFrame = image;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0f, 0.0f,
                width, height, textureWidth, textureHeight);
    }

    void close() {
        if (texture == null) return;
        Minecraft.getInstance().getTextureManager().release(id);
        texture = null;
        uploadedFrame = null;
    }

    private void ensureTexture(int width, int height) {
        if (texture != null && textureWidth == width && textureHeight == height) return;
        close();
        textureWidth = width;
        textureHeight = height;
        texture = new DynamicTexture(() -> "EcpisDLC " + name, width, height, false);
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }
}
