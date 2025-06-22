package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.renblood.medievalcoins.client.model.PlayerModel;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber
public class ConfigCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("mcconfig")
                // /mcconfig set apiurl <url>
                .then(Commands.literal("set")
                        .then(Commands.literal("apiurl")
                                .then(Commands.argument("url", StringArgumentType.string())
                                        .executes(c -> {
                                            String url = StringArgumentType.getString(c, "url");
                                            ModConfig cfg = ModConfig.load();
                                            cfg.apiUrl = url;
                                            ModConfig.save();
                                            // ici : on appelle la méthode statique, sans "new"
                                            c.getSource().sendSuccess(
                                                    () -> Component.literal("API URL mise à jour : " + url),
                                                    true
                                            );
                                            return 1;
                                        }))))
                // /mcconfig set apikey <clé>
                .then(Commands.literal("set")
                        .then(Commands.literal("apikey")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .executes(c -> {
                                            String key = StringArgumentType.getString(c, "key");
                                            ModConfig cfg = ModConfig.load();
                                            cfg.apiKey = key;
                                            ModConfig.save();
                                            c.getSource().sendSuccess(
                                                    () -> Component.literal("API Key enregistrée."),
                                                    true
                                            );
                                            return 1;
                                        }))))
                // /mcconfig ping
                .then(Commands.literal("ping")
                        .executes(c -> {
                            ModConfig cfg = ModConfig.load();
                            boolean ok = HttpHelper.ping(cfg.apiUrl);
                            c.getSource().sendSuccess(
                                    ok
                                            ? () ->Component.literal("Ping réussi ! (" + cfg.apiUrl + ")")
                                            : () -> Component.literal("Échec du ping sur : " + cfg.apiUrl),
                                    true
                            );
                            return ok ? 1 : 0;
                        }))
                .then(Commands.literal("refresh")
                        .executes(c -> {
                            CommandSourceStack src = c.getSource();
                            try {
                                String rank = ModConfig.load().apiKey.isEmpty() ? "citoyen" : "admin";
                                List<PlayerModel> players = ApiClient.getPlayers(rank);
                                // on met à jour le cache
                                PlayerCache.setPlayers(players);
                                src.sendSuccess(
                                        () -> Component.literal("🔄 Liste des joueurs rafraîchie : " + players.size() + " joueurs."),
                                        true
                                );
                                return 1;
                            } catch (Exception e) {
                                src.sendFailure(
                                        Component.literal("❌ Échec du refresh des joueurs : " + e.getMessage())
                                );
                                return 0;
                            }
                        })
                )
        );
    }
}
