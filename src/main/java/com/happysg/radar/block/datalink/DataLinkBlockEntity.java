package com.happysg.radar.block.datalink;

import com.happysg.radar.registry.AllDataBehaviors;
import com.happysg.radar.utils.NbtCompat;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

public class DataLinkBlockEntity extends SmartBlockEntity {

    protected BlockPos targetOffset = BlockPos.ZERO;

    public DataPeripheral activeSource;
    public DataController activeTarget;

    private CompoundTag sourceConfig;
    boolean ledState = false;

    private BlockPos linkedMonitorPos;

    public DataLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }

    @Override
    public void tick() {
        super.tick();
        updateGatheredData();
    }

    public void updateGatheredData() {
        BlockPos sourcePosition = getSourcePosition();
        BlockPos targetPosition = getTargetPosition();

        if (!level.isLoaded(targetPosition) || !level.isLoaded(sourcePosition))
            return;

        DataController target = AllDataBehaviors.targetOf(level, targetPosition);
        DataPeripheral source = AllDataBehaviors.sourcesOf(level, sourcePosition);
        boolean notify = false;

        if (activeTarget != target) {
            activeTarget = target;
            notify = true;
        }

        if (activeSource != source) {
            activeSource = source;
            sourceConfig = new CompoundTag();
            notify = true;
        }

        if (notify)
            notifyUpdate();
        if (activeSource == null || activeTarget == null) {
            ledState = false;
            return;
        }

        ledState = true;
        activeSource.transferData(new DataLinkContext(level, this), activeTarget);
        sendData();
        //TODO implement advancement
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeSafe(tag, provider);
        writeGatheredData(tag);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        writeGatheredData(tag);
        if (clientPacket && activeTarget != null)
            tag.putString("TargetType", activeTarget.id.toString());
        tag.putBoolean("LedState", ledState);
    }

    private void writeGatheredData(CompoundTag tag) {
        tag.put("TargetOffset", NbtUtils.writeBlockPos(targetOffset));

        if (activeSource != null) {
            CompoundTag data = sourceConfig.copy();
            data.putString("Id", activeSource.id.toString());
            tag.put("Source", data);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);

        targetOffset = NbtCompat.readBlockPosOrDefault(tag, "TargetOffset", BlockPos.ZERO);
        ledState = tag.getBoolean("LedState");

        if (clientPacket && tag.contains("TargetType"))
            activeTarget = AllDataBehaviors.getTarget(ResourceLocation.parse(tag.getString("TargetType")));

        if (!tag.contains("Source"))
            return;

        CompoundTag data = tag.getCompound("Source");
        activeSource = AllDataBehaviors.getSource(ResourceLocation.parse(data.getString("Id")));
        sourceConfig = new CompoundTag();
        if (activeSource != null)
            sourceConfig = data.copy();
    }



    public void target(BlockPos targetPosition) {
        this.targetOffset = targetPosition.subtract(worldPosition);
        setChanged();
    }

    public BlockPos getSourcePosition() {
        return worldPosition.relative(getDirection());
    }

    public CompoundTag getSourceConfig() {
        return sourceConfig;
    }

    public void setSourceConfig(CompoundTag sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    public Direction getDirection() {
        return getBlockState().getOptionalValue(DataLinkBlock.FACING)
                .orElse(Direction.UP)
                .getOpposite();
    }

    public BlockPos getTargetPosition() {
        return worldPosition.offset(targetOffset);
    }
}
