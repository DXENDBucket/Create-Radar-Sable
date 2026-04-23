package com.happysg.radar.networking;

import com.happysg.radar.block.monitor.MonitorSelectionPacket;
import com.happysg.radar.networking.packets.BoolListPacket;
import com.happysg.radar.networking.packets.FirePacket;
import com.happysg.radar.networking.packets.RadarLinkConfigurationPacket;
import com.happysg.radar.networking.packets.RaycastPacket;
import com.happysg.radar.networking.packets.SaveListsPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final Channel CHANNEL = new Channel();

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(SaveListsPacket.TYPE, SaveListsPacket.STREAM_CODEC, SaveListsPacket::handle);
        registrar.playToServer(BoolListPacket.TYPE, BoolListPacket.STREAM_CODEC, BoolListPacket::handle);
        registrar.playToServer(RaycastPacket.TYPE, RaycastPacket.STREAM_CODEC, RaycastPacket::handle);
        registrar.playToServer(FirePacket.TYPE, FirePacket.STREAM_CODEC, FirePacket::handle);
        registrar.playToServer(MonitorSelectionPacket.TYPE, MonitorSelectionPacket.STREAM_CODEC, MonitorSelectionPacket::handle);
        registrar.playToServer(RadarLinkConfigurationPacket.TYPE, RadarLinkConfigurationPacket.STREAM_CODEC, RadarLinkConfigurationPacket::handle);
    }

    public static class Channel {
        public void sendToServer(CustomPacketPayload payload) {
            PacketDistributor.sendToServer(payload);
        }
    }
}
