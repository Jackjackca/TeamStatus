package com.jackjackca.teamstatus.common.state;

import java.util.Comparator;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Immutable snapshot of a single status effect (hunger, wither, poison, ...).
 * <p>
 * Per the architecture constraints the localized effect name is never transmitted; only the
 * {@link MobEffect} registry id together with duration/amplifier is sent over the wire.
 */
public record EffectState(ResourceLocation effectId, int duration, int amplifier, boolean ambient,
                         boolean showIcon) {

    public static final StreamCodec<RegistryFriendlyByteBuf, EffectState> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, EffectState::effectId,
            ByteBufCodecs.VAR_INT, EffectState::duration,
            ByteBufCodecs.VAR_INT, EffectState::amplifier,
            ByteBufCodecs.BOOL, EffectState::ambient,
            ByteBufCodecs.BOOL, EffectState::showIcon,
            EffectState::new);

    /** Stable ordering so equal snapshots compare equal even if the source collection order changes. */
    public static final Comparator<EffectState> COMPARATOR =
            Comparator.comparing(EffectState::effectId).thenComparingInt(EffectState::amplifier);

    public static EffectState of(MobEffectInstance instance) {
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
        // The HUD shows whole seconds, so floor the duration to a 20-tick boundary; this keeps
        // snapshots equal while the on-screen countdown is unchanged and cuts effect traffic
        // from 4 Hz polling to at most 1 Hz per active effect. Infinite (-1) is preserved.
        int duration = instance.getDuration();
        if (duration > 0) {
            duration -= duration % 20;
        }
        return new EffectState(id, duration, instance.getAmplifier(), instance.isAmbient(),
                instance.showIcon());
    }
}
