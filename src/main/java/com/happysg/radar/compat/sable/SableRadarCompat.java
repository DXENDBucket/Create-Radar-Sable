package com.happysg.radar.compat.sable;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class SableRadarCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TRACK_ID_PREFIX = "sable:";
    private static final double SABLE_VELOCITY_TO_TICKS = 1.0 / 20.0;

    private static boolean initialized;
    private static boolean available;
    private static boolean warned;

    private static Class<?> subLevelContainerClass;
    private static Object companion;

    private static Method getContainer;
    private static Method getContainerForLevel;
    private static Method getAllSubLevels;
    private static Method getSubLevelByUuid;
    private static Method getAllIntersecting;
    private static Method projectOutOfSubLevel;
    private static Method getContaining;
    private static Method getVelocityAt;
    private static Method getVelocityInSubLevel;
    private static Constructor<?> boundingBox3dFromAabb;

    private SableRadarCompat() {
    }

    public static boolean isAvailable() {
        if (!initialized) {
            initialize();
        }
        return available;
    }

    public static Vec3 projectToWorld(Level level, Vec3 position) {
        if (level == null || position == null || !isAvailable()) {
            return position;
        }

        try {
            Object projected = projectOutOfSubLevel.invoke(companion, level, position);
            if (projected instanceof Vec3 vec) {
                return vec;
            }
        } catch (Throwable throwable) {
            warnOnce("Failed to project Sable position into world space", throwable);
        }

        return position;
    }

    public static double projectYawToWorld(Level level, Vec3 localOrigin, double yawDegrees) {
        double normalizedYaw = normalizeYaw(yawDegrees);
        if (level == null || localOrigin == null || !isAvailable()) {
            return normalizedYaw;
        }

        double yawRadians = Math.toRadians(normalizedYaw);
        Vec3 localDirection = new Vec3(Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Vec3 worldDirection = projectDirectionToWorld(level, localOrigin, localDirection);

        double horizontalLengthSqr = worldDirection.x * worldDirection.x + worldDirection.z * worldDirection.z;
        if (horizontalLengthSqr < 1.0E-8) {
            return normalizedYaw;
        }

        return normalizeYaw(Math.toDegrees(Math.atan2(worldDirection.x, worldDirection.z)));
    }

    public static Vec3 projectDirectionToWorld(Level level, Vec3 localOrigin, Vec3 localDirection) {
        if (level == null || localOrigin == null || localDirection == null || !isAvailable()) {
            return localDirection;
        }

        Vec3 worldOrigin = projectToWorld(level, localOrigin);
        Vec3 worldTip = projectToWorld(level, localOrigin.add(localDirection));
        return worldTip.subtract(worldOrigin);
    }

    public static Vec3 projectHorizontalDirectionToWorld(Level level, Vec3 localOrigin, Direction localDirection) {
        Vec3 fallback = directionToHorizontalVector(localDirection);
        Vec3 projected = projectDirectionToWorld(level, localOrigin, fallback);
        Vec3 horizontal = new Vec3(projected.x, 0.0, projected.z);
        double lengthSqr = horizontal.lengthSqr();
        if (lengthSqr < 1.0E-8) {
            return fallback;
        }
        return horizontal.scale(1.0 / Math.sqrt(lengthSqr));
    }

    private static Vec3 directionToHorizontalVector(Direction direction) {
        if (direction == null) {
            return Vec3.ZERO;
        }
        return new Vec3(direction.getStepX(), 0.0, direction.getStepZ());
    }

    private static double normalizeYaw(double yawDegrees) {
        double normalized = yawDegrees % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    public static boolean isInSubLevel(Level level, Vec3 localPosition) {
        if (level == null || localPosition == null || !isAvailable()) {
            return false;
        }

        try {
            return getContaining.invoke(companion, level, localPosition) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static Vec3 projectWorldToLocal(Level level, Vec3 referenceLocalPosition, Vec3 worldPosition) {
        if (level == null || referenceLocalPosition == null || worldPosition == null || !isAvailable()) {
            return worldPosition;
        }

        try {
            Object subLevel = getContaining.invoke(companion, level, referenceLocalPosition);
            Object pose = subLevel == null ? null : invokeNoArg(subLevel, "logicalPose");
            if (pose == null) {
                return worldPosition;
            }

            Method inverse = pose.getClass().getMethod("transformPositionInverse", Vec3.class);
            Object local = inverse.invoke(pose, worldPosition);
            if (local instanceof Vec3 vec) {
                return vec;
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to project Sable world position {} into local space near {}", worldPosition, referenceLocalPosition, throwable);
        }

        return worldPosition;
    }

    public static Vec3 projectEntityPosition(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        return projectToWorld(entity.level(), entity.position());
    }

    public static Vec3 getEntityVelocity(Entity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }

        Vec3 ownVelocity = entity.getDeltaMovement();
        Vec3 subLevelVelocity = getVelocity(entity.level(), entity.position());
        return ownVelocity.add(subLevelVelocity);
    }

    @Nullable
    public static String getContainingSubLevelId(Level level, Vec3 position) {
        if (level == null || position == null || !isAvailable()) {
            return null;
        }

        try {
            Object subLevel = getContaining.invoke(companion, level, position);
            UUID id = getSubLevelUuid(subLevel);
            if (id != null) {
                return id.toString();
            }
        } catch (Throwable throwable) {
            warnOnce("Failed to resolve containing Sable sub-level", throwable);
        }

        Vec3 projected = projectToWorld(level, position);
        if (projected.distanceToSqr(position) > 1.0E-4) {
            try {
                Object subLevel = getContaining.invoke(companion, level, projected);
                UUID id = getSubLevelUuid(subLevel);
                if (id != null) {
                    return id.toString();
                }
            } catch (Throwable ignored) {
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            String id = getContainingSubLevelIdFromBounds(serverLevel, position);
            if (id != null) {
                return id;
            }

            if (projected.distanceToSqr(position) > 1.0E-4) {
                return getContainingSubLevelIdFromBounds(serverLevel, projected);
            }
        }

        return null;
    }

    public static boolean isTrackForSubLevel(@Nullable RadarTrack track, @Nullable String subLevelId) {
        if (track == null || subLevelId == null || !track.isSableSubLevel()) {
            return false;
        }

        UUID trackUuid = getTrackSubLevelUuid(track);
        return trackUuid != null && trackUuid.toString().equals(subLevelId);
    }

    public static boolean trackContainsPosition(Level level, @Nullable RadarTrack track, Vec3 position) {
        if (level == null || track == null || position == null || !track.isSableSubLevel() || !isAvailable()) {
            return false;
        }

        UUID uuid = getTrackSubLevelUuid(track);
        if (uuid == null || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        Object subLevel = getSubLevel(serverLevel, uuid);
        if (subLevel == null || isRemoved(subLevel)) {
            return false;
        }

        AABB rawBox = getSubLevelRawAabb(subLevel);
        Vec3 projectedPosition = projectToWorld(level, position);

        if (containsWithTolerance(rawBox, position) || containsWithTolerance(rawBox, projectedPosition)) {
            return true;
        }

        AABB projectedBox = projectAabbToWorld(level, rawBox);
        return containsWithTolerance(projectedBox, position) || containsWithTolerance(projectedBox, projectedPosition);
    }

    public static boolean isTrackAtPosition(Level level, @Nullable RadarTrack track, Vec3 position) {
        if (level == null || track == null || position == null || !track.isSableSubLevel()) {
            return false;
        }

        if (trackContainsPosition(level, track, position)) {
            return true;
        }

        String subLevelId = getContainingSubLevelId(level, position);
        return isTrackForSubLevel(track, subLevelId);
    }

    public static boolean isTrackValid(ServerLevel level, @Nullable RadarTrack track) {
        if (level == null || !isSableTrack(track) || !isAvailable()) {
            return false;
        }

        UUID uuid = getTrackSubLevelUuid(track);
        if (uuid == null) {
            return false;
        }

        Object subLevel = getSubLevel(level, uuid);
        return isTrackableSubLevel(subLevel);
    }

    public static List<ServerPlayer> getTrackingPlayers(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !isAvailable()) {
            return List.of();
        }

        List<ServerPlayer> players = new ArrayList<>();

        try {
            Object container = getContainer(level);
            if (container == null) {
                return players;
            }

            Method getPlayersTracking = container.getClass().getMethod("getPlayersTracking", ChunkPos.class);
            Object result = getPlayersTracking.invoke(container, new ChunkPos(pos));
            if (result instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    addTrackingPlayer(level, players, value);
                }
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to resolve Sable tracking players for {}", pos, throwable);
        }

        return players;
    }

    @Nullable
    public static AABB getTrackWorldAabb(ServerLevel level, @Nullable RadarTrack track) {
        if (level == null || !isSableTrack(track) || !isAvailable()) {
            return null;
        }

        UUID uuid = getTrackSubLevelUuid(track);
        if (uuid == null) {
            return null;
        }

        Object subLevel = getSubLevel(level, uuid);
        if (!isTrackableSubLevel(subLevel)) {
            return null;
        }

        AABB rawBox = getSubLevelRawAabb(subLevel);
        return rawBox == null ? null : projectAabbToWorld(level, rawBox);
    }

    public static List<RadarTrack> collectSubLevelTracks(ServerLevel level, AABB scanBox, @Nullable String ignoredSubLevelId,
                                                         Predicate<Vec3> positionFilter, long scannedTime) {
        if (level == null || scanBox == null || !isAvailable()) {
            return List.of();
        }

        List<RadarTrack> tracks = new ArrayList<>();

        try {
            Object container = getContainer(level);
            boolean sawCandidate = addSubLevelTracks(level, queryIntersectingSubLevels(level, scanBox), scanBox,
                    ignoredSubLevelId, positionFilter, scannedTime, tracks);

            if (!sawCandidate && container != null && getAllSubLevels != null) {
                Object all = getAllSubLevels.invoke(container);
                if (all instanceof Iterable<?> subLevels) {
                    addSubLevelTracks(level, subLevels, scanBox, ignoredSubLevelId, positionFilter, scannedTime, tracks);
                }
            }
        } catch (Throwable throwable) {
            warnOnce("Failed to collect Sable radar tracks", throwable);
        }

        return tracks;
    }

    private static boolean addSubLevelTracks(ServerLevel level, @Nullable Iterable<?> subLevels, AABB scanBox,
                                             @Nullable String ignoredSubLevelId, Predicate<Vec3> positionFilter,
                                             long scannedTime, List<RadarTrack> tracks) {
        if (subLevels == null) {
            return false;
        }

        boolean sawCandidate = false;
        for (Object subLevel : subLevels) {
            sawCandidate = true;
            RadarTrack track = createTrack(level, subLevel, scanBox, ignoredSubLevelId, positionFilter, scannedTime);
            if (track != null) {
                tracks.add(track);
            }
        }
        return sawCandidate;
    }

    @Nullable
    private static Iterable<?> queryIntersectingSubLevels(ServerLevel level, AABB scanBox) {
        if (getAllIntersecting == null || boundingBox3dFromAabb == null) {
            return null;
        }

        try {
            Object queryBox = boundingBox3dFromAabb.newInstance(scanBox);
            Object result = getAllIntersecting.invoke(companion, level, queryBox);
            if (result instanceof Iterable<?> iterable) {
                return iterable;
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to query intersecting Sable sub-levels for {}", scanBox, throwable);
        }

        return null;
    }

    public static List<AABB> collectEntityCandidateBoxes(ServerLevel level, AABB worldScanBox, Vec3 localScanCenter,
                                                         double horizontalRange, double yRange) {
        if (level == null || worldScanBox == null) {
            return List.of();
        }

        List<AABB> boxes = new ArrayList<>();
        addDistinct(boxes, worldScanBox);

        if (localScanCenter != null) {
            Vec3 projectedCenter = projectToWorld(level, localScanCenter);
            if (projectedCenter.distanceToSqr(localScanCenter) > 1.0E-4) {
                addDistinct(boxes, scanBoxAround(localScanCenter, horizontalRange, yRange));
            }
        }

        if (!isAvailable()) {
            return boxes;
        }

        try {
            Object container = getContainer(level);
            if (container == null) {
                return boxes;
            }

            if (getAllSubLevels == null) {
                return boxes;
            }

            Object all = getAllSubLevels.invoke(container);
            if (!(all instanceof Iterable<?> subLevels)) {
                return boxes;
            }

            for (Object subLevel : subLevels) {
                if (!isTrackableSubLevel(subLevel)) {
                    continue;
                }

                AABB rawBox = getSubLevelRawAabb(subLevel);
                if (rawBox == null) {
                    continue;
                }

                AABB projectedBox = projectAabbToWorld(level, rawBox);
                if (projectedBox.intersects(worldScanBox)) {
                    addDistinct(boxes, rawBox);
                }
            }
        } catch (Throwable throwable) {
            warnOnce("Failed to collect Sable entity scan boxes", throwable);
        }

        return boxes;
    }

    public static AABB projectAabbToWorld(Level level, AABB box) {
        if (level == null || box == null || !isAvailable()) {
            return box;
        }

        Vec3[] corners = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (Vec3 corner : corners) {
            Vec3 projected = projectToWorld(level, corner);
            minX = Math.min(minX, projected.x);
            minY = Math.min(minY, projected.y);
            minZ = Math.min(minZ, projected.z);
            maxX = Math.max(maxX, projected.x);
            maxY = Math.max(maxY, projected.y);
            maxZ = Math.max(maxZ, projected.z);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Nullable
    public static String getTrackDisplayName(Level level, @Nullable RadarTrack track) {
        if (!isSableTrack(track)) {
            return null;
        }

        String name = normalizeSubLevelName(track.entityType());
        if (name != null) {
            return name;
        }

        if (level == null || !isAvailable()) {
            return null;
        }

        UUID uuid = getTrackSubLevelUuid(track);
        if (uuid == null) {
            return null;
        }

        Object subLevel = getSubLevel(level, uuid);
        return subLevel == null ? null : normalizeSubLevelName(readSubLevelName(subLevel));
    }

    @Nullable
    private static RadarTrack createTrack(ServerLevel level, Object subLevel, AABB scanBox, @Nullable String ignoredSubLevelId,
                                          Predicate<Vec3> positionFilter, long scannedTime) {
        if (!isTrackableSubLevel(subLevel)) {
            return null;
        }

        UUID uuid = getSubLevelUuid(subLevel);
        if (uuid == null) {
            return null;
        }

        String rawId = uuid.toString();
        if (rawId.equals(ignoredSubLevelId)) {
            return null;
        }

        AABB rawBox = getSubLevelRawAabb(subLevel);
        AABB projectedBox = rawBox == null ? null : projectAabbToWorld(level, rawBox);

        Vec3 rawPosition = getSubLevelCenter(subLevel);
        Vec3 position = pickTrackPosition(level, rawPosition, projectedBox, scanBox, positionFilter);
        if (position == null) {
            return null;
        }

        Vec3 velocity = getSubLevelVelocity(level, subLevel, position);
        float height = (float) Math.max(1.0, projectedBox == null ? getSubLevelHeight(subLevel) : projectedBox.getYsize());
        String name = getSubLevelName(subLevel);

        return RadarTrack.sableSubLevel(TRACK_ID_PREFIX + rawId, position, velocity, scannedTime, name, height);
    }

    private static Vec3 getSubLevelVelocity(Level level, Object subLevel, @Nullable Vec3 worldPosition) {
        if (level == null || subLevel == null || worldPosition == null) {
            return Vec3.ZERO;
        }

        if (getVelocityInSubLevel != null && isAvailable()) {
            try {
                Vec3 localPosition = projectWorldToSubLevelLocal(subLevel, worldPosition);
                Object velocity = getVelocityInSubLevel.invoke(companion, level, subLevel, localPosition);
                if (velocity instanceof Vec3 vec) {
                    return vec.scale(SABLE_VELOCITY_TO_TICKS);
                }
            } catch (Throwable throwable) {
                LOGGER.debug("Failed to resolve Sable sub-level velocity at {}", worldPosition, throwable);
            }
        }

        return getVelocity(level, worldPosition);
    }

    private static Vec3 projectWorldToSubLevelLocal(Object subLevel, Vec3 worldPosition) throws ReflectiveOperationException {
        Object pose = invokeNoArg(subLevel, "logicalPose");
        if (pose == null) {
            return worldPosition;
        }

        Method inverse = pose.getClass().getMethod("transformPositionInverse", Vec3.class);
        Object local = inverse.invoke(pose, worldPosition);
        return local instanceof Vec3 vec ? vec : worldPosition;
    }

    @Nullable
    private static Vec3 pickTrackPosition(Level level, @Nullable Vec3 rawCenter, @Nullable AABB projectedBox,
                                          AABB scanBox, Predicate<Vec3> positionFilter) {
        Vec3 projectedCenter = rawCenter == null ? null : projectToWorld(level, rawCenter);
        if (isAcceptedTrackPosition(projectedCenter, scanBox, positionFilter)) {
            return projectedCenter;
        }

        if (projectedBox == null || !projectedBox.intersects(scanBox)) {
            return null;
        }

        Vec3 nearestVisiblePoint = closestPoint(projectedBox, scanBox.getCenter());
        if (isAcceptedTrackPosition(nearestVisiblePoint, scanBox, positionFilter)) {
            return nearestVisiblePoint;
        }

        Vec3 projectedBoxCenter = projectedBox.getCenter();
        if (isAcceptedTrackPosition(projectedBoxCenter, scanBox, positionFilter)) {
            return projectedBoxCenter;
        }

        return null;
    }

    private static boolean isAcceptedTrackPosition(@Nullable Vec3 position, AABB scanBox, Predicate<Vec3> positionFilter) {
        return position != null && scanBox.contains(position) && positionFilter.test(position);
    }

    private static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(
                clamp(point.x, box.minX, box.maxX),
                clamp(point.y, box.minY, box.maxY),
                clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Nullable
    private static Vec3 getSubLevelCenter(Object subLevel) {
        try {
            Object bounds = invokeNoArg(subLevel, "boundingBox");
            if (bounds != null) {
                Object center = invokeNoArg(bounds, "center");
                Vec3 vec = vectorToVec3(center);
                if (vec != null) {
                    return vec;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Object pose = invokeNoArg(subLevel, "logicalPose");
            Object position = pose == null ? null : invokeNoArg(pose, "position");
            return vectorToVec3(position);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static Vec3 getVelocity(Level level, Vec3 position) {
        if (level == null || position == null || getVelocityAt == null || !isAvailable()) {
            return Vec3.ZERO;
        }

        try {
            Object velocity = getVelocityAt.invoke(companion, level, position);
            if (velocity instanceof Vec3 vec) {
                return vec.scale(SABLE_VELOCITY_TO_TICKS);
            }
        } catch (Throwable ignored) {
        }

        return Vec3.ZERO;
    }

    @Nullable
    private static AABB getSubLevelRawAabb(Object subLevel) {
        try {
            Object bounds = invokeNoArg(subLevel, "boundingBox");
            Object aabb = invokeNoArg(bounds, "toMojang");
            if (aabb instanceof AABB box) {
                return box;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object bounds = invokeNoArg(subLevel, "boundingBox");
            double minX = ((Number) invokeNoArg(bounds, "minX")).doubleValue();
            double minY = ((Number) invokeNoArg(bounds, "minY")).doubleValue();
            double minZ = ((Number) invokeNoArg(bounds, "minZ")).doubleValue();
            double maxX = ((Number) invokeNoArg(bounds, "maxX")).doubleValue();
            double maxY = ((Number) invokeNoArg(bounds, "maxY")).doubleValue();
            double maxZ = ((Number) invokeNoArg(bounds, "maxZ")).doubleValue();
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static AABB scanBoxAround(Vec3 center, double horizontalRange, double yRange) {
        return new AABB(
                center.x - horizontalRange, center.y - yRange, center.z - horizontalRange,
                center.x + horizontalRange, center.y + yRange, center.z + horizontalRange
        );
    }

    @Nullable
    private static UUID getTrackSubLevelUuid(@Nullable RadarTrack track) {
        if (!isSableTrack(track)) {
            return null;
        }

        String trackId = track.id();
        if (trackId == null) {
            return null;
        }

        if (trackId.startsWith(TRACK_ID_PREFIX)) {
            trackId = trackId.substring(TRACK_ID_PREFIX.length());
        }

        try {
            return UUID.fromString(trackId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object getSubLevel(Level level, UUID uuid) {
        Object container = getContainer(level);
        if (container == null) {
            return null;
        }

        try {
            if (getSubLevelByUuid != null) {
                Object subLevel = getSubLevelByUuid.invoke(container, uuid);
                if (subLevel != null) {
                    return subLevel;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            if (getAllSubLevels == null) {
                return null;
            }

            Object all = getAllSubLevels.invoke(container);
            if (!(all instanceof Iterable<?> subLevels)) {
                return null;
            }

            for (Object subLevel : subLevels) {
                UUID subLevelId = getSubLevelUuid(subLevel);
                if (uuid.equals(subLevelId)) {
                    return subLevel;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    private static Object getContainer(Level level) {
        if (level == null || (getContainer == null && getContainerForLevel == null)) {
            return null;
        }

        try {
            if (level instanceof ServerLevel && getContainer != null) {
                return getContainer.invoke(null, level);
            }
            if (getContainerForLevel != null) {
                return getContainerForLevel.invoke(null, level);
            }
        } catch (Throwable throwable) {
            LOGGER.debug("Failed to resolve Sable sub-level container for {}", level, throwable);
        }

        return null;
    }

    @Nullable
    private static String getContainingSubLevelIdFromBounds(ServerLevel level, Vec3 position) {
        try {
            Object container = getContainer(level);
            if (container == null) {
                return null;
            }

            if (getAllSubLevels == null) {
                return null;
            }

            Object all = getAllSubLevels.invoke(container);
            if (!(all instanceof Iterable<?> subLevels)) {
                return null;
            }

            for (Object subLevel : subLevels) {
                if (subLevel == null || isRemoved(subLevel)) {
                    continue;
                }

                UUID uuid = getSubLevelUuid(subLevel);
                if (uuid == null) {
                    continue;
                }

                AABB rawBox = getSubLevelRawAabb(subLevel);
                if (containsWithTolerance(rawBox, position)) {
                    return uuid.toString();
                }

                AABB projectedBox = projectAabbToWorld(level, rawBox);
                if (containsWithTolerance(projectedBox, position)) {
                    return uuid.toString();
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static boolean containsWithTolerance(@Nullable AABB box, Vec3 position) {
        return box != null && box.inflate(1.0).contains(position);
    }

    private static void addDistinct(List<AABB> boxes, AABB box) {
        if (box == null) {
            return;
        }

        for (AABB existing : boxes) {
            if (sameBox(existing, box)) {
                return;
            }
        }

        boxes.add(box);
    }

    private static boolean sameBox(AABB a, AABB b) {
        return Double.compare(a.minX, b.minX) == 0
                && Double.compare(a.minY, b.minY) == 0
                && Double.compare(a.minZ, b.minZ) == 0
                && Double.compare(a.maxX, b.maxX) == 0
                && Double.compare(a.maxY, b.maxY) == 0
                && Double.compare(a.maxZ, b.maxZ) == 0;
    }

    private static void addTrackingPlayer(ServerLevel level, List<ServerPlayer> players, Object value) {
        ServerPlayer player = null;
        if (value instanceof ServerPlayer serverPlayer) {
            player = serverPlayer;
        } else if (value instanceof UUID uuid) {
            player = level.getServer().getPlayerList().getPlayer(uuid);
        }

        if (player != null && !players.contains(player)) {
            players.add(player);
        }
    }

    private static double getSubLevelHeight(Object subLevel) {
        try {
            Object bounds = invokeNoArg(subLevel, "boundingBox");
            Object height = invokeNoArg(bounds, "height");
            if (height instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Throwable ignored) {
        }

        return 1.0;
    }

    private static boolean isRemoved(Object subLevel) {
        try {
            Object removed = invokeNoArg(subLevel, "isRemoved");
            return removed instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTrackableSubLevel(Object subLevel) {
        if (subLevel == null || isRemoved(subLevel)) {
            return false;
        }

        Boolean hasMass = hasPositiveMass(subLevel);
        return hasMass == null || hasMass;
    }

    @Nullable
    private static Boolean hasPositiveMass(Object subLevel) {
        Object massData = null;
        try {
            massData = invokeNoArg(subLevel, "getMassTracker");
        } catch (Throwable ignored) {
        }
        if (massData == null) {
            try {
                massData = invokeNoArg(subLevel, "getSelfMassTracker");
            } catch (Throwable ignored) {
            }
        }
        if (massData == null) {
            return null;
        }

        try {
            Object invalid = invokeNoArg(massData, "isInvalid");
            if (invalid instanceof Boolean b && b) {
                return false;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object mass = invokeNoArg(massData, "getMass");
            if (mass instanceof Number number) {
                return number.doubleValue() > 1.0E-6;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    private static UUID getSubLevelUuid(Object subLevel) {
        try {
            Object id = invokeNoArg(subLevel, "getUniqueId");
            return id instanceof UUID uuid ? uuid : null;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String getSubLevelName(Object subLevel) {
        String name = normalizeSubLevelName(readSubLevelName(subLevel));
        return name == null ? "sable:sub_level" : name;
    }

    @Nullable
    private static Object readSubLevelName(Object subLevel) {
        String[] methods = {"getName", "getDisplayName", "getCustomName", "displayName", "customName", "name"};
        for (String method : methods) {
            try {
                Object value = invokeNoArg(subLevel, method);
                if (normalizeSubLevelName(value) != null) {
                    return value;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    @Nullable
    private static String normalizeSubLevelName(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Optional<?> optional) {
            return optional.map(SableRadarCompat::normalizeSubLevelName).orElse(null);
        }

        String name = null;
        if (value instanceof Component component) {
            name = component.getString();
        } else if (value instanceof CharSequence sequence) {
            name = sequence.toString();
        }

        if (name == null) {
            return null;
        }

        name = name.trim();
        return name.isBlank() || "sable:sub_level".equals(name) ? null : name;
    }

    private static boolean isSableTrack(@Nullable RadarTrack track) {
        return track != null && (track.isSableSubLevel()
                || track.trackCategory() == TrackCategory.SABLE
                || (track.getId() != null && track.getId().startsWith(TRACK_ID_PREFIX)));
    }

    @Nullable
    private static Vec3 vectorToVec3(Object vector) {
        if (vector == null) {
            return null;
        }

        try {
            double x = ((Number) invokeNoArg(vector, "x")).doubleValue();
            double y = ((Number) invokeNoArg(vector, "y")).doubleValue();
            double z = ((Number) invokeNoArg(vector, "z")).doubleValue();
            return new Vec3(x, y, z);
        } catch (Throwable throwable) {
            return null;
        }
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static void initialize() {
        initialized = true;

        if (!ModList.get().isLoaded("sable")) {
            return;
        }

        try {
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");

            Field instance = companionClass.getField("INSTANCE");
            companion = instance.get(null);

            projectOutOfSubLevel = findMethod(companionClass, "projectOutOfSubLevel",
                    new Class<?>[]{Level.class, Position.class},
                    new Class<?>[]{Level.class, Vec3.class});
            getContaining = findMethod(companionClass, "getContaining",
                    new Class<?>[]{Level.class, Position.class});
            getVelocityAt = findMethod(companionClass, "getVelocity",
                    new Class<?>[]{Level.class, Position.class},
                    new Class<?>[]{Level.class, Vec3.class});

            if (companion == null || projectOutOfSubLevel == null || getContaining == null) {
                throw new NoSuchMethodException("Required Sable Companion methods are missing");
            }

            initializeCompanionQueryMethods(companionClass);
            initializeContainerMethods();

            available = true;
            LOGGER.info("Sable radar compatibility enabled via {}", companion.getClass().getName());
            if (getContainer == null && getContainerForLevel == null) {
                LOGGER.debug("Sable SubLevelContainer methods are unavailable; radar will rely on Companion projections and entity queries");
            }
        } catch (Throwable throwable) {
            available = false;
            warnOnce("Sable is loaded, but radar compatibility could not initialize", throwable);
        }
    }

    private static void initializeCompanionQueryMethods(Class<?> companionClass) {
        try {
            Class<?> boundingBox3dcClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");
            Class<?> boundingBox3dClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d");
            Class<?> subLevelAccessClass = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");

            getAllIntersecting = findMethod(companionClass, "getAllIntersecting",
                    new Class<?>[]{Level.class, boundingBox3dcClass});
            getVelocityInSubLevel = findMethod(companionClass, "getVelocity",
                    new Class<?>[]{Level.class, subLevelAccessClass, Position.class},
                    new Class<?>[]{Level.class, subLevelAccessClass, Vec3.class});
            boundingBox3dFromAabb = boundingBox3dClass.getConstructor(AABB.class);
        } catch (Throwable throwable) {
            LOGGER.debug("Sable Companion query helpers are unavailable", throwable);
        }
    }

    private static void initializeContainerMethods() {
        try {
            subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            getContainer = findMethod(subLevelContainerClass, "getContainer", new Class<?>[]{ServerLevel.class});
            getContainerForLevel = findMethod(subLevelContainerClass, "getContainer", new Class<?>[]{Level.class});
            getAllSubLevels = findMethod(subLevelContainerClass, "getAllSubLevels", new Class<?>[]{});
            getSubLevelByUuid = findMethod(subLevelContainerClass, "getSubLevel", new Class<?>[]{UUID.class});
        } catch (Throwable throwable) {
            LOGGER.debug("Sable SubLevelContainer helpers are unavailable", throwable);
        }
    }

    @Nullable
    private static Method findMethod(Class<?> owner, String name, Class<?>[]... signatures) {
        for (Class<?>[] signature : signatures) {
            try {
                return owner.getMethod(name, signature);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static void warnOnce(String message, Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        LOGGER.warn(message, throwable);
    }
}
