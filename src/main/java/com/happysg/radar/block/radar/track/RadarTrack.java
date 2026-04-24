package com.happysg.radar.block.radar.track;

import com.happysg.radar.block.monitor.MonitorSprite;
import com.happysg.radar.config.RadarConfig;
import net.createmod.catnip.theme.Color;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class RadarTrack {
    public static final String SOURCE_ENTITY = "entity";
    public static final String SOURCE_SABLE = "sable";

    private final String id;
    private Vec3 position;
    private Vec3 velocity;
    private long scannedTime;
    private final TrackCategory trackCategory;
    private final String entityType;
    private final float entityheight;
    private final String source;
    private final boolean weaponTargetable;

    public RadarTrack(String id, Vec3 position, Vec3 velocity, long scannedTime, TrackCategory trackCategory, String entityType, float entityheight) {
        this(id, position, velocity, scannedTime, trackCategory, entityType, entityheight, SOURCE_ENTITY, true);
    }

    public RadarTrack(String id, Vec3 position, Vec3 velocity, long scannedTime, TrackCategory trackCategory, String entityType, float entityheight,
                      String source, boolean weaponTargetable) {
        this.id = id;
        this.position = position;
        this.velocity = velocity;
        this.scannedTime = scannedTime;
        this.trackCategory = trackCategory;
        this.entityType = entityType;
        this.entityheight = entityheight;
        this.source = source == null || source.isBlank() ? SOURCE_ENTITY : source;
        this.weaponTargetable = weaponTargetable;
    }

    public RadarTrack(Entity entity) {
        this(entity.getUUID().toString(), entity.position(), entity.getDeltaMovement(), entity.level().getGameTime(),
                TrackCategory.get(entity), entity.getType().toString(), entity.getBbHeight());
    }

    public static RadarTrack sableSubLevel(String id, Vec3 position, Vec3 velocity, long scannedTime, String displayName, float height) {
        String entityType = displayName == null || displayName.isBlank() ? "sable:sub_level" : displayName;
        return new RadarTrack(id, position, velocity, scannedTime, TrackCategory.SABLE, entityType, height, SOURCE_SABLE, false);
    }

    public Color getColor() {
        return switch (trackCategory) {
            case CONTRAPTION -> new Color(RadarConfig.client().contraptionColor.get());
            case SABLE -> new Color(RadarConfig.client().sableColor.get());
            case PLAYER -> new Color(RadarConfig.client().playerColor.get());
            case ANIMAL -> new Color(RadarConfig.client().friendlyColor.get());
            case HOSTILE -> new Color(RadarConfig.client().hostileColor.get());
            case PROJECTILE -> new Color(RadarConfig.client().projectileColor.get());
            case ITEM-> new Color(RadarConfig.client().itemcolor.get());
            default -> Color.WHITE;
        };
    }

    public MonitorSprite getSprite() {
        return switch (trackCategory) {
            case CONTRAPTION, SABLE -> MonitorSprite.CONTRAPTION_HITBOX;
            case PLAYER -> MonitorSprite.PLAYER;
            case PROJECTILE -> MonitorSprite.PROJECTILE;
            default -> MonitorSprite.ENTITY_HITBOX;
        };
    }


    public static RadarTrack deserializeNBT(CompoundTag tag) {
        return new RadarTrack(tag.getString("id"),
                new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
                new Vec3(tag.getDouble("vx"), tag.getDouble("vy"), tag.getDouble("vz")),
                tag.getLong("scannedTime"),
                categoryFromOrdinal(tag.getInt("Category")),
                tag.getString("entityType"),
                tag.getFloat("eh"),
                tag.contains("source") ? tag.getString("source") : SOURCE_ENTITY,
                !tag.contains("weaponTargetable") || tag.getBoolean("weaponTargetable")
        );
    }


    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putDouble("x", position.x);
        tag.putDouble("y", position.y);
        tag.putDouble("z", position.z);
        tag.putDouble("vx", velocity.x);
        tag.putDouble("vy", velocity.y);
        tag.putDouble("vz", velocity.z);
        tag.putLong("scannedTime", scannedTime);
        tag.putInt("Category", trackCategory.ordinal());
        tag.putString("entityType", entityType);
        tag.putFloat("eh", entityheight );
        tag.putString("source", source);
        tag.putBoolean("weaponTargetable", weaponTargetable);

        return tag;
    }

    private static TrackCategory categoryFromOrdinal(int ordinal) {
        TrackCategory[] values = TrackCategory.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return TrackCategory.MISC;
        }
        return values[ordinal];
    }

    public void updateRadarTrack(Entity entity) {
        position = entity.position();
        velocity = entity.getDeltaMovement();
        scannedTime = entity.level().getGameTime();
    }

    public String getId() {
        return id;
    }

    public Vec3 getPosition() {
        return position;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
    }

    public Vec3 getVelocity() {
        return velocity;
    }

    public void setVelocity(Vec3 velocity) {
        this.velocity = velocity;
    }

    public long getScannedTime() {
        return scannedTime;
    }

    public void setScannedTime(long scannedTime) {
        this.scannedTime = scannedTime;
    }

    public float getEnityHeight(){return entityheight;}

    public TrackCategory getTrackCategory() {
        return trackCategory;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getSource() {
        return source;
    }

    public boolean isWeaponTargetable() {
        return weaponTargetable;
    }

    public boolean isSableSubLevel() {
        return SOURCE_SABLE.equals(source);
    }



    // This is a bit of a jank quick fix, since ive migrated from a record.
    public String id() {
        return getId();
    }
    public Vec3 position() {
        return getPosition();
    }
    public Vec3 velocity() {
        return getVelocity();
    }
    public long scannedTime() {
        return getScannedTime();
    }
    public TrackCategory trackCategory() {
        return getTrackCategory();
    }
    public String entityType() {
        return getEntityType();
    }
    public String source() {
        return getSource();
    }
    public boolean weaponTargetable() {
        return isWeaponTargetable();
    }
}
