package com.jackjackca.teamstatus.server;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /teamstatus} — per-player editing of the world-persistent hidden list.
 * <pre>
 *   /teamstatus hide &lt;players&gt;   remove players from your HUD
 *   /teamstatus show &lt;players&gt;   re-show players
 *   /teamstatus show all         reset (show every online player, the default)
 *   /teamstatus list             print your current hidden players
 * </pre>
 * Every player may run this for their own list (permission level 0). Changes take effect on
 * the next server tick's snapshot rebuild.
 */
public final class TeamStatusCommand {

    private static final String ARG_TARGETS = "targets";

    private TeamStatusCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, TeamStateCollector collector) {
        dispatcher.register(Commands.literal("teamstatus")
                .then(Commands.literal("hide")
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(ctx -> hide(ctx, collector))))
                .then(Commands.literal("show")
                        .then(Commands.literal("all")
                                .executes(ctx -> showAll(ctx, collector)))
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .executes(ctx -> show(ctx, collector))))
                .then(Commands.literal("list")
                        .executes(TeamStatusCommand::list)));
    }

    private static int hide(CommandContext<CommandSourceStack> ctx, TeamStateCollector collector)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        List<ServerPlayer> targets = EntityArgument.getPlayers(ctx, ARG_TARGETS).stream()
                .filter(p -> !p.getUUID().equals(self.getUUID()))
                .toList();
        if (targets.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("You cannot hide yourself."));
            return 0;
        }
        DisplayListStore.get(self.server).hide(self.getUUID(),
                targets.stream().map(ServerPlayer::getUUID).toList());
        collector.markDisplayListsChanged();
        ctx.getSource().sendSuccess(() -> Component.literal("Hidden " + namesOf(targets)
                + " from your team HUD."), false);
        return targets.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx, TeamStateCollector collector)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        List<ServerPlayer> targets = EntityArgument.getPlayers(ctx, ARG_TARGETS).stream().toList();
        boolean changed = DisplayListStore.get(self.server).show(self.getUUID(),
                targets.stream().map(ServerPlayer::getUUID).toList());
        if (changed) {
            collector.markDisplayListsChanged();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Showing " + namesOf(targets)
                + " on your team HUD."), false);
        return targets.size();
    }

    private static int showAll(CommandContext<CommandSourceStack> ctx, TeamStateCollector collector)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        boolean changed = DisplayListStore.get(self.server).showAll(self.getUUID());
        if (changed) {
            collector.markDisplayListsChanged();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Showing all players on your team HUD."), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        java.util.Set<UUID> hidden = DisplayListStore.get(self.server).getHidden(self.getUUID());
        if (hidden.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("You are seeing every online player."), false);
            return 0;
        }
        List<String> names = hidden.stream()
                .map(id -> {
                    ServerPlayer online = self.server.getPlayerList().getPlayer(id);
                    return online != null ? online.getGameProfile().getName() : id.toString();
                })
                .sorted()
                .toList();
        ctx.getSource().sendSuccess(() -> Component.literal("Hidden players: " + String.join(", ", names)), false);
        return hidden.size();
    }

    private static String namesOf(List<ServerPlayer> players) {
        return players.stream().map(p -> p.getGameProfile().getName())
                .sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}
