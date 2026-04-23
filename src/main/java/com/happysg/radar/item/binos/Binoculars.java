package com.happysg.radar.item.binos;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.controller.networkcontroller.NetworkFiltererBlockEntity;
import com.happysg.radar.utils.ItemNbt;
import com.happysg.radar.utils.NbtCompat;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class Binoculars extends SpyglassItem {
    private static final Logger LOGGER = LogUtils.getLogger();
    // how far the ray should go
    private static final double MAX_DISTANCE = 512.0;

    // step size for walking along the ray (smaller = more accurate, slightly more expensive)
    private static final double STEP = 0.15;
    private static final String TAG_LAST_HIT = "LastHitPos";
    private BlockPos targetBlock;

    public Binoculars(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack pStack, Item.TooltipContext pContext, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pContext, pTooltipComponents, pIsAdvanced);
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.base_text"));
        }
        CompoundTag tag = ItemNbt.getTag(pStack);
        if (tag != null && tag.contains("filtererPos")) {
            BlockPos monitorPos = NbtCompat.readBlockPos(tag, "filtererPos");
            if (monitorPos == null) return;
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.controller").append(": " + monitorPos.toShortString()));
        } else {
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".binoculars.no_controller"));
        }


    }
    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockPos clickedPos = pContext.getClickedPos();
        Player player = pContext.getPlayer();
        if (player == null) return super.useOn(pContext);

        if (pContext.getLevel().getBlockEntity(clickedPos) instanceof NetworkFiltererBlockEntity blockEntity) {
            player.displayClientMessage(
                    Component.translatable(CreateRadar.MODID + ".binoculars.paired").withStyle(ChatFormatting.BLUE),
                    true
            );

            CompoundTag tag = ItemNbt.getOrCreateTag(pContext.getItemInHand());
            tag.put("filterPos", NbtUtils.writeBlockPos(blockEntity.getBlockPos()));
            tag.put("filtererPos", NbtUtils.writeBlockPos(blockEntity.getBlockPos()));
            ItemNbt.setTag(pContext.getItemInHand(), tag);

            return InteractionResult.SUCCESS;
        }
        return super.useOn(pContext);
    }

    public static void setLastHit(ItemStack stack, @Nullable BlockPos pos) {
        CompoundTag tag = ItemNbt.getOrCreateTag(stack);

        if (pos == null) {
            tag.remove(TAG_LAST_HIT);
            ItemNbt.setTag(stack, tag);
            return;
        }

        tag.put(TAG_LAST_HIT, NbtUtils.writeBlockPos(pos));
        ItemNbt.setTag(stack, tag);
    }

    @Nullable
    public static BlockPos getLastHit(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null || !tag.contains(TAG_LAST_HIT)) return null;

        return NbtCompat.readBlockPos(tag, TAG_LAST_HIT);
    }

    public static boolean hasLastHit(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null && tag.contains(TAG_LAST_HIT);
    }

    public static void clearLastHit(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag != null) {
            tag.remove(TAG_LAST_HIT);
            ItemNbt.setTag(stack, tag);
        }
    }

}

