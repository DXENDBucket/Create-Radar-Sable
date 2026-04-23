package com.happysg.radar.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;

public final class NbtCompat {
    private NbtCompat() {
    }

    @Nullable
    public static BlockPos readBlockPos(CompoundTag tag, String key) {
        return NbtUtils.readBlockPos(tag, key).orElseGet(() -> readBlockPosTag(tag.get(key)));
    }

    public static BlockPos readBlockPosOrDefault(CompoundTag tag, String key, BlockPos fallback) {
        BlockPos pos = readBlockPos(tag, key);
        return pos == null ? fallback : pos;
    }

    public static ListTag getBlockPosList(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_INT_ARRAY);
        if (!list.isEmpty()) {
            return list;
        }
        return tag.getList(key, Tag.TAG_COMPOUND);
    }

    @Nullable
    public static BlockPos readBlockPosListEntry(ListTag list, int index) {
        return readBlockPosTag(list.get(index));
    }

    @Nullable
    public static BlockPos readBlockPosTag(@Nullable Tag tag) {
        if (tag instanceof IntArrayTag arrayTag) {
            int[] values = arrayTag.getAsIntArray();
            if (values.length >= 3) {
                return new BlockPos(values[0], values[1], values[2]);
            }
        }

        if (tag instanceof CompoundTag compound) {
            if (compound.contains("X", Tag.TAG_INT) && compound.contains("Y", Tag.TAG_INT) && compound.contains("Z", Tag.TAG_INT)) {
                return new BlockPos(compound.getInt("X"), compound.getInt("Y"), compound.getInt("Z"));
            }
            if (compound.contains("x", Tag.TAG_INT) && compound.contains("y", Tag.TAG_INT) && compound.contains("z", Tag.TAG_INT)) {
                return new BlockPos(compound.getInt("x"), compound.getInt("y"), compound.getInt("z"));
            }
        }

        return null;
    }
}
