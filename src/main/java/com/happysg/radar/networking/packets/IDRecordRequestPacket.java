package com.happysg.radar.networking.packets;

import com.happysg.radar.block.controller.id.IDManager;
import com.happysg.radar.networking.ModMessages;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public class IDRecordRequestPacket extends SimplePacketBase {
    private final long shipId;

    public IDRecordRequestPacket(long shipId) {
        this.shipId = shipId;
    }

    public IDRecordRequestPacket(FriendlyByteBuf buffer) {
        this.shipId = buffer.readLong();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(shipId);
    }

    @Override
    public boolean handle(NetworkEvent.Context context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;

            IDManager.IDRecord record = IDManager.getIDRecordByShipId(shipId);
            if (record == null) {
                ModMessages.sendToPlayer(new IDRecordSyncPacket(shipId, false, "", ""), sender);
                return;
            }

            ModMessages.sendToPlayer(
                    new IDRecordSyncPacket(shipId, true, record.name(), record.secretID()),
                    sender
            );
        });
        return true;
    }
}
