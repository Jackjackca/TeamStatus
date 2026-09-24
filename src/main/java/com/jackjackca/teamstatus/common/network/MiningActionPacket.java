package com.jackjackca.teamstatus.common.network;

import java.util.UUID;

import com.jackjackca.teamstatus.TeamStatus;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server &rarr; Client edge-triggered mining lifecycle signal for one party member.
 * <p>
 * {@link Action#START} carries the target {@link BlockState} (required because receivers may
 * be in another dimension and cannot resolve the block themselves) and the per-tick destroy
 * progress rate ({@code BlockState#getDestroyProgress}) so receivers predict progress
 * locally; {@link Action#STOP} and {@link Action#ABORT} terminate the prediction and the
 * block state is {@link Blocks#AIR} for those actions. No continuous progress is sent.
 */
public record MiningActionPacket(UUID memberId, Action action, BlockPos pos, float speedPerTick,
                                 BlockState blockState)
        implements CustomPacketPayload {

    public enum Action {
        /** Mining started: begin client-side prediction and 3D target rendering. */
        START,
        /** Block was broken: completion particle burst, then the indicator disappears. */
        STOP,
        /** Mining was cancelled (released button or retargeted): remove immediately. */
        ABORT
    }

    public static final Type<MiningActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeamStatus.MODID, "mining_action"));

    public static final StreamCodec<ByteBuf, Action> ACTION_STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[i], Enum::ordinal);

    public static final StreamCodec<ByteBuf, BlockState> BLOCK_STATE_STREAM_CODEC =
            ByteBufCodecs.fromCodec(BlockState.CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, MiningActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, MiningActionPacket::memberId,
                    ACTION_STREAM_CODEC, MiningActionPacket::action,
                    BlockPos.STREAM_CODEC, MiningActionPacket::pos,
                    ByteBufCodecs.FLOAT, MiningActionPacket::speedPerTick,
                    BLOCK_STATE_STREAM_CODEC, MiningActionPacket::blockState,
                    MiningActionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
