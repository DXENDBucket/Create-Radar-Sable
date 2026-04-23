package com.happysg.radar.networking;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class ModMessages {
    public static void sendToServer(CustomPacketPayload payload) {
        NetworkHandler.CHANNEL.sendToServer(payload);
    }
}
