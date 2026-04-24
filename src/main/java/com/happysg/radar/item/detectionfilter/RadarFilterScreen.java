package com.happysg.radar.item.detectionfilter;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.NetworkHandler;
import com.happysg.radar.networking.networkhandlers.BoolNBThelper;
import com.happysg.radar.networking.packets.BoolListPacket;
import com.happysg.radar.registry.ModGuiTextures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public class RadarFilterScreen extends AbstractSimiScreen {

    private static final String KEY = "detectBools";
    private static final int COUNT = 6;
    private static final Component PLAYER_TOOLTIP = Component.translatable(CreateRadar.MODID + ".radar_button.player");
    private static final Component CONTRAPTION_TOOLTIP = Component.translatable(CreateRadar.MODID + ".radar_button.contraption");
    private static final Component MOB_TOOLTIP = Component.translatable(CreateRadar.MODID + ".radar_button.mob");
    private static final Component ANIMAL_TOOLTIP = Component.translatable(CreateRadar.MODID + ".radar_button.animal");
    private static final Component PROJECTILE_TOOLTIP = Component.translatable(CreateRadar.MODID + ".radar_button.projectile");
    private static final Component ITEM_TOOLTIP = Component.translatable(CreateRadar.MODID + ".radar_button.item");

    boolean player;
    boolean contraption;
    boolean mob;
    boolean projectile;
    boolean animal;
    boolean item;

    protected IconButton playerButton;
    protected Indicator playerIndicator;
    protected IconButton contraptionButton;
    protected Indicator contraptionIndicator;
    protected IconButton mobButton;
    protected Indicator mobIndicator;
    protected IconButton projectileButton;
    protected Indicator projectileIndicator;
    protected IconButton animalButton;
    protected Indicator animalIndicator;
    protected IconButton itemButton;
    protected Indicator itemIndicator;
    protected ModGuiTextures background;
    protected IconButton confirmButton;
    public RadarFilterScreen() {
        this.background = ModGuiTextures.DETECTION_FILTER;

    }
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft;
        int y = guiTop;

        background.render(graphics, x, y);
        MutableComponent header = Component.translatable(CreateRadar.MODID + ".detection_filter.title");
        graphics.drawString(font, header, x + background.width / 2 - font.width(header) / 2, y + 4, 0, false);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(0, guiTop + 46, 0);
        ms.translate(0, 21, 0);
        ms.popPose();

        ms.pushPose();
        TransformStack.of(ms)
                .pushPose()
                .translate(x + background.width + 4, y + background.height + 4, 100)
                .scale(40)
                .rotateX(-22)
                .rotateY(63);
        ms.popPose();

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        Component tooltip = getHoveredTooltip(mouseX, mouseY);
        if (tooltip != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
            graphics.pose().popPose();
        }
    }

    private Component getHoveredTooltip(int mouseX, int mouseY) {
        if (isHovered(playerButton, mouseX, mouseY)) return PLAYER_TOOLTIP;
        if (isHovered(contraptionButton, mouseX, mouseY)) return CONTRAPTION_TOOLTIP;
        if (isHovered(mobButton, mouseX, mouseY)) return MOB_TOOLTIP;
        if (isHovered(animalButton, mouseX, mouseY)) return ANIMAL_TOOLTIP;
        if (isHovered(projectileButton, mouseX, mouseY)) return PROJECTILE_TOOLTIP;
        if (isHovered(itemButton, mouseX, mouseY)) return ITEM_TOOLTIP;
        return null;
    }

    private boolean isHovered(IconButton button, int mouseX, int mouseY) {
        return button != null && button.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void init() {
        setWindowSize(background.width, background.height);
        loadFlagsFromHeldItem();
        super.init();
        clearWidgets();
        int Y = guiLeft;
        int X = guiTop;
        playerButton = new IconButton(guiLeft + 32, guiTop + 38, ModGuiTextures.PLAYER_BUTTON);
        playerIndicator = new Indicator(guiLeft + 32, guiTop + 31, Component.empty());
        playerIndicator.state = player ? Indicator.State.GREEN : Indicator.State.RED;
        playerButton.withCallback((x, y) -> {
            player = !player;
            playerIndicator.state = player ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(playerButton);
        addRenderableWidget(playerIndicator);

        contraptionButton = new IconButton(guiLeft + 60, guiTop + 38, ModGuiTextures.CONTRAPTION_BUTTON);
        contraptionIndicator = new Indicator(guiLeft + 60, guiTop + 31, Component.empty());
        contraptionIndicator.state = contraption ? Indicator.State.GREEN : Indicator.State.RED;
        contraptionButton.withCallback((x, y) -> {
            contraption = !contraption;
            contraptionIndicator.state = contraption ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(contraptionButton);
        addRenderableWidget(contraptionIndicator);

        mobButton = new IconButton(guiLeft + 88, guiTop + 38, ModGuiTextures.MOB_BUTTON);
        mobIndicator = new Indicator(guiLeft + 88, guiTop + 31, Component.empty());
        mobIndicator.state = mob ? Indicator.State.GREEN : Indicator.State.RED;
        mobButton.withCallback((x, y) -> {
            mob = !mob;
            mobIndicator.state = mob ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(mobButton);
        addRenderableWidget(mobIndicator);

        animalButton = new IconButton(guiLeft + 116, guiTop + 38, ModGuiTextures.ANIMAL_BUTTON);
        animalIndicator = new Indicator(guiLeft + 116, guiTop + 31, Component.empty());
        animalIndicator.state = animal ? Indicator.State.GREEN : Indicator.State.RED;
        animalButton.withCallback((x, y) -> {
            animal = !animal;
            animalIndicator.state = animal ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(animalButton);
        addRenderableWidget(animalIndicator);

        projectileButton = new IconButton(guiLeft + 144, guiTop + 38, ModGuiTextures.PROJECTILE_BUTTON);
        projectileIndicator = new Indicator(guiLeft + 144, guiTop + 31, Component.empty());
        projectileIndicator.state = projectile ? Indicator.State.GREEN : Indicator.State.RED;
        projectileButton.withCallback((x, y) -> {
            projectile = !projectile;
            projectileIndicator.state = projectile ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(projectileButton);
        addRenderableWidget(projectileIndicator);

        itemButton = new IconButton(guiLeft + 172, guiTop + 38, ModGuiTextures.ITEM_BUTTON);
        itemIndicator = new Indicator(guiLeft + 172, guiTop + 31, Component.empty());
        itemIndicator.state = item ? Indicator.State.GREEN : Indicator.State.RED;
        itemButton.withCallback((x, y) -> {
            item = !item;
            itemIndicator.state = item ? Indicator.State.GREEN : Indicator.State.RED;
        });
        addRenderableWidget(itemButton);
        addRenderableWidget(itemIndicator);

        confirmButton = new IconButton(guiLeft+223,guiTop+72, AllIcons.I_CONFIRM);
        confirmButton.withCallback(this::onClose);
        addRenderableWidget(confirmButton);
    }

    private void loadFlagsFromHeldItem() {
        ItemStack stack = Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!stack.isEmpty()) {
            boolean[] arr = BoolNBThelper.loadBooleansFromBytes(stack, KEY, COUNT);
            if (arr.length >= COUNT) {
                player = arr[0];
                contraption = arr[1];
                mob = arr[2];
                animal = arr[3];
                projectile = arr[4];
                item = arr[5];
            }

        }
    }

    @Override
    public void removed() {
        super.removed();
        sendFlagsToServerAndSave();
    }

    private void sendFlagsToServerAndSave() {
        boolean[] flags = new boolean[COUNT];
        ItemStack stack = Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND);
        flags[0] = player;
        flags[1] = contraption;
        flags[2] = mob;
        flags[3] = animal;
        flags[4] = projectile;
        flags[5] = item;
        BoolNBThelper.saveBooleansAsBytes(stack,flags, KEY);

        NetworkHandler.CHANNEL.sendToServer(new BoolListPacket(true, flags, KEY));

    }

}
