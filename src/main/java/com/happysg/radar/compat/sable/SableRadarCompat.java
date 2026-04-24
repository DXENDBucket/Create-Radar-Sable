package com.happysg.radar.compat.sable;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
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

    @Nullable
    public static String getContainingSubLevelId(Level level, Vec3 position) {
        if (level == null || position == null || !isAvailable()) {
            return null;
        }

        try {
            Object subLevel = getContaining.invoke(companion, level, position);
            UUID id = getSubLevelUuid(subLevel);
            return id == null ? null : id.toString();
        } catch (Throwable throwable) {
            warnOnce("Failed to resolve containing Sable sub-level", throwable);
            return null;
        }
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

    private static Vec3 getVelocity(Level level, Vec3 position) {
        try {
            Object velocity = getVelocityAt.invoke(companion, level, position);
            if (velocity instanceof Vec3 vec) {
                return vec;
            }
        } catch (Throwable ignored) {
        }

        return Vec3.ZERO;
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
