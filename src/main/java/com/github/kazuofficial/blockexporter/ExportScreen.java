package com.github.kazuofficial.blockexporter;

import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fml.client.config.GuiSlider;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.client.renderer.RenderHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;

public class ExportScreen extends GuiScreen {
    private static final int PROGRESS_BAR_WIDTH = 280;
    private static final int PROGRESS_BAR_HEIGHT = 24;
    private static final int ITEM_SIZE = 48;
    private static final int PANEL_PADDING = 20;
    
    private static final int PROGRESS_BACKGROUND = 0xFF2D2D30;
    private static final int PROGRESS_FILL = 0xFF0E7B0E;
    private static final int PROGRESS_BORDER = 0xFF555555;
    private static final int ITEM_FRAME_COLOR = 0xFF8B8B8B;
    private static final int ACCENT_COLOR = 0xFF4A90E2;
    
    private static final int BATCH_SIZE = 16;
    
    private final List<Item> allItems;
    private List<Item> itemsToExport;
    private boolean finishedWithErrors = false;
    private int failedItemCount = 0;
    private int currentItemIndex = 0;
    private final AtomicInteger completedItems = new AtomicInteger(0);
    private boolean isExporting = false;
    private GuiButton startCancelButton;
    private GuiButton doneButton;
    private GuiButton openFolderButton;
    private ExporterItemRenderer itemRenderer;
    private int exportSize = 64;
    private File exportDirectory;
    private GuiSlider sizeSlider;
    private static final List<Integer> EXPORT_SIZES = Arrays.asList(16, 32, 64, 128, 256, 512, 1024);

