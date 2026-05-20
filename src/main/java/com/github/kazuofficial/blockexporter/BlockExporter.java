package com.github.kazuofficial.blockexporter;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(BlockExporter.MOD_ID)
public class BlockExporter {
    public static final String MOD_ID = "blockexporter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockExporter() {
        LOGGER.info("Block Exporter mod initialized!");
    }
}
