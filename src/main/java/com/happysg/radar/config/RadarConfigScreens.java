package com.happysg.radar.config;

import com.happysg.radar.CreateRadar;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@OnlyIn(Dist.CLIENT)
public final class RadarConfigScreens {

    private RadarConfigScreens() {
    }

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, RadarConfigScreens::createConfigScreen);
    }

    private static BaseConfigScreen createConfigScreen(ModContainer container, Screen parent) {
        BaseConfigScreen.setDefaultActionFor(CreateRadar.MODID, base -> base
                .withSpecs(RadarConfig.client().specification,
                        null,
                        RadarConfig.server().specification));

        return new BaseConfigScreen(parent, CreateRadar.MODID);
    }
}
