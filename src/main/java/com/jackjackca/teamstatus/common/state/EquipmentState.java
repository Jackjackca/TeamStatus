package com.jackjackca.teamstatus.common.state;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Immutable main-hand snapshot of a team member. Off-hand is not synchronized. */
public record EquipmentState(ItemState mainHand) {

    public static final EquipmentState EMPTY = new EquipmentState(ItemState.EMPTY);

    public static final StreamCodec<RegistryFriendlyByteBuf, EquipmentState> STREAM_CODEC =
            ItemState.STREAM_CODEC.map(EquipmentState::new, EquipmentState::mainHand);
}
