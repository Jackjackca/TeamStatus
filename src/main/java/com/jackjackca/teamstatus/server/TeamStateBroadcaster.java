package com.jackjackca.teamstatus.server;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Thin wrapper around the payload send path so the collector stays decoupled from NeoForge. */
public final class TeamStateBroadcaster {

    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
