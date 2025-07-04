package net.minecraft.src;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class GuiExportScreen extends GuiScreen {
    private static final int PROGRESS_BAR_WIDTH = 280;
    private static final int PROGRESS_BAR_HEIGHT = 24;
    private static final int ITEM_SIZE = 48;
    private static final int PANEL_PADDING = 20;
    
    private final List allItems;
    private List itemsToExport;
    private boolean finishedWithErrors = false;
    private int failedItemCount = 0;
    private int currentItemIndex = 0;
    private int completedItems = 0;
    private boolean isExporting = false;
    private GuiButton startCancelButton;
    private GuiButton doneButton;
    private GuiButton openFolderButton;
    private ExporterItemRenderer itemRenderer;
    private RenderItem guiItemRenderer;
    private int exportSize = 64;
    private File exportDirectory;
    private int selectedSizeIndex = 2;
    private static final int[] EXPORT_SIZES = {16, 32, 64, 128};
    private static final String[] SIZE_LABELS = {"16x16", "32x32", "64x64", "128x128"};

    public GuiExportScreen() {
        this.allItems = new ArrayList();
        
        for (int i = 0; i < Item.itemsList.length; i++) {
            Item item = Item.itemsList[i];
            if (item != null) {
                this.allItems.add(new ItemStack(item));
            }
        }
        
        this.itemsToExport = new ArrayList(this.allItems);
        this.exportDirectory = new File(Minecraft.getMinecraftDir(), "item_exports");
        this.guiItemRenderer = new RenderItem();
    }

    public void initGui() {
        super.initGui();
        
        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 12;
        int totalWidth = buttonWidth * 3 + spacing * 2;
        int buttonsX = (this.width - totalWidth) / 2;
        
        this.startCancelButton = new GuiButton(0, buttonsX, this.height - 40, buttonWidth, buttonHeight,
            isExporting ? "Cancel Export" : "Start Export");
        this.controlList.add(this.startCancelButton);

        this.openFolderButton = new GuiButton(1, buttonsX + buttonWidth + spacing, this.height - 40, 
            buttonWidth, buttonHeight, "Open Folder");
        this.controlList.add(this.openFolderButton);

        this.doneButton = new GuiButton(2, buttonsX + (buttonWidth + spacing) * 2, this.height - 40, 
            buttonWidth, buttonHeight, "Done");
        this.controlList.add(this.doneButton);

        int sizeButtonY = this.height - 75;
        int sizeButtonWidth = 60;
        int sizeButtonSpacing = 65;
        int totalSizeWidth = EXPORT_SIZES.length * sizeButtonWidth + (EXPORT_SIZES.length - 1) * 5;
        int sizeButtonsX = (this.width - totalSizeWidth) / 2;
        
        for (int i = 0; i < EXPORT_SIZES.length; i++) {
            GuiButton sizeButton = new GuiButton(10 + i, sizeButtonsX + i * sizeButtonSpacing, 
                sizeButtonY, sizeButtonWidth, 20, SIZE_LABELS[i]);
            this.controlList.add(sizeButton);
        }
        
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (isExporting) {
            startCancelButton.displayString = "Cancel Export";
            doneButton.enabled = false;
            for (int i = 0; i < EXPORT_SIZES.length; i++) {
                ((GuiButton)this.controlList.get(3 + i)).enabled = false;
            }
        } else {
            startCancelButton.displayString = "Start Export";
            doneButton.enabled = true;
            for (int i = 0; i < EXPORT_SIZES.length; i++) {
                ((GuiButton)this.controlList.get(3 + i)).enabled = true;
            }
        }
        
        for (int i = 0; i < EXPORT_SIZES.length; i++) {
            GuiButton sizeButton = (GuiButton)this.controlList.get(3 + i);
            if (i == selectedSizeIndex) {
                sizeButton.displayString = "> " + SIZE_LABELS[i] + " <";
            } else {
                sizeButton.displayString = SIZE_LABELS[i];
            }
        }
    }

    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (isExporting) {
                isExporting = false;
                updateButtonStates();
                System.out.println("Export cancelled by user.");
            } else {
                startExport();
            }
        } else if (button.id == 1) {
            try {
                if (!exportDirectory.exists()) {
                    exportDirectory.mkdirs();
                }
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(exportDirectory);
                } else {
                    System.out.println("Desktop is not supported. Export folder location: " + exportDirectory.getAbsolutePath());
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to open directory. Export folder location: " + exportDirectory.getAbsolutePath());
            }
        } else if (button.id == 2) {
            this.mc.displayGuiScreen(null);
        } else if (button.id >= 10 && button.id < 10 + EXPORT_SIZES.length) {
            selectedSizeIndex = button.id - 10;
            exportSize = EXPORT_SIZES[selectedSizeIndex];
            updateButtonStates();
        }
    }
    
    private void startExport() {
        isExporting = true;
        finishedWithErrors = false;
        failedItemCount = 0;
        itemsToExport = new ArrayList(allItems);

        if (itemRenderer != null) {
            itemRenderer.close();
        }
        itemRenderer = new ExporterItemRenderer(exportSize, this.mc);
        currentItemIndex = 0;
        completedItems = 0;
        System.out.println("Starting export of " + itemsToExport.size() + " items at " + exportSize + "x" + exportSize);
        updateButtonStates();
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        int panelHeight = 240;
        int panelY = (this.height - panelHeight) / 2 - 20;
        
        this.drawCenteredString(this.fontRenderer, "BlockExporter", this.width / 2, panelY + PANEL_PADDING, 0x4A90E2);
        
        int itemSectionY = panelY + PANEL_PADDING + 25;
        int displayIndex = Math.min(currentItemIndex, itemsToExport.size() - 1);
        if (displayIndex >= 0 && displayIndex < itemsToExport.size()) {
            ItemStack stack = (ItemStack)itemsToExport.get(displayIndex);
            
            int itemFrameSize = ITEM_SIZE + 8;
            int itemFrameX = (this.width - itemFrameSize) / 2;
            int itemFrameY = itemSectionY;
            
            this.drawRect(itemFrameX - 1, itemFrameY - 1, itemFrameX + itemFrameSize + 1, itemFrameY + itemFrameSize + 1, 0xFF8B8B8B);
            this.drawRect(itemFrameX, itemFrameY, itemFrameX + itemFrameSize, itemFrameY + itemFrameSize, 0xFF1E1E1E);
            
            int itemX = itemFrameX + 4;
            int itemY = itemFrameY + 4;
            
            GL11.glPushMatrix();
            GL11.glTranslatef(itemX + ITEM_SIZE / 2f, itemY + ITEM_SIZE / 2f, 0);
            GL11.glScalef(3.0f, 3.0f, 3.0f);
            GL11.glTranslatef(-8, -8, 0);
            
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            
            GL11.glPushMatrix();
            GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            
            guiItemRenderer.renderItemIntoGUI(this.fontRenderer, this.mc.renderEngine, stack, 0, 0);
            
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            
            GL11.glPopMatrix();
            
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            
            String itemName = "item_" + stack.getItem().shiftedIndex;
            if (itemName != null && itemName.length() > 25) {
                itemName = itemName.substring(0, 22) + "...";
            }
            if (itemName == null) itemName = "Unknown Item";
            this.drawCenteredString(this.fontRenderer, itemName, 
                this.width / 2, itemSectionY + itemFrameSize + 8, 0xFFFFFF);
        }

        int progressSectionY = itemSectionY + 85;
        int progressX = (this.width - PROGRESS_BAR_WIDTH) / 2;
        int progressY = progressSectionY;
        
        this.drawRect(progressX - 1, progressY - 1, progressX + PROGRESS_BAR_WIDTH + 1, progressY + PROGRESS_BAR_HEIGHT + 1, 0xFF555555);
        this.drawRect(progressX, progressY, progressX + PROGRESS_BAR_WIDTH, progressY + PROGRESS_BAR_HEIGHT, 0xFF2D2D30);

        boolean isComplete = currentItemIndex >= itemsToExport.size() && !isExporting;
        int completed = isComplete ? itemsToExport.size() - failedItemCount : completedItems;
        float progress = itemsToExport.isEmpty() ? 0 : (float) completed / itemsToExport.size();
        int progressWidth = (int) (PROGRESS_BAR_WIDTH * progress);
        
        if (progressWidth > 0) {
            this.drawRect(progressX, progressY, progressX + progressWidth, progressY + PROGRESS_BAR_HEIGHT, 0xFF0E7B0E);
            this.drawRect(progressX, progressY, progressX + progressWidth, progressY + 2, 0xFF4CAF50);
        }

        String progressText = String.format("%d / %d items (%.1f%%)",
            completed, itemsToExport.size(), progress * 100);
        this.drawCenteredString(this.fontRenderer, progressText, 
            this.width / 2, progressY + PROGRESS_BAR_HEIGHT + 8, 0xFFFFFF);
        
        String statusText = isExporting ? "Exporting..." : (isComplete ? (finishedWithErrors ? "Finished with errors" : "Export Complete!") : "Ready to export");
        int statusColor = isExporting ? 0xFFFFAA00 : (isComplete ? (finishedWithErrors ? 0xFFFF5555 : 0xFF00FF00) : 0xFFAAAAAA);
        this.drawCenteredString(this.fontRenderer, statusText, 
            this.width / 2, progressY + PROGRESS_BAR_HEIGHT + 25, statusColor);

        this.drawCenteredString(this.fontRenderer, "Export Size:", 
            this.width / 2, this.height - 95, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public void updateScreen() {
        super.updateScreen();
        
        if (isExporting && currentItemIndex < itemsToExport.size()) {
            ItemStack stack = (ItemStack)itemsToExport.get(currentItemIndex);
            
            if (itemRenderer != null) {
                boolean success = itemRenderer.exportSingleItem(stack);
                if (success) {
                    completedItems++;
                } else {
                    failedItemCount++;
                    finishedWithErrors = true;
                }
            }
            
            currentItemIndex++;
            
            if (currentItemIndex >= itemsToExport.size()) {
                isExporting = false;
                updateButtonStates();
                
                if (!finishedWithErrors) {
                    System.out.println("Export completed! Exported " + completedItems + " items");
                } else {
                    System.out.println("Export finished with " + failedItemCount + " failures.");
                }
            }
        }
    }

    public void onGuiClosed() {
        if (this.itemRenderer != null) {
            this.itemRenderer.close();
        }
        super.onGuiClosed();
    }

    public boolean doesGuiPauseGame() {
        return true;
    }
}
