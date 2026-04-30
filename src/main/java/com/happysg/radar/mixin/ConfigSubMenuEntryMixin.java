package com.happysg.radar.mixin;

import net.createmod.catnip.config.ui.ConfigScreen;
import net.createmod.catnip.config.ui.ConfigScreenList;
import net.createmod.catnip.config.ui.entries.SubMenuEntry;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubMenuEntry.class, remap = false)
public abstract class ConfigSubMenuEntryMixin extends ConfigScreenList.LabeledEntry {
    @Unique
    private String createRadar$localizedPath;

    public ConfigSubMenuEntryMixin(String label) {
        super(label);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void createRadar$localizeConfigText(GuiGraphics graphics, int index, int y, int x, int width,
                                                int height, int mouseX, int mouseY, boolean hovering,
                                                float partialTicks, CallbackInfo ci) {
        if (this.path == null || this.path.equals(createRadar$localizedPath)) {
            return;
        }

        String translationKey = ConfigScreen.modID + ".configuration." + this.path;
        if (!I18n.exists(translationKey)) {
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

        this.labelTooltip.add(Component.literal(ConfigScreen.modID + ":" + this.path)
                .withStyle(ChatFormatting.DARK_GRAY));
        createRadar$localizedPath = this.path;
    }
}
