package net.minecraft.src;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class ModBlockExporter {
    public static final String MODID = "blockexporter";
    public static final String VERSION = "0.1.0";
    
    private static boolean isKeyPressed = false;
    private static boolean wasKeyPressed = false;
    
    public static void load() {
        System.out.println("BlockExporter mod loaded for Alpha 1.1.2_01");
    }
    
    public static void onTick(Minecraft mc) {
        wasKeyPressed = isKeyPressed;
        isKeyPressed = Keyboard.isKeyDown(Keyboard.KEY_I);
        
        if (isKeyPressed && !wasKeyPressed) {
            if (mc.currentScreen == null) {
                mc.displayGuiScreen(new GuiExportScreen());
            }
        }
    }
}
