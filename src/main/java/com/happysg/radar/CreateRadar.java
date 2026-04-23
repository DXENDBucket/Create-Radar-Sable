package com.happysg.radar;

import com.happysg.radar.block.datalink.DataLinkBlockItem;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.block.monitor.MonitorInputHandler;
import com.happysg.radar.compat.cbcwpf.CBCWPFCompatRegister;
import com.happysg.radar.compat.computercraft.CCCompatRegister;
import com.happysg.radar.ponder.RadarPonderPlugin;
import com.happysg.radar.registry.ModCommands;

import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CBCCompatRegister;
import com.happysg.radar.compat.cbcmw.CBCMWCompatRegister;

import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.networking.NetworkHandler;
import com.happysg.radar.registry.*;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.api.stress.BlockStressValues;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.stream.Collectors;

@Mod(CreateRadar.MODID)
public class CreateRadar {

    public static final String MODID = "create_radar";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID)
            .setTooltipModifierFactory(item ->
            new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    public CreateRadar(IEventBus modEventBus, ModContainer container) {
        getLogger().info("Initializing Create Radar!");

        NeoForge.EVENT_BUS.register(this);
        REGISTRATE.registerEventListeners(modEventBus);

        ModItems.register();
        ModBlocks.register();
        ModBlockEntityTypes.register();
        ModCreativeTabs.register(modEventBus);
        ModLang.register();
        ModPartials.init();
        RadarConfig.register(container);
        modEventBus.addListener(NetworkHandler::register);
        modEventBus.addListener(CreateRadar::init);
        modEventBus.addListener(CreateRadar::clientInit);
        modEventBus.addListener(CreateRadar::onLoadComplete);
        modEventBus.addListener(NetworkFiltererBlockEntity::registerCapabilities);

        container.registerExtensionPoint(IConfigScreenFactory.class, RadarConfig::createConfigScreen);

        NeoForge.EVENT_BUS.addListener(CreateRadar::clientTick);
        ModSounds.register(modEventBus);

        // Compat modules
        if (Mods.CREATEBIGCANNONS.isLoaded())
            CBCCompatRegister.registerCBC();
        if (Mods.CBCMODERNWARFARE.isLoaded())
            CBCMWCompatRegister.registerCBCMW();
        if (Mods.COMPUTERCRAFT.isLoaded())
            CCCompatRegister.registerPeripherals();
        if (Mods.SHUPAPIUM.isLoaded())
            CBCWPFCompatRegister.registerCBCWPF();
    }
    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {

    }

    private static void clientTick(ClientTickEvent.Post event) {
        DataLinkBlockItem.clientTick();
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }


    public static String toHumanReadable(String key) {
        String s = key.replace("_", " ");
        s = Arrays.stream(StringUtils.splitByCharacterTypeCamelCase(s))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining(" "));
        return StringUtils.normalizeSpace(s);
    }

    public static void clientInit(final FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new RadarPonderPlugin());
        NeoForge.EVENT_BUS.addListener(MonitorInputHandler::monitorPlayerHovering);
    }



    public static void onLoadComplete(FMLLoadCompleteEvent event) {

    }

    public static void init(final FMLCommonSetupEvent event) {

        event.enqueueWork(() -> {
            // Must be registered after registries open
            ModContraptionTypes.register();
            // Stress values
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_BEARING_BLOCK.get(), () -> 4d);
            BlockStressValues.IMPACTS.register(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK.get(), () -> 64);
            BlockStressValues.IMPACTS.register(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK.get(), () -> 64d);
          //  BlockStressValues.IMPACTS.register(ModBlocks.TRACK_CONTROLLER_BLOCK.get(), () -> 16d);

            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_RECEIVER_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_DISH_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.RADAR_PLATE_BLOCK.get(), () -> 0d);
            BlockStressValues.IMPACTS.register(ModBlocks.CREATIVE_RADAR_PLATE_BLOCK.get(), () -> 0d);
        });

        ModDisplayBehaviors.register();
        AllDataBehaviors.registerDefaults();
    }
    static {
        REGISTRATE.setTooltipModifierFactory((item) -> (new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)));
    }

}
