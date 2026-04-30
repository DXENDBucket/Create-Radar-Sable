package com.happysg.radar.mixin;

import net.createmod.catnip.config.ui.ConfigAnnotations;
import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.entries.ValueEntry;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ValueEntry.class, remap = false)
public abstract class ConfigValueEntryMixin extends ConfigScreenList.LabeledEntry {
    public ConfigValueEntryMixin(String label) {
        super(label);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void createRadar$localizeConfigText(String label, ModConfigSpec.ConfigValue<?> value,
                                                ModConfigSpec.ValueSpec spec, CallbackInfo ci) {
        String translationKey = spec.getTranslationKey();
        if (translationKey == null || !I18n.exists(translationKey)) {
            return;
        }

        this.label.withText(Component.translatable(translationKey).getString());
        this.labelTooltip.clear();
        this.labelTooltip.add(Component.translatable(translationKey).withStyle(ChatFormatting.WHITE));

        String tooltipKey = translationKey + ".tooltip";
        if (I18n.exists(tooltipKey)) {
            this.labelTooltip.addAll(FontHelper.cutTextComponent(
                    Component.translatable(tooltipKey),
                    FontHelper.Palette.ALL_GRAY
            ));
        }

        if (this.annotations.containsKey(ConfigAnnotations.RequiresRelog.TRUE.getName())) {
            this.labelTooltip.addAll(FontHelper.cutTextComponent(
                    Component.translatable("catnip.ui.value_entry.relog_required"),
                    FontHelper.Palette.GRAY_AND_GOLD
            ));
        }
        if (this.annotations.containsKey(ConfigAnnotations.RequiresRestart.CLIENT.getName())) {
            this.labelTooltip.addAll(FontHelper.cutTextComponent(
                    Component.translatable("catnip.ui.value_entry.restart_required"),
                    FontHelper.Palette.GRAY_AND_RED
            ));
        }

        List<String> path = value.getPath();
        String configName = path.isEmpty() ? label : path.get(path.size() - 1);
        this.labelTooltip.add(Component.literal(ConfigScreen.modID + ":" + configName)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
