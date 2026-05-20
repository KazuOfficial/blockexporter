package com.github.kazuofficial.blockexporter;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public class BlockExporterClient {
    private static KeyMapping exportKeybind;

    @Mod.EventBusSubscriber(modid = BlockExporter.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            exportKeybind = new KeyMapping(
                "key.blockexporter.export",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "key.categories.blockexporter"
            );
            event.register(exportKeybind);
        }
    }

    @Mod.EventBusSubscriber(modid = BlockExporter.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (exportKeybind != null && exportKeybind.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new ExportScreen());
                }
            }
        }
    }
}
