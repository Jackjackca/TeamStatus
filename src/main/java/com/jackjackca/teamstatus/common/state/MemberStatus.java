package com.jackjackca.teamstatus.common.state;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Identity and presence flags of a party member.
 * <p>
 * The {@code online} / {@code alive} flags guard against unloaded or dead players so that
 * clients never dereference missing entities.
 */
public record MemberStatus(UUID id, String name, boolean online, boolean alive) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MemberStatus> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MemberStatus::id,
            ByteBufCodecs.STRING_UTF8, MemberStatus::name,
            ByteBufCodecs.BOOL, MemberStatus::online,
            ByteBufCodecs.BOOL, MemberStatus::alive,
            MemberStatus::new);
}
