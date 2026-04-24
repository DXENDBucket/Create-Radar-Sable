package com.happysg.radar.block.mount;

import com.happysg.radar.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlock;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;

public class SmartMountBlock extends CannonMountBlock {

    public SmartMountBlock(Properties properties) {
        super(properties);

    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(VERTICAL_DIRECTION);
    }

    @Override
    public BlockEntityType<? extends CannonMountBlockEntity> getBlockEntityType() {
        return ModBlockEntityTypes.SMART_MOUNT_BE.get();
    }

}
