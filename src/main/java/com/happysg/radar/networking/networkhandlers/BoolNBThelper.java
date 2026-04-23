package com.happysg.radar.networking.networkhandlers;

import com.happysg.radar.utils.ItemNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class BoolNBThelper {
    private BoolNBThelper() {}

    public static void saveBooleansAsBytes(ItemStack stack, boolean[] flags, String key) {
        if (stack == null || flags == null || key == null) return;
        CompoundTag tag = ItemNbt.getOrCreateTag(stack);
        byte[] arr = new byte[flags.length];
        for (int i = 0; i < flags.length; i++) arr[i] = (byte) (flags[i] ? 1 : 0);
        tag.putByteArray(key, arr);
        ItemNbt.setTag(stack, tag);
    }

    public static boolean[] loadBooleansFromBytes(ItemStack stack, String key, int expectedLength) {
        boolean[] res = new boolean[Math.max(0, expectedLength)];
        if (stack == null || key == null || expectedLength <= 0) return res;
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null || !tag.contains(key)) return res;
        try {
            byte[] arr = tag.getByteArray(key);
            int len = Math.min(arr.length, res.length);
            for (int i = 0; i < len; i++) res[i] = arr[i] != 0;
        } catch (Throwable t) { /* defensive; return defaults */ }
        return res;
    }

    public static void loadBooleansInto(ItemStack stack, String key, boolean[] dest) {
        if (dest == null) return;
        boolean[] tmp = loadBooleansFromBytes(stack, key, dest.length);
        System.arraycopy(tmp, 0, dest, 0, Math.min(tmp.length, dest.length));
    }
}

