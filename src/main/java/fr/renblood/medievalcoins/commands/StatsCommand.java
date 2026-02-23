package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.renblood.medievalcoins.events.PlayerStatsHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

@Mod.EventBusSubscriber
public class StatsCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("statsrefresh")
                .requires(src -> src.hasPermission(2)) // OP niveau 2 requis

                // /statsrefresh (soi-même)
                .executes(c -> {
                    if (c.getSource().getEntity() instanceof ServerPlayer player) {
                        PlayerStatsHandler.refreshPlayerStats(player);
                        c.getSource().sendSuccess(() -> Component.literal("✅ Vos stats ont été rafraîchies."), true);
                        return 1;
                    } else {
                        c.getSource().sendFailure(Component.literal("❌ Commande réservée aux joueurs."));
                        return 0;
                    }
                })

                // /statsrefresh all
                .then(Commands.literal("all")
                        .executes(c -> {
                            int count = 0;
                            for (ServerPlayer player : c.getSource().getServer().getPlayerList().getPlayers()) {
                                PlayerStatsHandler.refreshPlayerStats(player);
                                count++;
                            }
                            final int finalCount = count;
                            c.getSource().sendSuccess(() -> Component.literal("✅ Refresh lancé pour " + finalCount + " joueurs."), true);
                            return count;
                        })
                )

                // /statsrefresh <pseudo>
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "target");
                            PlayerStatsHandler.refreshPlayerStats(target);
                            c.getSource().sendSuccess(() -> Component.literal("✅ Stats rafraîchies pour " + target.getName().getString()), true);
                            return 1;
                        })
                )
        );
    }
}
