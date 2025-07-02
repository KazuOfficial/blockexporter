package com.github.kazuofficial.blockexporter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ExporterItemRenderer implements AutoCloseable {
    private final int textureSize;
    private final File exportDirectory;
    private final Minecraft client;
    private final ExecutorService fileWriteExecutor;
    private final ConcurrentLinkedQueue<ItemStack> failedExports;
    private final Framebuffer framebuffer;

    public ExporterItemRenderer(int textureSize) {
        this.textureSize = textureSize;
        this.client = Minecraft.getInstance();
        this.exportDirectory = new File(client.gameDir, "item_exports");
        int coreCount = Runtime.getRuntime().availableProcessors();
        this.fileWriteExecutor = Executors.newFixedThreadPool(Math.max(2, coreCount / 2));
        this.failedExports = new ConcurrentLinkedQueue<>();

        try {
            if (!exportDirectory.exists()) {
                exportDirectory.mkdirs();
            }
            BlockExporter.LOGGER.info("Created export directory: {}", exportDirectory.getAbsolutePath());
        } catch (Exception e) {
            BlockExporter.LOGGER.error("Failed to create export directory: {}", exportDirectory.getAbsolutePath(), e);
            throw new RuntimeException("Failed to create export directory", e);
        }
        
        this.framebuffer = new Framebuffer(this.textureSize, this.textureSize, true);
        this.framebuffer.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
    }

    public void exportItemsBatch(List<ItemStack> stacks, AtomicInteger completionCounter) {
        if (stacks == null || stacks.isEmpty()) {
            return;
        }

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                completionCounter.incrementAndGet();
                continue;
            }
            exportSingleItem(stack, completionCounter);
        }
    }

    private void exportSingleItem(ItemStack stack, AtomicInteger completionCounter) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            BlockExporter.LOGGER.warn("Could not get registry name for item: {}", stack.getItem());
            this.failedExports.add(stack);
            completionCounter.incrementAndGet();
            return;
        }
        
        try {
            this.framebuffer.bindFramebuffer(true);
            
            GlStateManager.viewport(0, 0, this.textureSize, this.textureSize);
            
            GlStateManager.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            
            GlStateManager.enableDepthTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0, this.textureSize, this.textureSize, 0.0, -1000.0, 1000.0);
            
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            GlStateManager.loadIdentity();
            
            try {
                this.renderItemDirect(stack);
            } catch (Exception e) {
                BlockExporter.LOGGER.warn("Failed to render item {}: {}", id, e.getMessage());
                this.failedExports.add(stack);
                completionCounter.incrementAndGet();
                return;
            }
            
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            
            ByteBuffer buffer = BufferUtils.createByteBuffer(this.textureSize * this.textureSize * 4);
            GL11.glReadPixels(0, 0, this.textureSize, this.textureSize, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            
            BufferedImage image = new BufferedImage(this.textureSize, this.textureSize, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < this.textureSize; y++) {
                for (int x = 0; x < this.textureSize; x++) {
                    int index = (y * this.textureSize + x) * 4;
                    int r = buffer.get(index) & 0xFF;
                    int g = buffer.get(index + 1) & 0xFF;
                    int b = buffer.get(index + 2) & 0xFF;
                    int a = buffer.get(index + 3) & 0xFF;
                    int argb = (a << 24) | (r << 16) | (g << 8) | b;
                    image.setRGB(x, this.textureSize - y - 1, argb);
                }
            }
            
            this.fileWriteExecutor.execute(() -> {
                try {
                    File filePath = new File(exportDirectory, id.getNamespace() + "_" + id.getPath() + ".png");
                    ImageIO.write(image, "PNG", filePath);
                    BlockExporter.LOGGER.debug("Exported: {}", filePath.getName());
                } catch (IOException e) {
                    BlockExporter.LOGGER.error("Failed to save exported item image: {}", id, e);
                    this.failedExports.add(stack);
                } finally {
                    completionCounter.incrementAndGet();
                }
            });
            
            this.framebuffer.unbindFramebuffer();
            
        } catch (Exception e) {
            BlockExporter.LOGGER.error("Failed to export item: {}", id, e);
            this.failedExports.add(stack);
            completionCounter.incrementAndGet();
            
            this.framebuffer.unbindFramebuffer();
        }
    }

    private void renderItemDirect(ItemStack stack) {
        net.minecraft.client.renderer.model.IBakedModel model = this.client.getItemRenderer().getModelWithOverrides(stack);
        
        GlStateManager.pushMatrix();
        
        this.client.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        this.client.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        
        boolean isGui3d = model.isGui3d();
        if (isGui3d) {
            GlStateManager.enableLighting();
            RenderHelper.enableGUIStandardItemLighting();
        } else {
            GlStateManager.disableLighting();
        }
        
        GlStateManager.translatef(this.textureSize / 2.0f, this.textureSize / 2.0f, 100.0f);
        
        GlStateManager.scalef(1.0f, -1.0f, 1.0f);
        
        float scale = this.textureSize;
        GlStateManager.scalef(scale, scale, scale);
        
        model = net.minecraftforge.client.ForgeHooksClient.handleCameraTransforms(
            model, net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType.GUI, false);
        
        this.client.getItemRenderer().renderItem(stack, model);
        
        if (isGui3d) {
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.disableAlphaTest();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        
        this.client.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE);
        this.client.getTextureManager().getTexture(net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
    }

    public List<ItemStack> getFailedExports() {
        return new ArrayList<>(this.failedExports);
    }

    @Override
    public void close() {
        this.fileWriteExecutor.shutdown();
        
        if (this.framebuffer != null) {
            this.framebuffer.deleteFramebuffer();
        }
    }
} 