package com.example.teamstatus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

@SuppressWarnings("null")
public record EatingActionPayload(
    UUID playerId,
    int actionDuration,
    boolean isActive
) implements CustomPacketPayload {

    public static final Type<EatingActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamstatus", "eating_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EatingActionPayload> STREAM_CODEC = StreamCodec.of(
        EatingActionPayload::encode,
        EatingActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, EatingActionPayload payload) {
        buf.writeUUID(payload.playerId());
        buf.writeInt(payload.actionDuration());
        buf.writeBoolean(payload.isActive());
    }

    private static EatingActionPayload decode(RegistryFriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        int actionDuration = buf.readInt();
        boolean isActive = buf.readBoolean();
        
        return new EatingActionPayload(playerId, actionDuration, isActive);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static EatingActionPayload start(UUID playerId, int duration) {
        return new EatingActionPayload(playerId, duration, true);
    }

    public static EatingActionPayload stop(UUID playerId) {
        return new EatingActionPayload(playerId, 0, false);
    }
}