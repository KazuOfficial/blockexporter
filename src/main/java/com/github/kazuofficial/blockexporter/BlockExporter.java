package com.github.kazuofficial.blockexporter;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockExporter implements ModInitializer {
	public static final String MOD_ID = "blockexporter";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Block Exporter mod initialized!");
	}
}