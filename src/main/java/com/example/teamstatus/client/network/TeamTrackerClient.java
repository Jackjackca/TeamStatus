package com.example.teamstatus.client.network;

import com.example.teamstatus.client.TeamTracker;
import com.example.teamstatus.network.AttackActionPayload;
import com.example.teamstatus.network.EatingActionPayload;
import com.example.teamstatus.network.MiningActionPayload;
import com.example.teamstatus.network.PlayerStatusPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TeamTrackerClient {

    public static void receivePlayerStatus(PlayerStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TeamTracker.updateRemotePlayer(payload.playerId(), payload));
    }

    public static void receiveMiningAction(MiningActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TeamTracker.updateMiningAction(payload.playerId(), payload));
    }

    public static void receiveAttackAction(AttackActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TeamTracker.updateAttackAction(payload.playerId(), payload));
    }

    public static void receiveEatingAction(EatingActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TeamTracker.updateEatingAction(payload.playerId(), payload));
    }
}