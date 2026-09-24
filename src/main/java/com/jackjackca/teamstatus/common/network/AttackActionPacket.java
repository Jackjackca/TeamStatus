package com.jackjackca.teamstatus.common.network;

import java.util.UUID;

import javax.annotation.Nullable;

import com.jackjackca.teamstatus.TeamStatus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; Client edge-triggered attack signal: a party member (the attacker) just hurt
 * a living entity. Sent only from {@code LivingDamageEvent.Post} when the damage source is a
 * player, so there is no periodic traffic.
 * <p>
 * The target is described in flat form for the HUD overlay: player targets carry
 * {@link #targetId()} + {@link #targetName()} for the face texture; mob targets carry their
 * entity type registry id for a flat icon. Each packet is one strike — the client restarts
 * the one-shot swing and refreshes (and accumulates) the red hit flash per receipt.
 */
public record AttackActionPacket(UUID memberId, TargetKind kind, @Nullable UUID targetId,
                                 @Nullable String targetName, @Nullable ResourceLocation entityType)
        implements CustomPacketPayload {

    public enum TargetKind {
        PLAYER, MOB
    }

    public static final Type<AttackActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamStatus.MODID, "attack_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttackActionPacket> STREAM_CODEC =
            StreamCodec.of(AttackActionPacket::write, AttackActionPacket::read);

    private static void write(RegistryFriendlyByteBuf buf, AttackActionPacket packet) {
        buf.writeUUID(packet.memberId());
        buf.writeEnum(packet.kind());
        buf.writeBoolean(packet.targetId() != null);
        if (packet.targetId() != null) {
            buf.writeUUID(packet.targetId());
        }
        buf.writeBoolean(packet.targetName() != null);
        if (packet.targetName() != null) {
            buf.writeUtf(packet.targetName());
        }
        buf.writeBoolean(packet.entityType() != null);
        if (packet.entityType() != null) {
            buf.writeResourceLocation(packet.entityType());
        }
    }

    private static AttackActionPacket read(RegistryFriendlyByteBuf buf) {
        UUID memberId = buf.readUUID();
        TargetKind kind = buf.readEnum(TargetKind.class);
        UUID targetId = buf.readBoolean() ? buf.readUUID() : null;
        String targetName = buf.readBoolean() ? buf.readUtf() : null;
        ResourceLocation entityType = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new AttackActionPacket(memberId, kind, targetId, targetName, entityType);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
