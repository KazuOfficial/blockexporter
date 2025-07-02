package com.github.kazuofficial.blockexporter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class BlockExporterClient implements ClientModInitializer {
	private static KeyBinding exportKeybind;

	@Override
	public void onInitializeClient() {
		exportKeybind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.blockexporter.export",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_I,
			"category.blockexporter"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (exportKeybind.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new ExportScreen());
				}
			}
		});
	}
}