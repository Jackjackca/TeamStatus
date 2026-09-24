package com.jackjackca.teamstatus.client.state;

import java.util.List;

import com.jackjackca.teamstatus.Config;
import com.jackjackca.teamstatus.common.state.EffectState;
import com.jackjackca.teamstatus.common.state.EquipmentState;
import com.jackjackca.teamstatus.common.state.HungerState;
import com.jackjackca.teamstatus.common.state.ItemState;
import com.jackjackca.teamstatus.common.state.TeamMemberState;
import com.jackjackca.teamstatus.common.state.VitalsState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Builds the local player's own {@link TeamMemberState} from the authoritative client entity
 * every client tick and publishes it to {@link ClientTeamState}. This replaces server
 * self-snapshots entirely: self vitals update with zero network latency (health/food arrive
 * vanilla-synced on the client entity before any mod packet), and solo mode needs no
 * server-side traffic at all.
 * <p>
 * Client main thread only.
 */
public final class LocalSelfState {

    private LocalSelfState() {
    }

    /** Publishes the current self state if in a world and self display is enabled; clears it otherwise. */
    public static void publish() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!Config.HUD_SHOW_SELF.get() || player == null) {
            ClientTeamState.setLocalSelf(null);
            return;
        }

        HungerState hunger = new HungerState(
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(),
                player.getFoodData().getExhaustionLevel());
        VitalsState vitals = new VitalsState(
                player.getHealth(),
                player.getMaxHealth(),
                player.getAbsorptionAmount(),
                player.getArmorValue(),
                player.isFullyFrozen(),
                hunger);

        List<EffectState> effects = player.getActiveEffects().stream()
                .map(EffectState::of)
                .sorted(EffectState.COMPARATOR)
                .toList();
        EquipmentState equipment = new EquipmentState(ItemState.of(player.getMainHandItem()));

        TeamMemberState self = TeamMemberState.online(player.getUUID(),
                player.getGameProfile().getName(), !player.isDeadOrDying(), vitals, effects, equipment);
        ClientTeamState.setLocalSelf(self);
        // Keep the self damage/heal flash memory in sync with the authoritative local health.
        ClientHealthFxState.update(List.of(self));
    }
}
