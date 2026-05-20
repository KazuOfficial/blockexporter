package com.github.kazuofficial.blockexporter;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

public class BlockExporterClient implements ClientModInitializer {
	private static KeyMapping exportKeybind;

	@Override
	public void onInitializeClient() {
		exportKeybind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.blockexporter.export",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_I,
			KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (exportKeybind.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new ExportScreen());
				}
			}
		});
	}
}
