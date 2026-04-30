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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
    private static final String TAG_LAST_HIT_VEC = "LastHitVec";
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
        setLastHit(stack, pos == null ? null : pos.getCenter());
    }

    public static void setLastHit(ItemStack stack, @Nullable Vec3 pos) {
        CompoundTag tag = ItemNbt.getOrCreateTag(stack);

        if (pos == null) {
            tag.remove(TAG_LAST_HIT);
            tag.remove(TAG_LAST_HIT_VEC);
            ItemNbt.setTag(stack, tag);
            return;
        }

        tag.put(TAG_LAST_HIT, NbtUtils.writeBlockPos(BlockPos.containing(pos)));

        CompoundTag hitVec = new CompoundTag();
        hitVec.putDouble("x", pos.x);
        hitVec.putDouble("y", pos.y);
        hitVec.putDouble("z", pos.z);
        tag.put(TAG_LAST_HIT_VEC, hitVec);

        ItemNbt.setTag(stack, tag);
    }

    @Nullable
    public static BlockPos getLastHit(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null) return null;

        if (tag.contains(TAG_LAST_HIT)) {
            return NbtCompat.readBlockPos(tag, TAG_LAST_HIT);
        }

        Vec3 pos = readLastHitVec(tag);
        if (pos != null) {
            return BlockPos.containing(pos);
        }

        return null;
    }

    @Nullable
    public static Vec3 getLastHitVec(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag == null) return null;

        Vec3 precise = readLastHitVec(tag);
        if (precise != null) {
            return precise;
        }

        BlockPos pos = NbtCompat.readBlockPos(tag, TAG_LAST_HIT);
        return pos == null ? null : pos.getCenter();
    }

    public static boolean hasLastHit(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null && (tag.contains(TAG_LAST_HIT) || tag.contains(TAG_LAST_HIT_VEC, Tag.TAG_COMPOUND));
    }

    public static void clearLastHit(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag != null) {
            tag.remove(TAG_LAST_HIT);
            tag.remove(TAG_LAST_HIT_VEC);
            ItemNbt.setTag(stack, tag);
        }
    }

    @Nullable
    private static Vec3 readLastHitVec(CompoundTag tag) {
        if (!tag.contains(TAG_LAST_HIT_VEC, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag hit = tag.getCompound(TAG_LAST_HIT_VEC);
        if (!hit.contains("x", Tag.TAG_DOUBLE) || !hit.contains("y", Tag.TAG_DOUBLE) || !hit.contains("z", Tag.TAG_DOUBLE)) {
            return null;
        }

        return new Vec3(hit.getDouble("x"), hit.getDouble("y"), hit.getDouble("z"));
    }

}

