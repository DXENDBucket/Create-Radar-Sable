package com.happysg.radar.networking.packets;


import com.happysg.radar.CreateRadar;
import com.happysg.radar.compat.Mods;
import com.happysg.radar.compat.vs2.VS2Utils;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.item.binos.Binoculars;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;

public class RaycastPacket {

    // tune these however you want
    private static final double MAX_DISTANCE = RadarConfig.server().binoRaycastRange.get();
    private static final double STEP = 0.25;

    public RaycastPacket() {}

    public static void encode(RaycastPacket msg, FriendlyByteBuf buf) {

    }

    public static RaycastPacket decode(FriendlyByteBuf buf) {
        return new RaycastPacket();
    }

    public static void handle(RaycastPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Player player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel serverLevel)) return;
            if (!player.isUsingItem()) return;

            if (!(player.getUseItem().getItem() instanceof Binoculars)) return;

            BlockPos hit = raycastFirstNonTransparentBlock(serverLevel, player, MAX_DISTANCE, STEP);

            if (hit != null) {
                Binoculars.setLastHit(player.getUseItem(), hit);

                player.displayClientMessage((Component.translatable(CreateRadar.MODID + ".binoculars.hit")).append(hit.toShortString()),true);
            } else {
                Binoculars.clearLastHit(player.getUseItem());

                player.displayClientMessage(
                        Component.translatable(
                                CreateRadar.MODID + ".binoculars.out_of_range"
                        ),
                        true
                );
            }
        });

        ctx.get().setPacketHandled(true);
    }


    @Nullable
    private static BlockPos raycastFirstNonTransparentBlock(ServerLevel level, Player player, double maxDistance, double step) {
        Vec3 start = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();

        BlockPos lastPos = BlockPos.containing(start);

        for (double t = 0.0; t <= maxDistance; t += step) {
            Vec3 p = start.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(p);
            if(Mods.VALKYRIENSKIES.isLoaded() && VS2Utils.isBlockInShipyard(level,pos)){
                pos =  VS2Utils.getWorldPos(level,pos);
            }

            if (pos.equals(lastPos)) continue;
            lastPos = pos;

            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);

            if (state.isAir()) continue;

            if (isTransparentPassThrough(level, pos, state)) continue;

            return pos;
        }

        return null;
    }

    private static boolean isTransparentPassThrough(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) return true;

        if (!state.canOcclude() || !state.isSolidRender(level, pos)) return true;
        if (!state.getFluidState().isEmpty()) return true;

        return false;
    }

    private static void storeLastHit(net.minecraft.world.item.ItemStack stack, ResourceKey<Level> dim, BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();

        CompoundTag hit = new CompoundTag();
        hit.putInt("x", pos.getX());
        hit.putInt("y", pos.getY());
        hit.putInt("z", pos.getZ());
        hit.putString("dim", dim.location().toString());

        tag.put("LastHitPos", hit);
    }

    private static void clearLastHit(net.minecraft.world.item.ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove("LastHitPos");
    }
}