    public ExportScreen() {
        this.allItems = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item != Items.AIR) {
                this.allItems.add(item);
            }
        }
        this.itemsToExport = new ArrayList<>(this.allItems);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.exportDirectory = new File(this.mc.gameDir, "item_exports");
        
        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 12;
        int totalWidth = buttonWidth * 3 + spacing * 2;
        int buttonsX = (this.width - totalWidth) / 2;
        
        this.startCancelButton = new GuiButton(0, buttonsX, this.height - 40, buttonWidth, buttonHeight,
            isExporting ? "Cancel Export" : "Start Export") {
            @Override
            public void onClick(double mouseX, double mouseY) {
                if (isExporting) {
                    isExporting = false;
                    updateButtonStates();
                    BlockExporter.LOGGER.info("Export cancelled by user.");
                } else {
                    isExporting = true;
                    finishedWithErrors = false;
                    failedItemCount = 0;
                    itemsToExport = new ArrayList<>(allItems);

                    if (itemRenderer != null) {
                        itemRenderer.close();
                    }
                    itemRenderer = new ExporterItemRenderer(exportSize);
                    currentItemIndex = 0;
                    completedItems.set(0);
                    BlockExporter.LOGGER.info("Starting export of {} items with batch size {}",
                        itemsToExport.size(), BATCH_SIZE);
                    updateButtonStates();
                }
            }
        };
        this.addButton(this.startCancelButton);

        this.openFolderButton = new GuiButton(1, buttonsX + buttonWidth + spacing, this.height - 40, buttonWidth, buttonHeight,
            "Open Folder") {
            @Override
            public void onClick(double mouseX, double mouseY) {
                try {
                    if (!exportDirectory.exists()) {
                        exportDirectory.mkdirs();
                    }
                    net.minecraft.util.Util.getOSType().openURI(exportDirectory.toURI());
                } catch (Exception e) {
                    BlockExporter.LOGGER.warn("Couldn't open export folder: {}. Export folder location: {}", 
                        e.getMessage(), exportDirectory.getAbsolutePath());
                }
            }
        };
        this.addButton(this.openFolderButton);

        this.doneButton = new GuiButton(2, buttonsX + (buttonWidth + spacing) * 2, this.height - 40, buttonWidth, buttonHeight,
            I18n.format("gui.done")) {
            @Override
            public void onClick(double mouseX, double mouseY) {
                ExportScreen.this.mc.displayGuiScreen(null);
            }
        };
        this.addButton(this.doneButton);

        int sliderWidth = 240;
        int sliderX = (this.width - sliderWidth) / 2;
        int sliderY = this.height - 75;
        int initialSizeIndex = EXPORT_SIZES.indexOf(exportSize);
        if (initialSizeIndex == -1) {
            initialSizeIndex = 2;
        }
        this.sizeSlider = new GuiSlider(3, sliderX, sliderY, sliderWidth, 20, "", "", 0, EXPORT_SIZES.size() - 1, initialSizeIndex, false, true, new GuiSlider.ISlider() {
            @Override
            public void onChangeSliderValue(GuiSlider slider) {
                int sizeIndex = slider.getValueInt();
                exportSize = EXPORT_SIZES.get(sizeIndex);
                slider.displayString = "Export Size: " + exportSize + "×" + exportSize + " pixels";
            }
        });
        this.sizeSlider.displayString = "Export Size: " + exportSize + "×" + exportSize + " pixels";
        this.addButton(this.sizeSlider);
        
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (isExporting) {
            startCancelButton.displayString = "Cancel Export";
            doneButton.enabled = false;
            if (sizeSlider != null) sizeSlider.enabled = false;
        } else {
            startCancelButton.displayString = "Start Export";
            doneButton.enabled = true;
            if (sizeSlider != null) sizeSlider.enabled = true;
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        int panelHeight = 240;
        int panelY = (this.height - panelHeight) / 2 - 20;
        
        this.drawCenteredString(this.fontRenderer, "BlockExporter", this.width / 2, panelY + PANEL_PADDING, ACCENT_COLOR);
        
        int itemSectionY = panelY + PANEL_PADDING + 25;
        int displayIndex = Math.min(currentItemIndex, itemsToExport.size() - 1);
        if (displayIndex >= 0 && displayIndex < itemsToExport.size()) {
            ItemStack stack = new ItemStack(itemsToExport.get(displayIndex));
            
            int itemFrameSize = ITEM_SIZE + 8;
            int itemFrameX = (this.width - itemFrameSize) / 2;
            int itemFrameY = itemSectionY;
            
            drawRect(itemFrameX - 1, itemFrameY - 1, itemFrameX + itemFrameSize + 1, itemFrameY + itemFrameSize + 1, ITEM_FRAME_COLOR);
            drawRect(itemFrameX, itemFrameY, itemFrameX + itemFrameSize, itemFrameY + itemFrameSize, 0xFF1E1E1E);
            
            int itemX = itemFrameX + 4;
            int itemY = itemFrameY + 4;
            
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translatef(itemX + ITEM_SIZE / 2f, itemY + ITEM_SIZE / 2f, 0);
            net.minecraft.client.renderer.GlStateManager.scalef(3.0f, 3.0f, 3.0f);
            net.minecraft.client.renderer.GlStateManager.translatef(-8, -8, 0);
            
            RenderHelper.enableGUIStandardItemLighting();
            net.minecraft.client.renderer.GlStateManager.enableRescaleNormal();
            net.minecraft.client.renderer.GlStateManager.enableLighting();
            net.minecraft.client.renderer.GlStateManager.enableDepthTest();
            
            this.itemRender.renderItemAndEffectIntoGUI(stack, 0, 0);
            
            net.minecraft.client.renderer.GlStateManager.disableLighting();
            net.minecraft.client.renderer.GlStateManager.disableDepthTest();
            net.minecraft.client.renderer.GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
            
            net.minecraft.client.renderer.GlStateManager.popMatrix();
            
            String itemName = stack.getDisplayName().getFormattedText();
            if (itemName.length() > 25) {
                itemName = itemName.substring(0, 22) + "...";
            }
            this.drawCenteredString(this.fontRenderer, itemName, 
                this.width / 2, itemSectionY + itemFrameSize + 8, 0xFFFFFF);
        }

        int progressSectionY = itemSectionY + 85;
        
        int progressX = (this.width - PROGRESS_BAR_WIDTH) / 2;
        int progressY = progressSectionY;
        
        drawRect(progressX - 1, progressY - 1, progressX + PROGRESS_BAR_WIDTH + 1, progressY + PROGRESS_BAR_HEIGHT + 1, PROGRESS_BORDER);
        drawRect(progressX, progressY, progressX + PROGRESS_BAR_WIDTH, progressY + PROGRESS_BAR_HEIGHT, PROGRESS_BACKGROUND);

        boolean isComplete = currentItemIndex >= itemsToExport.size() && !isExporting;

        int completed = isComplete ? itemsToExport.size() - failedItemCount : completedItems.get();
        float progress = itemsToExport.isEmpty() ? 0 : (float) completed / itemsToExport.size();
        int progressWidth = (int) (PROGRESS_BAR_WIDTH * progress);
        if (progressWidth > 0) {
            drawRect(progressX, progressY, progressX + progressWidth, progressY + PROGRESS_BAR_HEIGHT, PROGRESS_FILL);
            drawRect(progressX, progressY, progressX + progressWidth, progressY + 2, 0xFF4CAF50);
        }

        String progressText = String.format("%d / %d items (%.1f%%)",
            completed, itemsToExport.size(), progress * 100);
        this.drawCenteredString(this.fontRenderer, progressText, 
            this.width / 2, progressY + PROGRESS_BAR_HEIGHT + 8, 0xFFFFFF);
        
        String statusText = isExporting ? "Exporting..." : (isComplete ? (finishedWithErrors ? "Finished with errors" : "Export Complete!") : "Ready to export");
        int statusColor = isExporting ? 0xFFFFAA00 : (isComplete ? (finishedWithErrors ? 0xFFFF5555 : 0xFF00FF00) : 0xFFAAAAAA);
        this.drawCenteredString(this.fontRenderer, statusText, 
            this.width / 2, progressY + PROGRESS_BAR_HEIGHT + 25, statusColor);

        super.render(mouseX, mouseY, partialTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (isExporting) {
            if (currentItemIndex < itemsToExport.size()) {
                List<ItemStack> batch = new ArrayList<>();
                int batchEnd = Math.min(currentItemIndex + BATCH_SIZE, itemsToExport.size());

                for (int i = currentItemIndex; i < batchEnd; i++) {
                    batch.add(new ItemStack(itemsToExport.get(i)));
                }

                if (itemRenderer != null && !batch.isEmpty()) {
                    itemRenderer.exportItemsBatch(batch, completedItems);
                }

                currentItemIndex = batchEnd;

            } else if (completedItems.get() >= itemsToExport.size()) {
                isExporting = false;
                if (itemRenderer != null) {
                    List<ItemStack> currentFailedItems = itemRenderer.getFailedExports();
                    if (!currentFailedItems.isEmpty()) {
                        this.finishedWithErrors = true;
                        this.failedItemCount = currentFailedItems.size();
                    }
                }
                updateButtonStates();

                if (!this.finishedWithErrors) {
                    BlockExporter.LOGGER.info("Export completed! Exported {} items", itemsToExport.size());
                } else {
                    BlockExporter.LOGGER.warn("Export finished with {} failures.", this.failedItemCount);
                }
            }
        }
    }

    @Override
    public void onGuiClosed() {
        if (this.itemRenderer != null) {
            this.itemRenderer.close();
        }
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
} 