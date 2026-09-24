package com.jackjackca.teamstatus.common.state;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Immutable snapshot of one party member: identity/presence ({@link MemberStatus}), numeric
 * {@link VitalsState}, active {@link EffectState}s and held {@link EquipmentState}.
 * <p>
 * The server collector never traverses client entities and may produce entries for offline
 * party members; those carry {@code online=false}/{@code alive=false} and missing vitals.
 */
public record TeamMemberState(MemberStatus status, VitalsState vitals, List<EffectState> effects,
                              EquipmentState equipment) {

    public TeamMemberState {
        effects = List.copyOf(effects);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, List<EffectState>> EFFECTS_STREAM_CODEC =
            EffectState.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, TeamMemberState> STREAM_CODEC = StreamCodec.composite(
            MemberStatus.STREAM_CODEC, TeamMemberState::status,
            VitalsState.STREAM_CODEC, TeamMemberState::vitals,
            EFFECTS_STREAM_CODEC, TeamMemberState::effects,
            EquipmentState.STREAM_CODEC, TeamMemberState::equipment,
            TeamMemberState::new);

    public static TeamMemberState online(UUID id, String name, boolean alive, VitalsState vitals,
                                         List<EffectState> effects, EquipmentState equipment) {
        return new TeamMemberState(new MemberStatus(id, name, true, alive), vitals, List.copyOf(effects), equipment);
    }

    public static TeamMemberState offline(UUID id, String name) {
        return new TeamMemberState(new MemberStatus(id, name, false, false), VitalsState.MISSING,
                List.of(), EquipmentState.EMPTY);
    }

    public UUID id() {
        return status.id();
    }

    public String name() {
        return status.name();
    }

    public boolean online() {
        return status.online();
    }

    public boolean alive() {
        return status.alive();
    }

    public float health() {
        return vitals.health();
    }

    public float maxHealth() {
        return vitals.maxHealth();
    }

    public float absorptionAmount() {
        return vitals.absorption();
    }

    public int foodLevel() {
        return vitals.foodLevel();
    }

    public float saturationLevel() {
        return vitals.saturationLevel();
    }

    public float exhaustionLevel() {
        return vitals.exhaustionLevel();
    }

    public int armorValue() {
        return vitals.armor();
    }

    public ItemState mainHand() {
        return equipment.mainHand();
    }
}
