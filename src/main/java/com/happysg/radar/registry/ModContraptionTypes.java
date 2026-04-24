package com.happysg.radar.registry;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.bearing.RadarContraption;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings({"rawtypes", "unchecked"}) // needed for raw ContraptionType
public class ModContraptionTypes {

    public static ContraptionType RADAR_BEARING;

    public static void register() {
        if (RADAR_BEARING != null) {
            return;
        }

        RADAR_BEARING = new ContraptionType(RadarContraption::new);

        ResourceLocation id = CreateRadar.asResource("radar_bearing");
        Registry.register(CreateBuiltInRegistries.CONTRAPTION_TYPE, id, RADAR_BEARING);

        CreateRadar.getLogger().info("Registered contraption type '{}'", id);
    }
}
