package net.minecraft.src;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class ExporterItemRenderer {
    private final int textureSize;
    private final File exportDirectory;
    private final Minecraft mc;
    private RenderItem renderItem;

    public ExporterItemRenderer(int textureSize, Minecraft minecraft) {
        this.textureSize = textureSize;
        this.mc = minecraft;
        this.exportDirectory = new File(Minecraft.getMinecraftDir(), "item_exports");
        this.renderItem = new RenderItem();
        
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs();
        }
    }

    public boolean exportSingleItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        
        Item item = stack.getItem();
        String itemName = getItemName(item, stack);
        
        try {
            GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, this.textureSize, this.textureSize, 0.0, -1000.0, 1000.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glViewport(0, 0, this.textureSize, this.textureSize);

            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            
            GL11.glPushMatrix();
            GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            
            float scale = this.textureSize / 16.0f;
            GL11.glTranslatef((this.textureSize - 16 * scale) / 2.0f, (this.textureSize - 16 * scale) / 2.0f, 0.0f);
            GL11.glScalef(scale, scale, scale);
            
            renderItem.renderItemIntoGUI(this.mc.fontRenderer, this.mc.renderEngine, stack, 0, 0);

            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            ByteBuffer buffer = BufferUtils.createByteBuffer(this.textureSize * this.textureSize * 4);
            GL11.glReadPixels(0, 0, this.textureSize, this.textureSize, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            
            BufferedImage image = new BufferedImage(this.textureSize, this.textureSize, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < this.textureSize; y++) {
                for (int x = 0; x < this.textureSize; x++) {
                    int i = (x + (this.textureSize * y)) * 4;
                    int r = buffer.get(i) & 0xff;
                    int g = buffer.get(i + 1) & 0xff;
                    int b = buffer.get(i + 2) & 0xff;
                    int a = buffer.get(i + 3) & 0xff;
                    image.setRGB(x, this.textureSize - 1 - y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            
            File filePath = new File(exportDirectory, itemName + ".png");
            ImageIO.write(image, "PNG", filePath);
            
            return true;
            
        } catch (Exception e) {
            System.out.println("Failed to export item: " + itemName);
            e.printStackTrace();
            return false;
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        }
    }
    
    private String getItemName(Item item, ItemStack stack) {
        String name = "item_" + item.shiftedIndex;
        if (stack.itemDmg > 0) {
            name += "_" + stack.itemDmg;
        }
        name = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return name;
    }

    public void close() {
        this.renderItem = null;
    }
}
