package com.happysg.radar.block.behavior.networks.config;

import com.happysg.radar.block.radar.track.RadarTrack;
import com.happysg.radar.block.radar.track.TrackCategory;
import com.happysg.radar.config.RadarConfig;
import net.createmod.catnip.theme.Color;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public record DetectionConfig(boolean player, boolean contraption, boolean mob, boolean projectile, boolean animal, boolean item,
                              List<String> blacklistPlayers, List<String> whitelistPlayers) {

    public static final DetectionConfig DEFAULT = new DetectionConfig(true, true, true, true, true, true);

    public DetectionConfig(boolean player, boolean contraption, boolean mob, boolean projectile, boolean animal, boolean item) {
        this(player, contraption, mob, projectile, animal, item, List.of(), List.of());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("player", player);
        tag.putBoolean("contraption", contraption);
        tag.putBoolean("mob", mob);
        tag.putBoolean("projectile", projectile);
        tag.putBoolean("animal", animal);
        tag.putBoolean("item", item);

        CompoundTag playersListTag = new CompoundTag();
        blacklistPlayers.forEach(player -> playersListTag.putBoolean(player, false));
        whitelistPlayers.forEach(player -> playersListTag.putBoolean(player, true));
        tag.put("playerList", playersListTag);

        return tag;
    }

    public static DetectionConfig fromTag(CompoundTag tag) {
        boolean player = tag.getBoolean("player");
        boolean contraption = tag.getBoolean("contraption");
        boolean mob = tag.getBoolean("mob");
        boolean projectile = tag.getBoolean("projectile");
        boolean animal = tag.getBoolean("animal");
        boolean item = tag.getBoolean("item");
        List<String> blacklistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> !tag.getCompound("playerList").getBoolean(key)).toList();
        List<String> whitelistPlayers = tag.getCompound("playerList").getAllKeys().stream().filter(key -> tag.getCompound("playerList").getBoolean(key)).toList();
        return new DetectionConfig(player, contraption, mob, projectile, animal, item, blacklistPlayers, whitelistPlayers);
    }

    public boolean test(RadarTrack track) {
        return test(track.trackCategory());
    }

    public Color getColor(RadarTrack track) {
        if (track.trackCategory() == TrackCategory.PLAYER) {
            if (blacklistPlayers.contains(track.id())) {
                return new Color(RadarConfig.client().hostileColor.get());
            }
            if (whitelistPlayers.contains(track.id())) {
                return new Color(RadarConfig.client().friendlyColor.get());
            }
        }
        return track.getColor();
    }

    private boolean test(TrackCategory trackCategory) {
        if (trackCategory == TrackCategory.PLAYER) {
            return player;
        } else if (trackCategory == TrackCategory.CONTRAPTION || trackCategory == TrackCategory.SABLE) {
            return contraption;
        } else if (trackCategory == TrackCategory.MOB || trackCategory == TrackCategory.HOSTILE) {
            return mob;
        } else if (trackCategory == TrackCategory.PROJECTILE) {
            return projectile;
        } else if (trackCategory == TrackCategory.ANIMAL) {
            return animal;
        } else if (trackCategory == TrackCategory.ITEM) {
            return item;
        }
        return false;
    }
}
