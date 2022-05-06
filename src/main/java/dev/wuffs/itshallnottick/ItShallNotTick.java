package dev.wuffs.itshallnottick;

import dev.wuffs.itshallnottick.integration.FTBChunks;
import dev.wuffs.itshallnottick.network.PacketHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ItShallNotTick.MODID)
public class ItShallNotTick {

    public static final String MODID = "itshallnottick";

    public static final Logger LOGGER = LogManager.getLogger("ISNT");

    public static boolean isFTBChunksLoaded = false;

    public ItShallNotTick() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.CONFIG);

        isFTBChunksLoaded = ModList.get().isLoaded("ftbchunks");
        if (isFTBChunksLoaded) {
            FTBChunks.setup();
        }

        PacketHandler.regsiter();
    }
}
