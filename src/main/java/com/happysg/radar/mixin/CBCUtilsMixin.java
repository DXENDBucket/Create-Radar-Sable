package com.happysg.radar.mixin;

import com.happysg.radar.compat.sable.SableRadarCompat;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.multiloader.NetworkPlatform;
import rbasamoyai.createbigcannons.network.ClientboundSendCustomBreakProgressPacket;
import rbasamoyai.createbigcannons.utils.CBCUtils;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(value = CBCUtils.class, remap = false)
public abstract class CBCUtilsMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double PROJECTED_DAMAGE_PACKET_RANGE_SQR = 128.0 * 128.0;

    @Inject(method = "sendCustomBlockDamage", at = @At("TAIL"), remap = false)
    private static void create_radar$sendSableBlockDamageToProjectedViewers(Level level, BlockPos pos, int damage, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || pos == null || !SableRadarCompat.isAvailable()) {
            return;
        }

        Vec3 localCenter = pos.getCenter();
        Vec3 projectedCenter = SableRadarCompat.projectToWorld(level, localCenter);
        if (projectedCenter == null || projectedCenter.distanceToSqr(localCenter) < 1.0E-4) {
            return;
        }

        // Keep the local Sable plot position so Sable's client renderer can transform the crack decal.
        ClientboundSendCustomBreakProgressPacket packet =
                new ClientboundSendCustomBreakProgressPacket(pos, damage);
        ClientboundBlockDestructionPacket vanillaPacket =
                new ClientboundBlockDestructionPacket(create_radar$breakProgressId(pos), pos, damage);

        Set<ServerPlayer> recipients = new LinkedHashSet<>(SableRadarCompat.getTrackingPlayers(serverLevel, pos));
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(projectedCenter) < PROJECTED_DAMAGE_PACKET_RANGE_SQR) {
                recipients.add(player);
            }
        }

        for (ServerPlayer player : recipients) {
            NetworkPlatform.sendToClientPlayer(packet, player);
            player.connection.send(vanillaPacket);
        }

        LOGGER.debug("Forwarded Sable block damage pos={} damage={} projected={} recipients={}",
                pos, damage, projectedCenter, recipients.size());
    }

    private static int create_radar$breakProgressId(BlockPos pos) {
        return Integer.MIN_VALUE ^ Long.hashCode(pos.asLong());
    }
}
