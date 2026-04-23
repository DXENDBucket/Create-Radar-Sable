package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.utils.ItemNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class BoolListPacket implements CustomPacketPayload {
    public static final Type<BoolListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "bool_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BoolListPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> encode(pkt, buf), BoolListPacket::decode);

    private static final int DETECTION_FLAG_COUNT = 6;
    private static final int TARGET_FLAG_COUNT = 7;

    public final boolean mainHand;
    public final boolean[] flags;
    public final String key;

    public BoolListPacket(boolean mainHand, boolean[] flags, String key) {
        this.mainHand = mainHand;
        this.flags = flags;
        this.key = key;
    }

    public static void encode(BoolListPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.mainHand);
        buf.writeUtf(pkt.key);
        buf.writeInt(pkt.flags.length);
        for (boolean b : pkt.flags) buf.writeBoolean(b);
    }

    public static BoolListPacket decode(FriendlyByteBuf buf) {
        boolean main = buf.readBoolean();
        String key = buf.readUtf();
        int len = buf.readInt();
        boolean[] f = new boolean[len];
        for (int i = 0; i < len; i++) f[i] = buf.readBoolean();
        return new BoolListPacket(main, f, key);
    }

    public static void handle(BoolListPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            if (pkt.flags == null) return;

            InteractionHand hand = pkt.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty()) return;

            try {
                if ("detectBools".equals(pkt.key)) {
                    if (pkt.flags.length != DETECTION_FLAG_COUNT) return;

                    CompoundTag root = ItemNbt.getOrCreateTag(stack);

                    CompoundTag filters = root.contains("Filters", Tag.TAG_COMPOUND) ? root.getCompound("Filters") : new CompoundTag();

                    CompoundTag det = new CompoundTag();
                    det.putBoolean("player", pkt.flags[0]);
                    det.putBoolean("contraption", pkt.flags[1]);
                    det.putBoolean("mob", pkt.flags[2]);
                    det.putBoolean("animal", pkt.flags[3]);
                    det.putBoolean("projectile", pkt.flags[4]);
                    det.putBoolean("item", pkt.flags[5]);

                    byte[] arr = new byte[pkt.flags.length];
                    for (int i = 0; i < pkt.flags.length; i++) arr[i] = (byte) (pkt.flags[i] ? 1 : 0);
                    root.putByteArray(pkt.key, arr);

                    filters.put("detection", det);
                    root.put("Filters", filters);

                    ItemNbt.setTag(stack, root);
                } else if ("TargetBools".equals(pkt.key)) {
                    if (pkt.flags.length != TARGET_FLAG_COUNT) return;

                    CompoundTag root = ItemNbt.getOrCreateTag(stack);
                    CompoundTag filters = root.contains("Filters", Tag.TAG_COMPOUND) ? root.getCompound("Filters") : new CompoundTag();

                    CompoundTag tgt = new CompoundTag();
                    tgt.putBoolean("player", pkt.flags[0]);
                    tgt.putBoolean("contraption", pkt.flags[1]);
                    tgt.putBoolean("mob", pkt.flags[2]);
                    tgt.putBoolean("animal", pkt.flags[3]);
                    tgt.putBoolean("projectile", pkt.flags[4]);
                    tgt.putBoolean("lineSight", pkt.flags[5]);
                    tgt.putBoolean("autoTarget", pkt.flags[6]);

                    byte[] arr = new byte[pkt.flags.length];
                    for (int i = 0; i < pkt.flags.length; i++) arr[i] = (byte) (pkt.flags[i] ? 1 : 0);
                    root.putByteArray(pkt.key, arr);

                    filters.put("targeting", tgt);
                    root.put("Filters", filters);
                    ItemNbt.setTag(stack, root);
                }

                player.setItemInHand(hand, stack);
                player.inventoryMenu.broadcastChanges();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
