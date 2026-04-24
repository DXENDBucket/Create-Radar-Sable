package com.happysg.radar.compat.sable;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class SableRadarCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TRACK_ID_PREFIX = "sable:";

    private static boolean initialized;
    private static boolean available;
    private static boolean warned;

    private static Class<?> subLevelContainerClass;
    private static Object companion;

    private static Method getContainer;
    private static Method getAllSubLevels;
    private static Method projectOutOfSubLevel;
    private static Method getContaining;
    private static Method getVelocityAt;

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

    public static List<RadarTrack> collectSubLevelTracks(ServerLevel level, AABB scanBox, @Nullable String ignoredSubLevelId,
                                                         Predicate<Vec3> positionFilter, long scannedTime) {
        if (level == null || scanBox == null || !isAvailable()) {
            return List.of();
        }

        List<RadarTrack> tracks = new ArrayList<>();

        try {
            Object container = getContainer.invoke(null, level);
            if (container == null) {
                return tracks;
            }

            Object all = getAllSubLevels.invoke(container);
            if (!(all instanceof Iterable<?> subLevels)) {
                return tracks;
            }

            for (Object subLevel : subLevels) {
                RadarTrack track = createTrack(level, subLevel, scanBox, ignoredSubLevelId, positionFilter, scannedTime);
                if (track != null) {
                    tracks.add(track);
                }
            }
        } catch (Throwable throwable) {
            warnOnce("Failed to collect Sable radar tracks", throwable);
        }

        return tracks;
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
            Object container = getContainer.invoke(null, level);
            if (container == null) {
                return boxes;
            }

            Object all = getAllSubLevels.invoke(container);
            if (!(all instanceof Iterable<?> subLevels)) {
                return boxes;
            }

            for (Object subLevel : subLevels) {
                if (subLevel == null || isRemoved(subLevel)) {
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
    private static RadarTrack createTrack(ServerLevel level, Object subLevel, AABB scanBox, @Nullable String ignoredSubLevelId,
                                          Predicate<Vec3> positionFilter, long scannedTime) {
        if (subLevel == null || isRemoved(subLevel)) {
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

        Vec3 position = getSubLevelCenter(level, subLevel);
        if (position == null || !scanBox.contains(position) || !positionFilter.test(position)) {
            return null;
        }

        Vec3 velocity = getVelocity(level, position);
        float height = (float) Math.max(1.0, getSubLevelHeight(subLevel));
        String name = getSubLevelName(subLevel);

        return RadarTrack.sableSubLevel(TRACK_ID_PREFIX + rawId, position, velocity, scannedTime, name, height);
    }

    @Nullable
    private static Vec3 getSubLevelCenter(ServerLevel level, Object subLevel) {
        try {
            Object bounds = invokeNoArg(subLevel, "boundingBox");
            if (bounds != null) {
                Object center = invokeNoArg(bounds, "center");
                Vec3 vec = vectorToVec3(center);
                if (vec != null) {
                    return projectToWorld(level, vec);
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Object pose = invokeNoArg(subLevel, "logicalPose");
            Object position = pose == null ? null : invokeNoArg(pose, "position");
            Vec3 vec = vectorToVec3(position);
            return vec == null ? null : projectToWorld(level, vec);
        } catch (Throwable throwable) {
            return null;
        }
    }

    public static Vec3 getVelocity(Level level, Vec3 position) {
        try {
            Object velocity = getVelocityAt.invoke(companion, level, position);
            if (velocity instanceof Vec3 vec) {
                return vec;
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
        if (track == null || !track.isSableSubLevel()) {
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
    private static Object getSubLevel(ServerLevel level, UUID uuid) {
        try {
            Object container = getContainer.invoke(null, level);
            if (container == null) {
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
    private static String getContainingSubLevelIdFromBounds(ServerLevel level, Vec3 position) {
        try {
            Object container = getContainer.invoke(null, level);
            if (container == null) {
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
        try {
            Object name = invokeNoArg(subLevel, "getName");
            if (name instanceof String value && !value.isBlank()) {
                return value;
            }
        } catch (Throwable ignored) {
        }

        return "sable:sub_level";
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
            subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
            Class<?> companionClass = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");

            Field instance = companionClass.getField("INSTANCE");
            companion = instance.get(null);

            getContainer = subLevelContainerClass.getMethod("getContainer", ServerLevel.class);
            getAllSubLevels = subLevelContainerClass.getMethod("getAllSubLevels");
            projectOutOfSubLevel = companionClass.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);
            getContaining = companionClass.getMethod("getContaining", Level.class, Position.class);
            getVelocityAt = companionClass.getMethod("getVelocity", Level.class, Vec3.class);

            available = companion != null;
            if (available) {
                LOGGER.info("Sable radar compatibility enabled");
            }
        } catch (Throwable throwable) {
            available = false;
            warnOnce("Sable is loaded, but radar compatibility could not initialize", throwable);
        }
    }

    private static void warnOnce(String message, Throwable throwable) {
        if (warned) {
            return;
        }
        warned = true;
        LOGGER.warn(message, throwable);
    }
}
