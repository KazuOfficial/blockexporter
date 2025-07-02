package com.github.kazuofficial.blockexporter;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("blockexporter")
public class BlockExporter {
    public static final String MODID = "blockexporter";
    public static final Logger LOGGER = LogManager.getLogger();

    public BlockExporter() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("BlockExporter mod initialized");
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        LOGGER.info("BlockExporter client setup");
        BlockExporterClient.registerKeyBinding();
    }
}
