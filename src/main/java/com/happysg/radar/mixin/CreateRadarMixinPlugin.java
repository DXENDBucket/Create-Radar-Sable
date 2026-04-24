package com.happysg.radar.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreateRadarMixinPlugin implements IMixinConfigPlugin {
    private static final Map<String, String> OPTIONAL_MIXINS = Map.of(
            "com.happysg.radar.mixin.CBATAutoCannonAccessor", "cbc_at",
            "com.happysg.radar.mixin.ShupapiumACContraptionAccessor", "shupapium"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String requiredMod = OPTIONAL_MIXINS.get(mixinClassName);
        return requiredMod == null || isModLoaded(requiredMod);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isModLoaded(String modId) {
        try {
            LoadingModList mods = LoadingModList.get();
            return mods != null && mods.getModFileById(modId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
