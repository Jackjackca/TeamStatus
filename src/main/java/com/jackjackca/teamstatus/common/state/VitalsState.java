package com.jackjackca.teamstatus.common.state;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable numeric vitals of a team member: health (with max health and absorption), armor,
 * the fully-frozen flag (drives the vanilla {@code FROZEN} heart type) and the
 * {@link HungerState} group (food, saturation, exhaustion).
 * <p>
 * The poison/wither heart types are not stored here: they are derived on the client from the
 * synced {@link EffectState} list, exactly like {@code Gui.HeartType.forPlayer}.
 * <p>
 * Note: hunger values are grouped because a single {@code StreamCodec.composite} accepts at
 * most 6 components; this record already uses all 6 slots.
 */
public record VitalsState(float health, float maxHealth, float absorption, int armor, boolean frozen,
                          HungerState hunger) {

    public static final VitalsState MISSING =
            new VitalsState(0.0F, 20.0F, 0.0F, 0, false, HungerState.MISSING);

    public static final StreamCodec<RegistryFriendlyByteBuf, VitalsState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, VitalsState::health,
            ByteBufCodecs.FLOAT, VitalsState::maxHealth,
            ByteBufCodecs.FLOAT, VitalsState::absorption,
            ByteBufCodecs.VAR_INT, VitalsState::armor,
            ByteBufCodecs.BOOL, VitalsState::frozen,
            HungerState.STREAM_CODEC, VitalsState::hunger,
            VitalsState::new);

    public int foodLevel() {
        return hunger.food();
    }

    public float saturationLevel() {
        return hunger.saturation();
    }

    public float exhaustionLevel() {
        return hunger.exhaustion();
    }
}
