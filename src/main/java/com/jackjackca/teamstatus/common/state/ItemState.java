package com.jackjackca.teamstatus.common.state;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable wrapper around an {@link ItemStack} used for main-/off-hand synchronization.
 * <p>
 * Uses {@link ItemStack#OPTIONAL_STREAM_CODEC}: empty hands are encoded as nothing instead of
 * wasting bandwidth, and the stack is only (re)sent when it is part of a dirty snapshot.
 */
public record ItemState(ItemStack stack) {

    public static final ItemState EMPTY = new ItemState(ItemStack.EMPTY);

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemState> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.map(ItemState::new, ItemState::stack);

    public static ItemState of(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : new ItemState(stack.copy());
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }
}
