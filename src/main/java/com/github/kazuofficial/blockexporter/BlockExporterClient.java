package com.github.kazuofficial.blockexporter;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = BlockExporter.MODID, dist = Dist.CLIENT)
public class BlockExporterClient {
    private static KeyMapping exportKeybind;

    public BlockExporterClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public static void registerKeyBinding() {
        exportKeybind = new KeyMapping(
            "key.blockexporter.export",
            GLFW.GLFW_KEY_I,
            "category.blockexporter"
        );
    }

    @EventBusSubscriber(modid = BlockExporter.MODID, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            if (exportKeybind != null) {
                event.register(exportKeybind);
            }
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (exportKeybind != null) {
            while (exportKeybind.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ExportScreen());
                }
            }
        }
    }
}