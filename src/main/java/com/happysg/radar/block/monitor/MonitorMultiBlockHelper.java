package com.happysg.radar.block.monitor;

import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.happysg.radar.block.monitor.MonitorBlock.SHAPE;
import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;


//this is messy but couldn't figure out how to use Create MultiblockHelper
//todo make better
public class MonitorMultiBlockHelper {
    public static boolean refreshAt(Level pLevel, BlockPos pPos) {
        BlockState state = pLevel.getBlockState(pPos);
        if (!state.is(ModBlocks.MONITOR.get()))
            return false;

        Direction facing = state.getValue(FACING);
        Formation formation = findBestFormation(pLevel, pPos, facing);
        if (formation == null || formation.size <= 1) {
            return applySingle(pLevel, pPos, state);
        }

        return applyFormation(pLevel, formation);
    }

    public static boolean isValidFormationFor(Level level, BlockPos controller, Direction facing, int size, BlockPos member) {
        if (size < 1)
            return false;
        if (!isValidFormation(level, controller, facing, size))
            return false;

        Direction right = facing.getClockWise();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (controller.above(i).relative(right, j).equals(member))
                    return true;
            }
        }
        return false;
    }

    public static void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        if (pState.getValue(SHAPE) != MonitorBlock.Shape.SINGLE && !pIsMoving)
            return;

        Direction originFacing = pState.getValue(FACING);

        BlockPos.betweenClosedStream(new AABB(pPos).inflate(RadarConfig.server().monitorMaxSize.get()))
                .forEach(candidate -> {
                    BlockState candState = pLevel.getBlockState(candidate);
                    if (!candState.is(ModBlocks.MONITOR.get())) return;
                    if (candState.getValue(FACING) != originFacing) return;

                    if (pLevel.getBlockEntity(candidate) instanceof MonitorBlockEntity monitor) {
                        int size = getSize(pLevel, candidate);
                        if (size > 1) {
                            formMulti(pState, pLevel, monitor.getControllerPos(), size);
                        }
                    }});
    }

    public static void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (ModBlocks.MONITOR.has(pNewState) && !pIsMoving)
            return;
        if (pLevel.getBlockEntity(pPos) instanceof MonitorBlockEntity monitor) {
            destroyMulti(pState, pLevel, pPos, monitor.getControllerPos(), monitor.getSize());
        }
    }

    static void formMulti(BlockState pState, Level pLevel, BlockPos pPos, int size) {
        MonitorBlock.Shape shape;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == 0 && j == 0) shape = MonitorBlock.Shape.LOWER_RIGHT;
                else if (i == 0 && j == size - 1) shape = MonitorBlock.Shape.LOWER_LEFT;
                else if (i == size - 1 && j == 0) shape = MonitorBlock.Shape.UPPER_RIGHT;
                else if (i == size - 1 && j == size - 1) shape = MonitorBlock.Shape.UPPER_LEFT;
                else if (i == 0) shape = MonitorBlock.Shape.LOWER_CENTER;
                else if (i == size - 1) shape = MonitorBlock.Shape.UPPER_CENTER;
                else if (j == 0) shape = MonitorBlock.Shape.MIDDLE_RIGHT;
                else if (j == size - 1) shape = MonitorBlock.Shape.MIDDLE_LEFT;
                else shape = MonitorBlock.Shape.CENTER;

                Direction facing = pLevel.getBlockState(pPos).getValue(FACING);
                pLevel.setBlockAndUpdate(pPos.above(i).relative(facing.getClockWise(), j), pState.setValue(SHAPE, shape));
                if (pLevel.getBlockEntity(pPos.above(i).relative(facing.getClockWise(), j)) instanceof MonitorBlockEntity monitor) {
                    monitor.setControllerPos(pPos, size);
                }
            }
        }
    }

    private static boolean applyFormation(Level level, Formation formation) {
        boolean changed = false;
        Direction facing = formation.facing;
        BlockPos controller = formation.controller;

        for (int i = 0; i < formation.size; i++) {
            for (int j = 0; j < formation.size; j++) {
                BlockPos pos = controller.above(i).relative(facing.getClockWise(), j);
                BlockState state = level.getBlockState(pos);
                if (!state.is(ModBlocks.MONITOR.get()))
                    continue;

                MonitorBlock.Shape shape = shapeFor(i, j, formation.size);
                if (state.getValue(SHAPE) != shape) {
                    level.setBlockAndUpdate(pos, state.setValue(SHAPE, shape));
                    changed = true;
                }

                if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
                    if (!monitor.getControllerPos().equals(controller) || monitor.getSize() != formation.size) {
                        monitor.setControllerPos(controller, formation.size);
                        changed = true;
                    }
                }
            }
        }

        return changed;
    }

    private static boolean applySingle(Level level, BlockPos pos, BlockState state) {
        boolean changed = false;
        if (state.getValue(SHAPE) != MonitorBlock.Shape.SINGLE) {
            level.setBlockAndUpdate(pos, state.setValue(SHAPE, MonitorBlock.Shape.SINGLE));
            changed = true;
        }

        if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            if (!monitor.getControllerPos().equals(pos) || monitor.getSize() != 1) {
                monitor.setControllerPos(pos, 1);
                changed = true;
            }
        }

        return changed;
    }

    private static Formation findBestFormation(Level level, BlockPos pos, Direction facing) {
        Formation best = null;
        Direction right = facing.getClockWise();
        int maxSize = RadarConfig.server().monitorMaxSize.get();

        for (int size = 1; size <= maxSize; size++) {
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    BlockPos controller = pos.below(i).relative(right, -j);
                    if (!isValidFormation(level, controller, facing, size))
                        continue;

                    if (best == null || size > best.size) {
                        best = new Formation(controller, facing, size);
                    }
                }
            }
        }

        return best;
    }

    private static boolean isValidFormation(Level level, BlockPos controller, Direction facing, int size) {
        Direction right = facing.getClockWise();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                BlockPos pos = controller.above(i).relative(right, j);
                BlockState state = level.getBlockState(pos);
                if (!state.is(ModBlocks.MONITOR.get()))
                    return false;
                if (state.getValue(FACING) != facing)
                    return false;
            }
        }
        return true;
    }

    private static MonitorBlock.Shape shapeFor(int i, int j, int size) {
        if (size <= 1) return MonitorBlock.Shape.SINGLE;
        if (i == 0 && j == 0) return MonitorBlock.Shape.LOWER_RIGHT;
        if (i == 0 && j == size - 1) return MonitorBlock.Shape.LOWER_LEFT;
        if (i == size - 1 && j == 0) return MonitorBlock.Shape.UPPER_RIGHT;
        if (i == size - 1 && j == size - 1) return MonitorBlock.Shape.UPPER_LEFT;
        if (i == 0) return MonitorBlock.Shape.LOWER_CENTER;
        if (i == size - 1) return MonitorBlock.Shape.UPPER_CENTER;
        if (j == 0) return MonitorBlock.Shape.MIDDLE_RIGHT;
        if (j == size - 1) return MonitorBlock.Shape.MIDDLE_LEFT;
        return MonitorBlock.Shape.CENTER;
    }

    private record Formation(BlockPos controller, Direction facing, int size) {}

    private static void destroyMulti(BlockState pState, Level pLevel, BlockPos removedPos, BlockPos controllerPos, int size) {
        if (size == 1)
            return;
        if (pLevel.getBlockEntity(removedPos) instanceof MonitorBlockEntity monitor && monitor.getControllerPos().equals(controllerPos)) {
            monitor.setControllerPos(removedPos, 1);
        }
        Direction facing = pState.getValue(FACING);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                BlockPos pos = controllerPos.above(i).relative(facing.getClockWise(), j);
                if (pos.equals(removedPos))
                    continue;
                if (pLevel.getBlockEntity(pos) instanceof MonitorBlockEntity monitor && monitor.getControllerPos().equals(controllerPos)) {
                    monitor.setControllerPos(pos, 1);
                    monitor.onDataLinkRemoved();
                    pLevel.setBlockAndUpdate(pos, pState.setValue(SHAPE, MonitorBlock.Shape.SINGLE));
                }
            }
        }
    }



    public static int getSize(Level pLevel, BlockPos pPos) {
        if (!pLevel.getBlockState(pPos).is(ModBlocks.MONITOR.get()))
            return 0;
        Direction facing = pLevel.getBlockState(pPos).getValue(FACING);
        int potentialsize = 0;
        for (int i = 0; i < RadarConfig.server().monitorMaxSize.get(); i++) {
            AtomicBoolean valid = new AtomicBoolean(true);
            BlockPos.betweenClosed(pPos, pPos.above(i).relative(facing.getClockWise(), i)).forEach(p -> {
                if (!pLevel.getBlockState(p).is(ModBlocks.MONITOR.get()))
                    valid.set(false);
            });
            if (valid.get())
                potentialsize = i + 1;
            else
                break;
        }
        if (potentialsize == 1)
            return 1;

        for (int i = 0; i < potentialsize; i++) {
            for (int j = 0; j < potentialsize; j++) {
                BlockEntity be = pLevel.getBlockEntity(pPos.above(i).relative(facing.getClockWise(), j));
                if (!(be instanceof MonitorBlockEntity monitor && monitor.getSize() < potentialsize))
                    return Math.max(1, Math.min(i, j));

            }
        }

        return potentialsize;

    }

    public static boolean isMulti(Level pLevel, BlockPos pos) {
        if (!pLevel.getBlockState(pos).is(ModBlocks.MONITOR.get()))
            return false;
        return getSize(pLevel, pos) > 1;
    }


    //todo add a size verification and reupdate multiblock if necessary
    public static void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {


    }
}
