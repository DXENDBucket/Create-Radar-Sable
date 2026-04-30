package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.item.binos.Binoculars;
import com.happysg.radar.utils.ItemNbt;
import com.happysg.radar.utils.NbtCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public class FirePacket implements CustomPacketPayload {
    public static final Type<FirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "fire"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FirePacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> encode(pkt, buf), FirePacket::decode);

    private static final String TAG_FILTERER_POS = "filtererPos";


    private final boolean enable;

    public FirePacket(boolean enable) {
        this.enable = enable;
    }
    public static void encode(FirePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enable);
    }
    public static FirePacket decode(FriendlyByteBuf buf) {
        return new FirePacket(buf.readBoolean());
    }

    public static void handle(FirePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player.level() instanceof ServerLevel serverLevel)) return;

           // if (!player.isUsingItem()) return;

            ItemStack binos = findBinosStack(player);
            if (binos.isEmpty()) return;

            BlockPos filtererPos = getFiltererPos(binos);
            if (filtererPos == null) return;
            if (!serverLevel.isLoaded(filtererPos)) return;

            if (!(serverLevel.getBlockEntity(filtererPos) instanceof NetworkFiltererBlockEntity filtererBe)) return;

            if (msg.enable) {
                Vec3 hit = Binoculars.getLastHitVec(binos);
                if (hit == null) return;
                filtererBe.onBinocularsTriggered(player,binos, false);

            } else {
                // release: go back to normal
                filtererBe.onBinocularsTriggered(player, binos, true);
            }

            filtererBe.setChanged();
        });

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static ItemStack findBinosStack(Player player) {
        // i prioritize 鈥渦sing item鈥?(scoped) because that鈥檚 the cleanest intent
        ItemStack using = player.getUseItem();
        if (!using.isEmpty() && using.getItem() instanceof Binoculars) return using;

        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.getItem() instanceof Binoculars) return main;

        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.getItem() instanceof Binoculars) return off;

        return ItemStack.EMPTY;
    }

    @Nullable
    private static BlockPos getFiltererPos(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null) return null;
        if (!tag.contains(TAG_FILTERER_POS)) return null;

        return NbtCompat.readBlockPos(tag, TAG_FILTERER_POS);
    }
}
