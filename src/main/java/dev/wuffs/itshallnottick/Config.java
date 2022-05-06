package dev.wuffs.itshallnottick;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    public static final String CATEGORY_GENERAL = "general";

    public static ForgeConfigSpec CONFIG;

    public static ForgeConfigSpec.ConfigValue<Integer> maxEntitySpawnDistanceHorizontal;
    public static ForgeConfigSpec.ConfigValue<Integer> maxEntitySpawnDistanceVertical;

    public static ForgeConfigSpec.ConfigValue<Integer> maxEntityTickDistanceHorizontal;
    public static ForgeConfigSpec.ConfigValue<Integer> maxEntityTickDistanceVertical;

    public static ForgeConfigSpec.ConfigValue<List<String>> entityIgnoreList;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading configEvent) {
        updateMobLists();
    }

    @SubscribeEvent
    public static void onFileChange(ModConfigEvent.Reloading configEvent) {
        updateMobLists();
    }

    public static final Set<ResourceLocation> entityResources = new HashSet<>();
    public static final Set<TagKey<EntityType<?>>> entityTagKeys = new HashSet<>();
    public static final Set<String> entityWildcards = new HashSet<>();

    private static void updateMobLists() {
        Utils.isIgnored.clear();

        for (String key : entityIgnoreList.get()) {
            if (key.contains("#")) {
                entityTagKeys.add(TagKey.create(Registry.ENTITY_TYPE_REGISTRY, new ResourceLocation(key.replace("#", ""))));
            } else if (key.contains("*")) {
                entityWildcards.add(key.split(":")[0]);
            } else {
                entityResources.add(new ResourceLocation(key));
            }
        }

        ItShallNotTick.LOGGER.debug(entityResources);
        ItShallNotTick.LOGGER.debug(entityTagKeys);
        ItShallNotTick.LOGGER.debug(entityWildcards);
    }

    static {
        ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        List<String> defaultIgnoreList = new ArrayList<>();
        defaultIgnoreList.add("minecraft:wither");
        defaultIgnoreList.add("minecraft:phantom");
        defaultIgnoreList.add("minecraft:ender_dragon");
        defaultIgnoreList.add("minecraft:elder_guardian");
        defaultIgnoreList.add("minecraft:player");

        BUILDER.comment("General settings").push(CATEGORY_GENERAL);
        maxEntitySpawnDistanceHorizontal = BUILDER.comment("Maximum distance from player (horizontally) for entity spawning check [Squared, Default 64^2]")
                .define("maxEntitySpawnDistanceHorizontal", 4096);

        maxEntitySpawnDistanceVertical = BUILDER.comment("Maximum distance from player (vertically) for entity spawning check [Raw, Default 32]")
                .define("maxEntitySpawnDistanceVertical", 32);

        maxEntityTickDistanceHorizontal = BUILDER.comment("Maximum distance from player (horizontally) to allow entity ticking [Squared, Default 48^2]")
                .define("maxEntityTickDistanceHorizontal", 2304);

        maxEntityTickDistanceVertical = BUILDER.comment("Maximum distance from player (vertically) to allow entity ticking [Raw, Default 32]")
                .define("maxEntityTickDistanceVertical", 32);

        entityIgnoreList = BUILDER.comment(
                        "List of entities to ignore when checking if they are allowed to tick",
                        "Tags can be used by using #minecraft:<tag_name> or #modid:<tag_name>",
                        "You can also use a wildcard after modid (modid:*)",
                        "Example list for a modpack",
                        "entityIgnoreList = [\"minecraft:wither\",\"minecraft:phantom\",\"minecraft:ender_dragon\", \"minecraft:elder_guardian\", \"minecraft:player\", \"botania:*\", \"create:*\", \"ftbic:*\", \"immersiveengineering:*\", \"ae2:*\", \"littlelogistics:*\", \"tiab:*\"]"
                )
                .define("entityIgnoreList", defaultIgnoreList);


        BUILDER.pop();

        CONFIG = BUILDER.build();
    }
}
