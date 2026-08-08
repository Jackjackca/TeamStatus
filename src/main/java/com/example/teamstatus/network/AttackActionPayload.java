package com.example.teamstatus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.UUID;

@SuppressWarnings("null")
public record AttackActionPayload(
    UUID playerId,
    EntityType<?> targetEntity,
    boolean isActive
) implements CustomPacketPayload {

    public static final Type<AttackActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamstatus", "attack_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttackActionPayload> STREAM_CODEC = StreamCodec.of(
        AttackActionPayload::encode,
        AttackActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, AttackActionPayload payload) {
        buf.writeUUID(payload.playerId());
        
        EntityType<?> entityType = payload.targetEntity();
        if (entityType != null) {
            buf.writeBoolean(true);
            buf.writeResourceLocation(entityType.builtInRegistryHolder().getKey().location());
        } else {
            buf.writeBoolean(false);
        }
        
        buf.writeBoolean(payload.isActive());
    }

    private static AttackActionPayload decode(RegistryFriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        
        EntityType<?> targetEntity = null;
        if (buf.readBoolean()) {
            ResourceLocation entityLoc = buf.readResourceLocation();
            targetEntity = EntityType.byString(entityLoc.toString()).orElse(null);
        }
        
        boolean isActive = buf.readBoolean();
        
        return new AttackActionPayload(playerId, targetEntity, isActive);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static AttackActionPayload hit(UUID playerId, EntityType<?> targetEntity) {
        return new AttackActionPayload(playerId, targetEntity, true);
    }

    public static AttackActionPayload stop(UUID playerId) {
        return new AttackActionPayload(playerId, null, false);
    }
}