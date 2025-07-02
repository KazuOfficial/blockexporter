package com.github.kazuofficial.blockexporter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BlockExporter.MODID, value = Dist.CLIENT)
public class BlockExporterClient {
    private static KeyBinding exportKeybind;

    public static void registerKeyBinding() {
        exportKeybind = new KeyBinding(
            "key.blockexporter.export",
            KeyConflictContext.IN_GAME,
            InputMappings.Type.KEYSYM.getOrMakeInput(GLFW.GLFW_KEY_I),
            "category.blockexporter"
        );
        
        ClientRegistry.registerKeyBinding(exportKeybind);
        MinecraftForge.EVENT_BUS.register(BlockExporterClient.class);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (exportKeybind != null && exportKeybind.isPressed()) {
            if (client.currentScreen == null) {
                client.displayGuiScreen(new ExportScreen());
            }
        }
    }
} 