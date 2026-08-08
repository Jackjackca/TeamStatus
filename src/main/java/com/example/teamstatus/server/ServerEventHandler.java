package com.example.teamstatus.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@SuppressWarnings("null")
public class ServerEventHandler {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerLeave);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerRespawn);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerDeath);

        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onLivingHeal);

        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onUseItemStart);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onUseItemStop);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onUseItemFinish);

        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onBlockBreakStart);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onBlockBreakEnd);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerStopUsingItem);
        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerBreakSpeed);

        NeoForge.EVENT_BUS.addListener(ServerEventHandler::onPlayerAttackEntity);
    }

    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handlePlayerJoin(player);
    }

    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handlePlayerLeave(player);
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handlePlayerRespawn(player);
    }

    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handlePlayerDeath(player);
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handleStateChange(player);
    }

    public static void onLivingHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handleStateChange(player);
    }

    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handleStateChange(player);
        
        FoodProperties food = event.getItem().getItem().getFoodProperties(event.getItem(), event.getEntity());
        if (food != null) {
            ServerHandler.handleEatingStart(player, event.getDuration());
        }
    }

    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handleStateChange(player);
        
        FoodProperties food = event.getItem().getItem().getFoodProperties(event.getItem(), event.getEntity());
        if (food != null) {
            ServerHandler.handleEatingStop(player);
        }
    }

    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handleStateChange(player);
        
        FoodProperties food = event.getItem().getItem().getFoodProperties(event.getItem(), event.getEntity());
        if (food != null) {
            ServerHandler.handleEatingStop(player);
        }
    }

    public static void onBlockBreakStart(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            BlockState targetBlock = event.getLevel().getBlockState(event.getPos());
            ServerHandler.handleMiningStart(player, targetBlock);
        } else if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.ABORT) {
            ServerHandler.handleMiningStop(player);
        }
    }

    public static void onBlockBreakEnd(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ServerHandler.handleMiningStop(player);
    }

    public static void onPlayerStopUsingItem(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handleMiningStop(player);
    }
    
    public static void onPlayerBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getState() != null) {
            BlockPos pos = event.getPosition().orElse(null);
            ServerHandler.handleMiningProgress(player, event.getState(), event.getNewSpeed(), pos);
        }
    }

    public static void onPlayerAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerHandler.handleAttack(player, event.getTarget().getType());
    }
}