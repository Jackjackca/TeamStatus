package com.jackjackca.teamstatus.common.network;

import com.jackjackca.teamstatus.client.ClientPacketHandler;

import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Registers all custom payloads using the 1.21 {@code CustomPacketPayload} /
 * {@code RegisterPayloadHandlersEvent} system.
 * <p>
 * Thread-safety: payload handlers run on the network thread, so all client state mutation is
 * explicitly dispatched to the client main thread through {@link IPayloadContext#enqueueWork}.
 */
public final class NetworkHandler {

    public static final String PROTOCOL_VERSION = "1";

    private NetworkHandler() {
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(TeamStatePacket.TYPE, TeamStatePacket.STREAM_CODEC, NetworkHandler::handleClientbound)
                .playToClient(MiningActionPacket.TYPE, MiningActionPacket.STREAM_CODEC, NetworkHandler::handleMiningClientbound)
                .playToClient(AttackActionPacket.TYPE, AttackActionPacket.STREAM_CODEC, NetworkHandler::handleAttackClientbound)
                .playToClient(ItemUseActionPacket.TYPE, ItemUseActionPacket.STREAM_CODEC, NetworkHandler::handleItemUseClientbound);
    }

    private static void handleClientbound(TeamStatePacket payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            // Never touch client state directly on the network thread.
            context.enqueueWork(() -> ClientPacketHandler.handle(payload));
        }
    }

    private static void handleMiningClientbound(MiningActionPacket payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> ClientPacketHandler.handle(payload));
        }
    }

    private static void handleAttackClientbound(AttackActionPacket payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> ClientPacketHandler.handle(payload));
        }
    }

    private static void handleItemUseClientbound(ItemUseActionPacket payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            context.enqueueWork(() -> ClientPacketHandler.handle(payload));
        }
    }
}
