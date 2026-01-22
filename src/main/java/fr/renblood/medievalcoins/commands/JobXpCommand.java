package fr.renblood.medievalcoins.commands;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.renblood.medievalcoins.network.ApiClient;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber
public class JobXpCommand {

    private static final List<String> JOBS = Arrays.asList(
            "lumberjack", "naval_architect", "artisan", "carpenter", "miner",
            "blacksmith", "glassmaker", "mason", "farmer", "breeder",
            "fisherman", "innkeeper", "guard", "merchant", "transporter",
            "explorer", "bestiary", "banker", "politician", "builder"
    );

    private static final SuggestionProvider<CommandSourceStack> JOB_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(JOBS, builder);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> d = evt.getDispatcher();

        d.register(Commands.literal("jobxp")
                .requires(src -> src.hasPermission(2)) // OP niveau 2
                .then(Commands.argument("action", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.asList("add", "set", "remove"), builder))
                        .then(Commands.argument("job", StringArgumentType.word())
                                .suggests(JOB_SUGGESTIONS)
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        // Cas 1 : Pas de joueur spécifié -> Soi-même
                                        .executes(c -> execute(c.getSource(),
                                                StringArgumentType.getString(c, "action"),
                                                StringArgumentType.getString(c, "job"),
                                                IntegerArgumentType.getInteger(c, "amount"),
                                                c.getSource().getPlayerOrException()))
                                        // Cas 2 : Joueur spécifié
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(c -> execute(c.getSource(),
                                                        StringArgumentType.getString(c, "action"),
                                                        StringArgumentType.getString(c, "job"),
                                                        IntegerArgumentType.getInteger(c, "amount"),
                                                        EntityArgument.getPlayer(c, "target")))
                                        )
                                )
                        )
                )
        );
    }

    private static int execute(CommandSourceStack source, String action, String job, int amount, ServerPlayer target) {
        if (!JOBS.contains(job)) {
            source.sendFailure(Component.literal("❌ Métier inconnu : " + job));
            return 0;
        }

        new Thread(() -> {
            try {
                String uuid = target.getGameProfile().getId().toString();
                JsonObject response = ApiClient.manageJobXp(uuid, action, job, amount);

                String jobName = response.get("job").getAsString();
                int newXp = response.get("new_xp").getAsInt();
                int level = response.get("level").getAsInt();

                source.getServer().execute(() -> {
                    // Message pour l'admin
                    source.sendSuccess(() -> Component.literal(
                            String.format("✅ %s XP %s pour %s (Job: %s). Nouvel XP: %d (Niveau %d)",
                                    action.toUpperCase(), amount, target.getName().getString(), jobName, newXp, level)
                    ), true);

                    // Feedback visuel pour le joueur cible (Action Bar)
                    target.sendSystemMessage(Component.literal(
                            String.format("§6[Métier] §e%s : §bXP %d §7(Niveau %d)", jobName, newXp, level)
                    ), true); // true = action bar
                });

            } catch (Exception e) {
                source.getServer().execute(() ->
                        source.sendFailure(Component.literal("❌ Erreur API : " + e.getMessage()))
                );
            }
        }).start();

        return 1;
    }
}
