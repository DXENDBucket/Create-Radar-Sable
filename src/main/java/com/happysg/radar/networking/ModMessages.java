package com.happysg.radar.networking;

import com.happysg.radar.CreateRadar;
import com.happysg.radar.networking.packets.RadarLinkConfigurationPacket;
import com.simibubi.create.foundation.networking.SimplePacketBase;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class ModMessages {

    private static SimpleChannel INSTANCE;
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        INSTANCE = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(CreateRadar.MODID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        c2s(RadarLinkConfigurationPacket.class);
    }

    private static <T extends SimplePacketBase> void s2c(Class<T> clazz) {
        make(clazz, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static <T extends SimplePacketBase> void c2s(Class<T> clazz) {
        make(clazz, NetworkDirection.PLAY_TO_SERVER);
    }

    private static <T extends SimplePacketBase> void make(Class<T> clazz, NetworkDirection dir) {
        INSTANCE.messageBuilder(clazz, id(), dir)
                .decoder(buf -> {
                    try {
                        return clazz.getConstructor(FriendlyByteBuf.class).newInstance(buf);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to construct packet", e);
                    }
                })
                .encoder(SimplePacketBase::write)
                .consumerMainThread(ModMessages::handler)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToClients(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }


    private static boolean handler(SimplePacketBase packet, Supplier<NetworkEvent.Context> ctx) {
        return packet.handle(ctx.get());
    }
}
