package com.happysg.radar.networking.packets;


import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.compat.sable.SableRadarCompat;
import com.happysg.radar.config.RadarConfig;
import com.happysg.radar.item.binos.Binoculars;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;
import java.util.List;

public class RaycastPacket implements CustomPacketPayload {
    public static final Type<RaycastPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "raycast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaycastPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> encode(pkt, buf), RaycastPacket::decode);

    private static final double STEP = 0.25;
    private static final double SABLE_RAY_TOLERANCE = 4.0;
    private static final double EPSILON = 1.0E-8;

    public RaycastPacket() {}

    public static void encode(RaycastPacket msg, FriendlyByteBuf buf) {

    }

    public static RaycastPacket decode(FriendlyByteBuf buf) {
        return new RaycastPacket();
    }

    public static void handle(RaycastPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player.level() instanceof ServerLevel serverLevel)) return;
            if (!player.isUsingItem()) return;

            if (!(player.getUseItem().getItem() instanceof Binoculars)) return;

            double maxDistance = RadarConfig.server().binoRaycastRange.get();
            RaycastHit hit = raycastTarget(serverLevel, player, maxDistance, STEP);

            if (hit != null) {
                Binoculars.setLastHit(player.getUseItem(), hit.position());

                player.displayClientMessage((Component.translatable(CreateRadar.MODID + ".binoculars.hit")).append(hit.displayPos().toShortString()),true);
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

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    @Nullable
    private static RaycastHit raycastTarget(ServerLevel level, Player player, double maxDistance, double step) {
        Ray ray = makeWorldRay(level, player);

        RaycastHit blockHit = raycastFirstNonTransparentBlock(level, ray.start(), ray.direction(), maxDistance, step);
        RaycastHit sableHit = raycastSableSubLevel(level, player, ray.start(), ray.direction(), maxDistance);

        if (blockHit == null) {
            return sableHit;
        }
        if (sableHit == null) {
            return blockHit;
        }
        return sableHit.distance() < blockHit.distance() ? sableHit : blockHit;
    }

    private static Ray makeWorldRay(ServerLevel level, Player player) {
        Vec3 localStart = player.getEyePosition();
        Vec3 localDir = player.getLookAngle();

        Vec3 worldStart = SableRadarCompat.projectToWorld(level, localStart);
        Vec3 worldDir = SableRadarCompat.projectDirectionToWorld(level, localStart, localDir);
        if (worldDir.lengthSqr() < EPSILON) {
            worldDir = localDir;
        }

        return new Ray(worldStart, worldDir.normalize());
    }

    @Nullable
    private static RaycastHit raycastFirstNonTransparentBlock(ServerLevel level, Vec3 start, Vec3 dir, double maxDistance, double step) {

        BlockPos lastPos = BlockPos.containing(start);

        for (double t = 0.0; t <= maxDistance; t += step) {
            Vec3 p = start.add(dir.scale(t));
            BlockPos pos = BlockPos.containing(p);
            if (pos.equals(lastPos)) continue;
            lastPos = pos;

            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);

            if (state.isAir()) continue;

            if (isTransparentPassThrough(level, pos, state)) continue;

            return new RaycastHit(pos.getCenter(), pos, t);
        }

        return null;
    }

    @Nullable
    private static RaycastHit raycastSableSubLevel(ServerLevel level, Player player, Vec3 start, Vec3 dir, double maxDistance) {
        if (!SableRadarCompat.isAvailable()) {
            return null;
        }

        Vec3 end = start.add(dir.scale(maxDistance));
        AABB scanBox = rayBox(start, end).inflate(SABLE_RAY_TOLERANCE);
        String ignoredSubLevelId = null;

        if (RadarConfig.server().preventSableSelfTargeting.get()) {
            ignoredSubLevelId = SableRadarCompat.getContainingSubLevelId(level, player.getEyePosition());
            if (ignoredSubLevelId == null) {
                ignoredSubLevelId = SableRadarCompat.getContainingSubLevelId(level, start);
            }
        }

        List<RadarTrack> tracks = SableRadarCompat.collectSubLevelTracks(
                level,
                scanBox,
                ignoredSubLevelId,
                position -> isNearRay(position, start, dir, maxDistance, SABLE_RAY_TOLERANCE),
                level.getGameTime()
        );

        RaycastHit best = null;
        for (RadarTrack track : tracks) {
            Vec3 target = track.position();
            if (target == null) {
                continue;
            }

            double distance = distanceAlongRay(target, start, dir);
            if (distance < 0.0 || distance > maxDistance) {
                continue;
            }

            Vec3 hitPos = target;
            AABB trackBox = SableRadarCompat.getTrackWorldAabb(level, track);
            if (trackBox != null) {
                Vec3 clipped = trackBox.inflate(0.25).clip(start, end).orElse(null);
                if (clipped != null) {
                    hitPos = clipped;
                    distance = clipped.distanceTo(start);
                }
            }

            RaycastHit hit = new RaycastHit(hitPos, BlockPos.containing(hitPos), distance);
            if (best == null || hit.distance() < best.distance()) {
                best = hit;
            }
        }

        return best;
    }

    private static boolean isNearRay(Vec3 position, Vec3 start, Vec3 dir, double maxDistance, double tolerance) {
        double distance = distanceAlongRay(position, start, dir);
        if (distance < 0.0 || distance > maxDistance) {
            return false;
        }

        Vec3 nearest = start.add(dir.scale(distance));
        return nearest.distanceToSqr(position) <= tolerance * tolerance;
    }

    private static double distanceAlongRay(Vec3 position, Vec3 start, Vec3 dir) {
        return position.subtract(start).dot(dir);
    }

    private static AABB rayBox(Vec3 start, Vec3 end) {
        return new AABB(
                Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)
        );
    }

    private static boolean isTransparentPassThrough(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) return true;

        if (!state.canOcclude() || !state.isSolidRender(level, pos)) return true;
        if (!state.getFluidState().isEmpty()) return true;

        return false;
    }

    private record Ray(Vec3 start, Vec3 direction) {
    }

    private record RaycastHit(Vec3 position, BlockPos displayPos, double distance) {
    }
}
