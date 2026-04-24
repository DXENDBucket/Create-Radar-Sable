package com.happysg.radar.block.radar.skyradar;

import com.happysg.radar.registry.ModBlockEntityTypes;
import net.minecraft.world.level.block.entity.BlockEntityType;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

public class SkyRadarBlock extends CannonMountBlock {
    public SkyRadarBlock(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public BlockEntityType<? extends CannonMountBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.SKY_RADAR_BE.get();
    }
}
