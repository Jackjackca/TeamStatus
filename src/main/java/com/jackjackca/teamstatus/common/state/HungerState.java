package com.jackjackca.teamstatus.common.state;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable hunger-related numbers: vanilla food level plus the hidden AppleSkin values
 * (saturation and exhaustion) that vanilla never syncs to other players.
 */
public record HungerState(int food, float saturation, float exhaustion) {

    public static final HungerState MISSING = new HungerState(0, 0.0F, 0.0F);

    public static final StreamCodec<RegistryFriendlyByteBuf, HungerState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HungerState::food,
            ByteBufCodecs.FLOAT, HungerState::saturation,
            ByteBufCodecs.FLOAT, HungerState::exhaustion,
            HungerState::new);
}
