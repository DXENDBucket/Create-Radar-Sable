package com.happysg.radar.block.mount;

import com.happysg.radar.block.mount.SmartMountBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.KineticDebugger;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaternionf;
import rbasamoyai.createbigcannons.index.CBCBlockPartials;

public class SmartMountVisual extends KineticBlockEntityVisual<SmartMountBlockEntity> implements SimpleDynamicVisual {
    private final OrientedInstance rotatingMount;
    private final OrientedInstance rotatingMountShaft;
    private final RotatingInstance yawShaft;

    public SmartMountVisual(VisualizationContext ctx, SmartMountBlockEntity tile, float partialTick) {
        super(ctx, tile, partialTick);
        Direction vertical = (Direction)tile.getBlockState().getValue(BlockStateProperties.VERTICAL_DIRECTION);
        Direction facing = (Direction)tile.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        this.rotatingMount = ((OrientedInstance)this.instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(CBCBlockPartials.ROTATING_MOUNT)).createInstance()).position(this.getVisualPosition().relative(vertical.getOpposite()));
        this.rotatingMountShaft = ((OrientedInstance)this.instancerProvider().instancer(InstanceTypes.ORIENTED, Models.partial(CBCBlockPartials.CANNON_CARRIAGE_AXLE)).createInstance()).position(this.getVisualPosition().relative(vertical, -2));
        this.yawShaft = ((RotatingInstance)this.instancerProvider().instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF)).createInstance()).rotateToFace(Direction.SOUTH, vertical).setup(((SmartMountBlockEntity)this.blockEntity).getYawInterface()).setColor(((SmartMountBlockEntity)this.blockEntity).getYawInterface()).setPosition(this.getVisualPosition());
        this.transformModels();
    }

    public void _delete() {
        this.rotatingMount.delete();
        this.rotatingMountShaft.delete();
        this.yawShaft.delete();
    }

    private void transformModels() {
        this.updateRotation(this.yawShaft, Axis.Y, ((SmartMountBlockEntity)this.blockEntity).getYawSpeed(), false);
    }

    protected void updateRotation(RotatingInstance instance, Direction.Axis axis, float speed, boolean pitch) {
        instance.setRotationAxis(axis).setRotationOffset(rotationOffset(this.blockState, axis, this.pos)).setRotationalSpeed(speed * 6.0F);
        if (KineticDebugger.isActive()) {
            instance.setColor(pitch ? ((SmartMountBlockEntity)this.blockEntity).getPitchInterface() : ((SmartMountBlockEntity)this.blockEntity).getYawInterface());
        }

        instance.setChanged();
    }

    public void beginFrame(DynamicVisual.Context ctx) {
        this.transformModels();
        float partialTicks = ctx.partialTick();
        boolean upsideDown = this.blockState.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.UP;
        float yaw = ((SmartMountBlockEntity)this.blockEntity).getYawOffset(partialTicks);
        Quaternionf qyaw = upsideDown ? com.mojang.math.Axis.ZP.rotationDegrees(180.0F).mul(com.mojang.math.Axis.YP.rotationDegrees(yaw)) : com.mojang.math.Axis.YP.rotationDegrees(-yaw);
        this.rotatingMount.rotation(qyaw);
        float pitch = ((SmartMountBlockEntity)this.blockEntity).getPitchOffset(partialTicks);
        Quaternionf qpitch = upsideDown ? com.mojang.math.Axis.XP.rotationDegrees(pitch) : com.mojang.math.Axis.XP.rotationDegrees(-pitch);
        Quaternionf qyaw1 = new Quaternionf(qyaw);
        qyaw1.mul(qpitch);
        this.rotatingMountShaft.rotation(qyaw1);
        this.rotatingMount.setChanged();
        this.rotatingMountShaft.setChanged();
        this.yawShaft.setChanged();
    }

    public void updateLight(float partialTicks) {
        Direction vertical = ((Direction)this.blockState.getValue(BlockStateProperties.VERTICAL_DIRECTION)).getOpposite();
        this.relight(this.pos.relative(vertical), new FlatLit[]{this.rotatingMount});
        this.relight(this.pos.relative(vertical, 2), new FlatLit[]{this.rotatingMountShaft});
        this.relight(this.pos, new FlatLit[]{this.yawShaft});
    }

    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.rotatingMount);
        consumer.accept(this.rotatingMountShaft);
        consumer.accept(this.yawShaft);
    }
}
