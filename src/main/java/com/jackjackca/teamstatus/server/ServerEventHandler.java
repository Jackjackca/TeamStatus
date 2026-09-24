package com.jackjackca.teamstatus.server;

import com.jackjackca.teamstatus.TeamStatus;
import com.jackjackca.teamstatus.common.network.ItemUseActionPacket;
import com.jackjackca.teamstatus.common.network.MiningActionPacket;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.UseAnim;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus events that drive server side collection. Everything here runs on the server
 * main thread (the integrated server in singleplayer too).
 * <p>
 * Snapshot events only mark members dirty; the actual state is read once at tick end in
 * {@link TeamStateCollector#serverTick}. Mining/attack/item-use events are edge signals
 * forwarded immediately by {@link TeamActionSignals} only to players who see the actor.
 */
@EventBusSubscriber(modid = TeamStatus.MODID)
public final class ServerEventHandler {

    private static final TeamStateBroadcaster BROADCASTER = new TeamStateBroadcaster();
    private static final TeamStateCollector COLLECTOR = new TeamStateCollector(BROADCASTER);
    private static final TeamActionSignals ACTION_SIGNALS = new TeamActionSignals(BROADCASTER);

    private ServerEventHandler() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TeamStatusCommand.register(event.getDispatcher(), COLLECTOR);
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        COLLECTOR.serverTick(event.getServer());
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            COLLECTOR.markDirty(player.getUUID());
        }
        // Player source only; DamageSource#getEntity() already returns the shooter for arrows.
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer attacker && attacker != event.getEntity()) {
            ACTION_SIGNALS.onAttack(attacker, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            COLLECTOR.markDirty(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            COLLECTOR.markDirty(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            COLLECTOR.markDirty(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            COLLECTOR.markDirty(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            COLLECTOR.markDirty(player.getUUID());
        }
    }

    // ---- Fixed-duration item use (eating/drinking): edge signals for HUD progress ----

    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && isConsumable(event.getItem())) {
            ACTION_SIGNALS.onItemUse(player, ItemUseActionPacket.Action.START,
                    event.getItem(), event.getDuration());
        }
    }

    @SubscribeEvent
    public static void onItemUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player && isConsumable(event.getItem())) {
            ACTION_SIGNALS.onItemUse(player, ItemUseActionPacket.Action.STOP,
                    event.getItem(), 0);
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && isConsumable(event.getItem())) {
            ACTION_SIGNALS.onItemUse(player, ItemUseActionPacket.Action.FINISH,
                    event.getItem(), 0);
        }
    }

    private static boolean isConsumable(net.minecraft.world.item.ItemStack stack) {
        UseAnim anim = stack.getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        switch (event.getAction()) {
            case START -> ACTION_SIGNALS.onMiningAction(player, MiningActionPacket.Action.START, event.getPos());
            case STOP -> ACTION_SIGNALS.onMiningAction(player, MiningActionPacket.Action.STOP, event.getPos());
            case ABORT -> ACTION_SIGNALS.onMiningAction(player, MiningActionPacket.Action.ABORT, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        COLLECTOR.onPlayerLoggedOut(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        COLLECTOR.onServerStopped();
    }
}
