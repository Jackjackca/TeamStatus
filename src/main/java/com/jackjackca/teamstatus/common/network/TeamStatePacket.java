package com.jackjackca.teamstatus.common.network;

import java.util.List;

import com.jackjackca.teamstatus.TeamStatus;
import com.jackjackca.teamstatus.common.state.TeamMemberState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client payload carrying the full (but delta-gated and throttled) team snapshot
 * for the receiving player.
 * <p>
 * Implements {@link CustomPacketPayload} directly; the old SimpleChannel API is not used.
 */
public record TeamStatePacket(List<TeamMemberState> members) implements CustomPacketPayload {

    public static final Type<TeamStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamStatus.MODID, "team_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, List<TeamMemberState>> MEMBER_LIST_STREAM_CODEC =
            TeamMemberState.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamStatePacket> STREAM_CODEC =
            MEMBER_LIST_STREAM_CODEC.map(TeamStatePacket::new, TeamStatePacket::members);

    public TeamStatePacket {
        members = List.copyOf(members);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
