package com.github.kazuofficial.blockexporter;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ExporterItemRenderer implements AutoCloseable {
    private final int textureSize;
    private final Path exportDirectory;
    private final Minecraft client;
    private final TextureTarget framebuffer;
    private final ExecutorService fileWriteExecutor;
    private final Semaphore fileWriteSemaphore;
    private final ConcurrentLinkedQueue<ItemStack> failedExports;
    private final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer;
    private final ItemModelResolver itemModelResolver;
    private final MultiBufferSource.BufferSource bufferSource;

    public ExporterItemRenderer(int textureSize) {
        this.textureSize = textureSize;
        this.client = Minecraft.getInstance();
        this.exportDirectory = client.gameDirectory.toPath().resolve("item_exports");
        int coreCount = Runtime.getRuntime().availableProcessors();
        this.fileWriteExecutor = Executors.newFixedThreadPool(Math.max(2, coreCount / 2));
        this.fileWriteSemaphore = new Semaphore(coreCount);
        this.failedExports = new ConcurrentLinkedQueue<>();
        this.projectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("item_exporter_proj", -1000.0F, 1000.0F, true);
        this.itemModelResolver = this.client.getItemModelResolver();
        this.bufferSource = this.client.renderBuffers().bufferSource();

        try {
            Files.createDirectories(exportDirectory);
            BlockExporter.LOGGER.info("Created export directory: {}", exportDirectory.toAbsolutePath());
        } catch (IOException e) {
            BlockExporter.LOGGER.error("Failed to create export directory: {}", exportDirectory.toAbsolutePath(), e);
            throw new RuntimeException("Failed to create export directory", e);
        }
        
        this.framebuffer = new TextureTarget("item_exporter", this.textureSize, this.textureSize, true);
    }

    public void exportItemsBatch(List<ItemStack> stacks, AtomicInteger completionCounter) {
        if (stacks == null || stacks.isEmpty()) {
            return;
        }

        try {
            RenderSystem.outputColorTextureOverride = this.framebuffer.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = this.framebuffer.getDepthTextureView();
            
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(this.projectionMatrixBuffer.getBuffer(this.textureSize, this.textureSize), ProjectionType.ORTHOGRAPHIC);

            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) {
                    completionCounter.incrementAndGet();
                    continue;
                }
                exportSingleItemFast(stack, completionCounter);
            }

        } catch (Exception e) {
            BlockExporter.LOGGER.error("Failed to export item batch", e);
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private void exportSingleItemFast(ItemStack stack, AtomicInteger completionCounter) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        
        try {
            RenderSystem.getDevice()
                .createCommandEncoder()
                .clearColorAndDepthTextures(this.framebuffer.getColorTexture(), 0, this.framebuffer.getDepthTexture(), 1.0);
            
            ItemStackRenderState renderState = new ItemStackRenderState();
            this.itemModelResolver.updateForTopItem(renderState, stack, ItemDisplayContext.GUI, this.client.level, null, 0);
            
            PoseStack poseStack = new PoseStack();
            poseStack.pushPose();
            poseStack.translate(this.textureSize / 2.0F, this.textureSize / 2.0F, 0.0F);
            poseStack.scale(this.textureSize, -this.textureSize, this.textureSize);
            
            boolean usesBlockLight = renderState.usesBlockLight();
            if (!usesBlockLight) {
                this.client.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
            } else {
                this.client.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            }

            renderState.render(poseStack, this.bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
            this.bufferSource.endBatch();
            
            poseStack.popPose();

            takeScreenshotWithTransparency(this.framebuffer, image -> {
                this.fileWriteExecutor.execute(() -> {
                    try {
                        this.fileWriteSemaphore.acquire();
                        Path filePath = exportDirectory.resolve(id.getNamespace() + "_" + id.getPath() + ".png");
                        image.writeToFile(filePath);
                        BlockExporter.LOGGER.debug("Async exported: {}", filePath.getFileName());
                    } catch (IOException e) {
                        BlockExporter.LOGGER.error("Failed to save exported item image: {}", id, e);
                        this.failedExports.add(stack);
                    } catch (InterruptedException e) {
                        BlockExporter.LOGGER.error("Screenshot thread interrupted for item: {}", id, e);
                        this.failedExports.add(stack);
                        Thread.currentThread().interrupt();
                    } finally {
                        image.close();
                        this.fileWriteSemaphore.release();
                        completionCounter.incrementAndGet();
                    }
                });
            });

        } catch (Exception e) {
            BlockExporter.LOGGER.error("Failed to export item: {}", id, e);
            this.failedExports.add(stack);
            completionCounter.incrementAndGet();
        }
    }

    private static void takeScreenshotWithTransparency(TextureTarget renderTarget, Consumer<NativeImage> writer) {
        int i = renderTarget.width;
        int j = renderTarget.height;
        GpuTexture gputexture = renderTarget.getColorTexture();
        if (gputexture == null) {
            throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
        } else {
            GpuBuffer gpubuffer = RenderSystem.getDevice().createBuffer(() -> "Screenshot buffer", 9, i * j * gputexture.getFormat().pixelSize());
            CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();
            RenderSystem.getDevice()
                .createCommandEncoder()
                .copyTextureToBuffer(
                    gputexture,
                    gpubuffer,
                    0,
                    () -> {
                        try (GpuBuffer.MappedView gpubuffer$mappedview = commandencoder.mapBuffer(gpubuffer, true, false)) {
                            NativeImage nativeimage = new NativeImage(i, j, false);

                            for (int i1 = 0; i1 < j; i1++) {
                                for (int j1 = 0; j1 < i; j1++) {
                                    int pixelData = gpubuffer$mappedview.data().getInt((j1 + i1 * i) * gputexture.getFormat().pixelSize());
                                    nativeimage.setPixelABGR(j1, j - i1 - 1, pixelData);
                                }
                            }

                            writer.accept(nativeimage);
                        }

                        gpubuffer.close();
                    },
                    0
                );
        }
    }

    public List<ItemStack> getFailedExports() {
        return List.copyOf(this.failedExports);
    }

    @Override
    public void close() {
        this.fileWriteExecutor.shutdown();
        this.framebuffer.destroyBuffers();
        this.projectionMatrixBuffer.close();
    }
}