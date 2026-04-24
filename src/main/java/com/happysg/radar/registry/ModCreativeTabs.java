package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.cbc.CBCCompatRegister;
import com.happysg.radar.compat.cbcmw.CBCMWCompatRegister;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


import java.util.function.Supplier;

import static com.happysg.radar.CreateRadar.REGISTRATE;

public class ModCreativeTabs {
    public static DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateRadar.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RADAR_CREATIVE_TAB = addTab("radar", "Create: Radars",
            ModBlocks.MONITOR::asStack);


    public static DeferredHolder<CreativeModeTab, CreativeModeTab> addTab(String id, String name, Supplier<ItemStack> icon) {
        String itemGroupId = "itemGroup." + CreateRadar.MODID + "." + id;
        REGISTRATE.addRawLang(itemGroupId, name);

        CreativeModeTab.Builder tabBuilder = CreativeModeTab.builder()
                .icon(icon)
                .displayItems(ModCreativeTabs::displayItems)
                .title(Component.translatable(itemGroupId))
                .withTabsBefore(getCreateTabOrFallback());
        
        return CREATIVE_TABS.register(id, tabBuilder::build);
    }

    private static ResourceKey<CreativeModeTab> getCreateTabOrFallback() {
        try {
            Class<?> clazz = Class.forName("com.simibubi.create.AllCreativeModeTabs");
            var field = clazz.getField("PALETTES_CREATIVE_TAB");
            Object palettesTab = field.get(null);

            var getKeyMethod = palettesTab.getClass().getMethod("getKey");
            @SuppressWarnings("unchecked")
            ResourceKey<CreativeModeTab> key =
                    (ResourceKey<CreativeModeTab>) getKeyMethod.invoke(palettesTab);

            return key;
        } catch (Throwable t) {
            return CreativeModeTabs.REDSTONE_BLOCKS;
        }
    }

    private static void displayItems(CreativeModeTab.ItemDisplayParameters pParameters, CreativeModeTab.Output pOutput) {
        // Registrate already contributes these items to the global search tab.
        // Restrict our custom tab entries to the parent tab to avoid duplicate search entries.
        pOutput.accept(ModBlocks.MONITOR, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModItems.SAFE_ZONE_DESIGNATOR, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.RADAR_LINK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.RADAR_BEARING_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.RADAR_RECEIVER_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.RADAR_PLATE_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.RADAR_DISH_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.CREATIVE_RADAR_PLATE_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.AUTO_YAW_CONTROLLER_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.AUTO_PITCH_CONTROLLER_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.NETWORK_FILTERER_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.FIRE_CONTROLLER_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.RADOME, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModBlocks.SIREN, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModItems.IDENT_FILTER_ITEM, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModItems.RADAR_FILTER_ITEM, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModItems.TARGET_FILTER_ITEM, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        pOutput.accept(ModItems.BINOCULARS, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);



        if (Mods.CBCMODERNWARFARE.isLoaded()) {
            pOutput.accept(CBCMWCompatRegister.RADAR_GUIDANCE_BLOCK, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
    }


    public static void register(IEventBus eventBus) {
        CreateRadar.getLogger().info("Registering CreativeTabs!");
        CREATIVE_TABS.register(eventBus);
    }

}
