package com.example.teamstatus.network;

import com.example.teamstatus.client.network.TeamTrackerClient;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络处理器
 *
 * 重构后的架构：
 * - 客户端只接收服务端推送的状态包（Server -> Client）
 * - 客户端不再主动发送状态上报（客户端采集已移除）
 * - 服务端主动读取玩家状态，通过 ServerHandler 广播
 */
@SuppressWarnings("null")
public class NetworkHandler {

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playBidirectional(
            PlayerStatusPayload.TYPE,
            PlayerStatusPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                TeamTrackerClient::receivePlayerStatus,
                (payload, context) -> {}
            )
        );

        registrar.playBidirectional(
            MiningActionPayload.TYPE,
            MiningActionPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                TeamTrackerClient::receiveMiningAction,
                (payload, context) -> {}
            )
        );

        registrar.playBidirectional(
            AttackActionPayload.TYPE,
            AttackActionPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                TeamTrackerClient::receiveAttackAction,
                (payload, context) -> {}
            )
        );

        registrar.playBidirectional(
            EatingActionPayload.TYPE,
            EatingActionPayload.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                TeamTrackerClient::receiveEatingAction,
                (payload, context) -> {}
            )
        );
    }
}
