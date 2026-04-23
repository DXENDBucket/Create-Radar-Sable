package com.happysg.radar.networking.packets;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.block.datalink.DataLinkBlockEntity;
import com.happysg.radar.block.datalink.DataPeripheral;
import com.happysg.radar.registry.AllDataBehaviors;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RadarLinkConfigurationPacket implements CustomPacketPayload {
    public static final Type<RadarLinkConfigurationPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateRadar.MODID, "radar_link_configuration"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarLinkConfigurationPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> pkt.write(buf), RadarLinkConfigurationPacket::new);

    private final BlockPos pos;
    private CompoundTag configData;

    public RadarLinkConfigurationPacket(BlockPos pos, CompoundTag configData) {
        this.pos = pos;
        this.configData = configData;
    }

    public RadarLinkConfigurationPacket(FriendlyByteBuf buffer) {
        this.pos = buffer.readBlockPos();
        this.configData = buffer.readNbt();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeNbt(configData);
    }

    public static void handle(RadarLinkConfigurationPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos) instanceof DataLinkBlockEntity be)) return;
            packet.applySettings(be);
        });
    }

    private void applySettings(DataLinkBlockEntity be) {
        if (configData == null || !configData.contains("Id")) {
            be.notifyUpdate();
            return;
        }

        ResourceLocation id = ResourceLocation.parse(configData.getString("Id"));
        DataPeripheral source = AllDataBehaviors.getSource(id);
        if (source == null) {
            be.notifyUpdate();
            return;
        }

        if (be.activeSource == null || be.activeSource != source) {
            be.activeSource = source;
            be.setSourceConfig(configData.copy());
        } else {
            be.getSourceConfig()
                    .merge(configData);
        }

        be.updateGatheredData();
        be.notifyUpdate();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
