package com.happysg.radar.block.radar.behavior;

import com.happysg.radar.block.radar.bearing.RadarBearingBlockEntity;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.compat.sable.SableRadarCompat;
import com.happysg.radar.config.RadarConfig;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.happysg.radar.block.behavior.networks.config.DetectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class RadarScanningBlockBehavior extends BlockEntityBehaviour {

    public static final BehaviourType<RadarScanningBlockBehavior> TYPE = new BehaviourType<>();

    private int trackExpiration = 100;
    private int fov = RadarConfig.server().radarFOV.get();
    private int yRange = 20;
    private double range = RadarConfig.server().radarBaseRange.get();
    private double angle;
    private boolean running = false;
    private SmartBlockEntity bearingEntity;
    private RadarBearingBlockEntity radarBearing;
    Vec3 localScanPos = Vec3.ZERO;
    Vec3 scanPos = Vec3.ZERO;

    private final Set<Entity> scannedEntities = new HashSet<>();
    private final Set<Projectile> scannedProjectiles = new HashSet<>();
    private final HashMap<String, RadarTrack> radarTracks = new HashMap<>();

    public RadarScanningBlockBehavior(SmartBlockEntity be) {
        super(be);
        this.bearingEntity = be;
        if (be instanceof RadarBearingBlockEntity radarBearing) {
            this.radarBearing = radarBearing;
        }
    }

    public void applyDetectionConfig(DetectionConfig cfg) {
        if (cfg == null) cfg = DetectionConfig.DEFAULT;
        setScanFlags(
                cfg.player(),
                cfg.contraption(),
                cfg.mob(),
                cfg.animal(),
                cfg.projectile(),
                cfg.item()
        );
    }


    private boolean scanPlayers = true;
    private boolean scanContraptions = true;
    private boolean scanMobs = true;
    private boolean scanAnimals = true;
    private boolean scanProjectiles = true;
    private boolean scanItems = true;

    private boolean allowCategory(TrackCategory c) {
        return switch (c) {
            case PLAYER -> scanPlayers;
            case CONTRAPTION, SABLE -> scanContraptions;
            case PROJECTILE -> scanProjectiles;
            case ITEM -> scanItems;

            case ANIMAL -> scanAnimals;
            case HOSTILE, MOB -> scanMobs;

            default -> true;
        };
    }

    private void pruneDisabledTracksNow() {
        radarTracks.entrySet().removeIf(e -> !allowCategory(e.getValue().trackCategory()));
    }

    public void setScanFlags(boolean players, boolean contraptions, boolean mobs, boolean animals, boolean projectiles, boolean items) {
        boolean changed = players != scanPlayers || contraptions != scanContraptions || mobs != scanMobs || animals != scanAnimals || projectiles != scanProjectiles || items != scanItems;

        this.scanPlayers = players;
        this.scanContraptions = contraptions;
        this.scanMobs = mobs;
        this.scanAnimals = animals;
        this.scanProjectiles = projectiles;
        this.scanItems = items;

        if (changed) {
            pruneDisabledTracksNow();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide)
            return;
        if(blockEntity.getLevel().getGameTime() %5!=1)return;
        removeDeadTracks();
        if (running) {
            updateScanPos();
            pruneOwnRadarContraptionTrack();
            updateRadarTracks();
        }
        if (running) {
            scannedEntities.clear();
            scannedProjectiles.clear();

            scanForEntityTracks();
            scanForSableTracks();
        }
    }

    private void updateScanPos() {
        Level level = blockEntity.getLevel();
        localScanPos = bearingEntity.getBlockPos().getCenter();
        scanPos = level == null ? localScanPos : SableRadarCompat.projectToWorld(level, localScanPos);
    }

    private void updateRadarTracks() {
        Level level = blockEntity.getLevel();
        if (level == null )return;


        for (Entity entity : scannedEntities) {
            if (isOwnRadarContraption(entity)) {
                radarTracks.remove(entity.getUUID().toString());
                continue;
            }
            if (entity.isAlive() && isInFovAndRange(entity.position())) {
                radarTracks.compute(entity.getUUID().toString(), (id, track) -> {
                    if (track == null) return new RadarTrack(entity);
                    track.updateRadarTrack(entity);
                    return track;
                });

                if (entity instanceof Projectile)
                    scannedProjectiles.add((Projectile) entity);
            }
        }
        pruneOwnRadarContraptionTrack();
    }

    private void scanForSableTracks() {
        if (!scanContraptions) {
            return;
        }

        Level level = blockEntity.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        String ownSubLevelId = null;
        if (RadarConfig.server().preventSableSelfTargeting.get()) {
            ownSubLevelId = SableRadarCompat.getContainingSubLevelId(level, localScanPos);
            if (ownSubLevelId == null && scanPos != null) {
                ownSubLevelId = SableRadarCompat.getContainingSubLevelId(level, scanPos);
            }
            if (ownSubLevelId != null) {
                String finalOwnSubLevelId = ownSubLevelId;
                radarTracks.entrySet().removeIf(entry -> SableRadarCompat.isTrackForSubLevel(entry.getValue(), finalOwnSubLevelId));
            }
            radarTracks.entrySet().removeIf(entry -> isOwnSableTrack(serverLevel, entry.getValue()));
        }

        List<RadarTrack> sableTracks = SableRadarCompat.collectSubLevelTracks(
                serverLevel,
                getRadarAABB(),
                ownSubLevelId,
                this::isInFovAndRange,
                level.getGameTime()
        );

        for (RadarTrack track : sableTracks) {
            if (RadarConfig.server().preventSableSelfTargeting.get() && isOwnSableTrack(serverLevel, track)) {
                continue;
            }
            radarTracks.put(track.id(), track);
        }
    }

    private boolean isOwnSableTrack(ServerLevel level, RadarTrack track) {
        if (track == null || !track.isSableSubLevel()) {
            return false;
        }
        if (SableRadarCompat.isTrackAtPosition(level, track, localScanPos)) {
            return true;
        }
        return scanPos != null && SableRadarCompat.isTrackAtPosition(level, track, scanPos);
    }

    private boolean isInFovAndRange(Vec3 target) {
        Vec3 worldTarget = projectToWorld(target);
        double horizontalDistance = Math.sqrt(Math.pow(worldTarget.x() - scanPos.x(), 2) + Math.pow(worldTarget.z() - scanPos.z(), 2));
        double verticalDistance = Math.abs(worldTarget.y() - scanPos.y());
        double yScanRange = RadarConfig.server().radarYScanRange.get();

        if (horizontalDistance > range || verticalDistance > yScanRange)
            return false;

        if (horizontalDistance < 2)
            return true;

        double angleToEntity = Math.toDegrees(Math.atan2(worldTarget.x() - scanPos.x(), worldTarget.z() - scanPos.z()));
        angleToEntity = (angleToEntity + 360) % 360;
        double angleDiff = Math.abs(angleToEntity - angle);
        if (angleDiff > 180) angleDiff = 360 - angleDiff;

        return angleDiff <= fov / 2.0;
    }

    private void removeDeadTracks() {
        pruneOwnRadarContraptionTrack();

        // entities
        for (Entity entity : scannedEntities) {
            if (!entity.isAlive())
                radarTracks.remove(entity.getUUID().toString());
        }

        // ttl expiration (works for everything: entities, ships, projectiles)
        List<String> toRemove = new ArrayList<>();
        assert blockEntity.getLevel() != null;
        long currentTime = blockEntity.getLevel().getGameTime();
        for (RadarTrack track : radarTracks.values()) {
            if (track.isSableSubLevel()
                    && blockEntity.getLevel() instanceof ServerLevel serverLevel
                    && !SableRadarCompat.isTrackValid(serverLevel, track)) {
                toRemove.add(track.id());
                continue;
            }
            if (currentTime - track.scannedTime() > trackExpiration)
                toRemove.add(track.id());
        }
        toRemove.forEach(radarTracks::remove);

        // projectiles
        scannedProjectiles.removeIf(p -> {
            boolean dead = !p.isAlive();
            if (dead) radarTracks.remove(p.getUUID().toString());
            return dead;
        });
    }
    private void scanForEntityTracks() {
        Level level = blockEntity.getLevel();
        if (level == null) return;

        boolean scanAll =
                scanPlayers && scanContraptions && scanMobs && scanAnimals && scanProjectiles && scanItems;

        List<AABB> sourceBoxes = List.of(getRadarAABB());
        double yScan = RadarConfig.server().radarYScanRange.get();
        if (level instanceof ServerLevel serverLevel) {
            sourceBoxes = SableRadarCompat.collectEntityCandidateBoxes(serverLevel, getRadarAABB(), localScanPos, range, yScan);
        }

        for (AABB sourceBox : sourceBoxes) {
            for (AABB aabb : splitAABB(sourceBox, 256)) {
                if (scanAll) {
                    scannedEntities.addAll(level.getEntities(null, aabb));
                    continue;
                }

                if (scanPlayers)
                    scannedEntities.addAll(level.getEntitiesOfClass(Player.class, aabb));

                if (scanProjectiles)
                    scannedEntities.addAll(level.getEntitiesOfClass(Projectile.class, aabb));

                if (scanItems)
                    scannedEntities.addAll(level.getEntitiesOfClass(ItemEntity.class, aabb));

                if (scanContraptions)
                    scannedEntities.addAll(level.getEntitiesOfClass(AbstractContraptionEntity.class, aabb));

                if (scanAnimals)
                    scannedEntities.addAll(level.getEntitiesOfClass(Animal.class, aabb));

                if (scanMobs) {
                    scannedEntities.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, aabb,
                            e -> !(e instanceof Animal)));
                }
            }
        }
        pruneOwnRadarContraptionTrack();
    }

    private void pruneOwnRadarContraptionTrack() {
        ControlledContraptionEntity ownContraption = getOwnRadarContraptionEntity();
        if (ownContraption == null) {
            return;
        }

        scannedEntities.removeIf(this::isOwnRadarContraption);
        radarTracks.remove(ownContraption.getUUID().toString());
    }

    private boolean isOwnRadarContraption(Entity entity) {
        ControlledContraptionEntity ownContraption = getOwnRadarContraptionEntity();
        return ownContraption != null && entity.getUUID().equals(ownContraption.getUUID());
    }

    private ControlledContraptionEntity getOwnRadarContraptionEntity() {
        if (radarBearing == null) {
            return null;
        }
        return radarBearing.getMovedContraption();
    }

    private AABB getRadarAABB() {
        Vec3 radarPos = scanPos == null ? blockEntity.getBlockPos().getCenter() : scanPos;
        double x = radarPos.x;
        double y = radarPos.y;
        double z = radarPos.z;

        double yScan = RadarConfig.server().radarYScanRange.get();

        return new AABB(
                x - range, y - yScan, z - range,
                x + range, y + yScan, z + range
        );
    }

    private Vec3 projectToWorld(Vec3 position) {
        Level level = blockEntity.getLevel();
        return level == null ? position : SableRadarCompat.projectToWorld(level, position);
    }

    public static List<AABB> splitAABB(AABB aabb, double maxSize) {
        List<AABB> result = new ArrayList<>();
        for (double x = aabb.minX; x < aabb.maxX; x += maxSize) {
            for (double y = aabb.minY; y < aabb.maxY; y += maxSize) {
                for (double z = aabb.minZ; z < aabb.maxZ; z += maxSize) {
                    result.add(new AABB(
                            x, y, z,
                            Math.min(x + maxSize, aabb.maxX),
                            Math.min(y + maxSize, aabb.maxY),
                            Math.min(z + maxSize, aabb.maxZ)
                    ));
                }
            }
        }
        return result;
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        if (nbt.contains("fov")) fov = nbt.getInt("fov");
        if (nbt.contains("yRange")) yRange = nbt.getInt("yRange");
        if (nbt.contains("range")) range = nbt.getDouble("range");
        if (nbt.contains("angle")) angle = nbt.getDouble("angle");
        if (nbt.contains("scanPosX")) scanPos = new Vec3(nbt.getDouble("scanPosX"), nbt.getDouble("scanPosY"), nbt.getDouble("scanPosZ"));
        if (nbt.contains("running")) running = nbt.getBoolean("running");
        if (nbt.contains("trackExpiration")) trackExpiration = nbt.getInt("trackExpiration");
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        nbt.putInt("fov", fov);
        nbt.putInt("yRange", yRange);
        nbt.putDouble("range", range);
        nbt.putDouble("angle", angle);
        nbt.putDouble("scanPosX", scanPos.x);
        nbt.putDouble("scanPosY", scanPos.y);
        nbt.putDouble("scanPosZ", scanPos.z);
        nbt.putBoolean("running", running);
        nbt.putInt("trackExpiration", trackExpiration);
    }

    public void setFov(int fov) { this.fov = fov; }
    public void setYRange(int yRange) { this.yRange = yRange; }
    public void setRange(double range) { this.range = range; }
    public void setAngle(double angle) { this.angle = angle; }
    public void setScanPos(Vec3 scanPos) { this.scanPos = scanPos; }
    public void setRunning(boolean running) { this.running = running; }
    public void setTrackExpiration(int trackExpiration) { this.trackExpiration = trackExpiration; }

    public Collection<RadarTrack> getRadarTracks() {
        return radarTracks.values();
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public float getAngle() {
        return (float) angle;
    }
}
