package dev.wuffs.itshallnottick;

import dev.ftb.mods.ftbchunks.data.ClaimedChunk;
import dev.ftb.mods.ftbchunks.data.FTBChunksAPI;
import dev.wuffs.itshallnottick.integration.FTBChunks;
import dev.wuffs.itshallnottick.network.ChunkDimPos;
import dev.wuffs.itshallnottick.network.PacketHandler;
import dev.wuffs.itshallnottick.network.SendClaimedChunksPacket;
import dev.wuffs.itshallnottick.network.SendMinPlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ItShallNotTick.MODID)
public class ItShallNotTick {

    public static final String MODID = "itshallnottick";

    public static final Logger LOGGER = LogManager.getLogger("ISNT");

    public static boolean isFTBChunksLoaded = false;
    public static int minPlayer;

    public ItShallNotTick() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.CONFIG);

        isFTBChunksLoaded = ModList.get().isLoaded("ftbchunks");
        if (isFTBChunksLoaded) {
            FTBChunks.setup();
        }
        PacketHandler.regsiter();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void commonSetup(FMLCommonSetupEvent event){
        minPlayer = Config.minPlayers.get();
    }

    @SubscribeEvent
    @OnlyIn(Dist.DEDICATED_SERVER)
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        PacketHandler.sendToClient(new SendMinPlayerPacket(minPlayer), (ServerPlayer) event.getEntity());
    }
}
