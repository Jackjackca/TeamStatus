package com.example.teamstatus.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

@SuppressWarnings("null")
public record MiningActionPayload(
    UUID playerId,
    BlockState targetBlock,
    float mineProgress,
    boolean isActive
) implements CustomPacketPayload {

    public static final Type<MiningActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("teamstatus", "mining_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MiningActionPayload> STREAM_CODEC = StreamCodec.of(
        MiningActionPayload::encode,
        MiningActionPayload::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, MiningActionPayload payload) {
        buf.writeUUID(payload.playerId());
        
        BlockState blockState = payload.targetBlock();
        if (blockState != null) {
            buf.writeBoolean(true);
            buf.writeVarInt(Block.getId(blockState));
        } else {
            buf.writeBoolean(false);
        }
        
        buf.writeFloat(payload.mineProgress());
        buf.writeBoolean(payload.isActive());
    }

    private static MiningActionPayload decode(RegistryFriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        
        BlockState targetBlock = null;
        if (buf.readBoolean()) {
            int blockId = buf.readVarInt();
            targetBlock = Block.stateById(blockId);
        }
        
        float mineProgress = buf.readFloat();
        boolean isActive = buf.readBoolean();
        
        return new MiningActionPayload(playerId, targetBlock, mineProgress, isActive);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static MiningActionPayload start(UUID playerId, BlockState targetBlock) {
        return new MiningActionPayload(playerId, targetBlock, 0.0f, true);
    }

    public static MiningActionPayload progress(UUID playerId, BlockState targetBlock, float progress) {
        return new MiningActionPayload(playerId, targetBlock, progress, true);
    }

    public static MiningActionPayload stop(UUID playerId) {
        return new MiningActionPayload(playerId, null, 0.0f, false);
    }
}