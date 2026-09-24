package com.jackjackca.teamstatus.common.network;

import java.util.UUID;

import com.jackjackca.teamstatus.TeamStatus;
import com.jackjackca.teamstatus.common.state.ItemState;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; Client edge-triggered item-use signal for one party member (eating, drinking
 * and other fixed-duration {@code UseAnim} actions). Vanilla only syncs the "is using item"
 * entity flag, not the item or its remaining ticks, so the HUD predicts progress locally:
 * START carries the used stack and the total duration in ticks, receivers integrate elapsed
 * ticks and interpolate with the frame's partial tick; STOP (interrupted) and FINISH
 * (completed) terminate the indicator. No per-tick progress is sent.
 */
public record ItemUseActionPacket(UUID memberId, Action action, ItemState item, int durationTicks)
        implements CustomPacketPayload {

    public enum Action {
        /** Use started: begin local 0..duration progress prediction. */
        START,
        /** Use was interrupted (release button, hurt, switched item): remove immediately. */
        STOP,
        /** Use completed (item consumed): remove immediately. */
        FINISH
    }

    public static final Type<ItemUseActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamStatus.MODID, "item_use_action"));

    public static final StreamCodec<ByteBuf, Action> ACTION_STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Enum::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemUseActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, ItemUseActionPacket::memberId,
                    ACTION_STREAM_CODEC, ItemUseActionPacket::action,
                    ItemState.STREAM_CODEC, ItemUseActionPacket::item,
                    ByteBufCodecs.VAR_INT, ItemUseActionPacket::durationTicks,
                    ItemUseActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
