package com.happysg.radar.item;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.monitor.MonitorBlockEntity;
import com.happysg.radar.utils.ItemNbt;
import com.happysg.radar.utils.NbtCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class SafeZoneDesignatorItem extends Item {

    public SafeZoneDesignatorItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public void inventoryTick(ItemStack pStack, Level pLevel, Entity pEntity, int pSlotId, boolean pIsSelected) {
        super.inventoryTick(pStack, pLevel, pEntity, pSlotId, pIsSelected);
        if (pIsSelected) {
            CompoundTag data = ItemNbt.getTag(pStack);
            if (data != null && data.contains("monitorPos")) {
                BlockPos monitorPos = NbtCompat.readBlockPos(data, "monitorPos");
                if (monitorPos == null) return;
                if (pLevel.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitorBlockEntity && pLevel.isClientSide) {
                    monitorBlockEntity.showSafeZone();
                }
            }
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        BlockPos pos = pContext.getClickedPos();
        Level level = pContext.getLevel();
        ItemStack stack = pContext.getItemInHand();
        CompoundTag data = ItemNbt.getOrCreateTag(stack);
        Player player = pContext.getPlayer();

        if (player == null) {
            return InteractionResult.FAIL;
        }
        boolean isCrouching = player.isCrouching();
        if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitorBlockEntity) {
            data.put("monitorPos", NbtUtils.writeBlockPos(monitorBlockEntity.getControllerPos()));
            ItemNbt.setTag(stack, data);
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.set", ChatFormatting.GREEN);
            return InteractionResult.SUCCESS;
        }

        if (!data.contains("monitorPos")) {
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.no_monitor", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        BlockPos monitorPos = NbtCompat.readBlockPos(data, "monitorPos");
        if (monitorPos == null) return InteractionResult.FAIL;

        if (!data.contains("startPos")) {
            if (level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitorBlockEntity) {
                if (monitorBlockEntity.getController().tryRemoveAABB(pos)) {
                    displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.remove", ChatFormatting.RED);
                    return InteractionResult.SUCCESS;
                }
            }
            data.put("startPos", NbtUtils.writeBlockPos(pos));
            displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.start", ChatFormatting.GREEN);
        } else {
            if (player.isCrouching()) {
                data.remove("startPos");
                ItemNbt.setTag(stack, data);
                displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.reset", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }

            BlockPos startPos = NbtCompat.readBlockPos(data, "startPos");
            if (startPos == null) return InteractionResult.FAIL;

            if (level.getBlockEntity(monitorPos) instanceof MonitorBlockEntity monitorBlockEntity) {
                monitorBlockEntity.addSafeZone(startPos, pos);
                displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.end", ChatFormatting.GREEN);
                data.remove("startPos");
            } else {
                displayMessage(player, CreateRadar.MODID + ".item.safe_zone_designator.no_monitor", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
        }

        ItemNbt.setTag(stack, data);
        return InteractionResult.SUCCESS;
    }

    private void displayMessage(Player player, String messageKey, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(messageKey).withStyle(color), true);
    }

    @Override
    public void appendHoverText(ItemStack pStack, Item.TooltipContext pContext, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pContext, pTooltipComponents, pIsAdvanced);
        CompoundTag data = ItemNbt.getTag(pStack);
        if (data != null && data.contains("monitorPos")) {
            BlockPos monitorPos = NbtCompat.readBlockPos(data, "monitorPos");
            if (monitorPos == null) return;
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.linked_monitor", monitorPos));
        } else
            pTooltipComponents.add(Component.translatable(CreateRadar.MODID + ".guided_fuze.no_monitor"));
    }

    @Nullable
    public BlockPos getMonitorPos(ItemStack stack) {
        CompoundTag data = ItemNbt.getTag(stack);
        if (data != null && data.contains("monitorPos")) {
            return NbtCompat.readBlockPos(data, "monitorPos");
        }
        return null;
    }
}
