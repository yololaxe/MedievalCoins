package fr.renblood.medievalcoins.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.api.MedievalCoinsAPI;
import fr.renblood.medievalcoins.config.ModConfig;
import fr.renblood.medievalcoins.events.PlayerStatsHandler;
import fr.renblood.medievalcoins.events.TimeManager;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.tree.*;
import fr.renblood.medievalcoins.tree.fertilize.FertilizeCommand;
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

@Mod.EventBusSubscriber(modid = MedievalCoin.MODID)
public class ModCommands {

    private static final List<String> JOBS = Arrays.asList(
            "lumberjack", "naval_architect", "artisan", "carpenter", "miner",
            "blacksmith", "glassmaker", "mason", "farmer", "breeder",
            "fisherman", "innkeeper", "guard", "merchant", "transporter",
            "explorer", "bestiary", "banker", "politician", "builder"
    );

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("mc")
            // --- SOUS-COMMANDE : CONFIG ---
            .then(Commands.literal("config")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("api")
                    .then(Commands.literal("url")
                        .then(Commands.argument("url", StringArgumentType.greedyString())
                            .executes(c -> setApiUrl(c.getSource(), StringArgumentType.getString(c, "url")))))
                    .then(Commands.literal("key")
                        .then(Commands.argument("key", StringArgumentType.string())
                            .executes(c -> setApiKey(c.getSource(), StringArgumentType.getString(c, "key")))))
                    .then(Commands.literal("ping")
                        .executes(c -> pingApi(c.getSource())))
                )
                .then(Commands.literal("refresh")
                    .executes(c -> refreshPlayers(c.getSource())))
                .then(Commands.literal("debug")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(c -> setDebugMode(c.getSource(), BoolArgumentType.getBool(c, "enabled")))))
                .then(Commands.literal("time")
                    .then(Commands.literal("daylength")
                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1, 100.0))
                            .executes(c -> setDayLength(c.getSource(), DoubleArgumentType.getDouble(c, "multiplier")))))
                    .then(Commands.literal("sleep_percentage")
                        .then(Commands.argument("percentage", DoubleArgumentType.doubleArg(0.0, 100.0))
                            .executes(c -> setSleepPercentage(c.getSource(), DoubleArgumentType.getDouble(c, "percentage")))))
                )
            )

            // --- SOUS-COMMANDE : JOB ---
            .then(Commands.literal("job")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("xp")
                    .then(Commands.argument("action", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.asList("add", "set", "remove"), b))
                        .then(Commands.argument("job", StringArgumentType.word())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(JOBS, b))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(c -> manageJobXp(c.getSource(), StringArgumentType.getString(c, "action"), StringArgumentType.getString(c, "job"), IntegerArgumentType.getInteger(c, "amount"), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                    .executes(c -> manageJobXp(c.getSource(), StringArgumentType.getString(c, "action"), StringArgumentType.getString(c, "job"), IntegerArgumentType.getInteger(c, "amount"), EntityArgument.getPlayer(c, "target")))
                                )
                            )
                        )
                    )
                )
            )

            // --- SOUS-COMMANDE : STATS ---
            .then(Commands.literal("stats")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("refresh")
                    .executes(c -> refreshStats(c.getSource(), c.getSource().getPlayerOrException()))
                    .then(Commands.literal("all")
                        .executes(c -> refreshAllStats(c.getSource())))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> refreshStats(c.getSource(), EntityArgument.getPlayer(c, "target")))
                    )
                )
            )
        );
    }

    // --- IMPLEMENTATIONS ---

    private static int setApiUrl(CommandSourceStack src, String url) {
        ModConfig cfg = ModConfig.load();
        cfg.apiUrl = url;
        ModConfig.save();
        src.sendSuccess(() -> Component.translatable("message.medieval_coins.config_url", url), true);
        return 1;
    }

    private static int setApiKey(CommandSourceStack src, String key) {
        ModConfig cfg = ModConfig.load();
        cfg.apiKey = key;
        ModConfig.save();
        src.sendSuccess(() -> Component.translatable("message.medieval_coins.config_key"), true);
        return 1;
    }

    private static int pingApi(CommandSourceStack src) {
        ModConfig cfg = ModConfig.load();
        boolean ok = HttpHelper.ping(cfg.apiUrl);
        if (ok) src.sendSuccess(() -> Component.translatable("message.medieval_coins.config_ping_success", cfg.apiUrl), true);
        else src.sendFailure(Component.translatable("message.medieval_coins.config_ping_fail", cfg.apiUrl));
        return ok ? 1 : 0;
    }

    private static int refreshPlayers(CommandSourceStack src) {
        new Thread(() -> {
            try {
                String rank = ModConfig.load().apiKey.isEmpty() ? "citoyen" : "admin";
                var players = ApiClient.getPlayers(rank);
                PlayerCache.setPlayers(players);
                src.getServer().execute(() -> 
                    src.sendSuccess(() -> Component.translatable("message.medieval_coins.config_refresh_success", players.size()), true)
                );
            } catch (Exception e) {
                src.getServer().execute(() -> 
                    src.sendFailure(Component.translatable("message.medieval_coins.config_refresh_fail", e.getMessage()))
                );
            }
        }).start();
        return 1;
    }
    
    private static int setDebugMode(CommandSourceStack src, boolean enabled) {
        MedievalCoin.DEBUG_MODE = enabled;
        src.sendSuccess(() -> Component.literal("✅ Debug mode " + (enabled ? "enabled" : "disabled")), true);
        return 1;
    }

    private static int setDayLength(CommandSourceStack src, double mult) {
        TimeManager.dayLengthMultiplier = mult;
        double totalMinutes = 20.0 * mult;
        src.sendSuccess(() -> Component.translatable("message.medieval_coins.config_daylength", mult, totalMinutes), true);
        return 1;
    }

    private static int setSleepPercentage(CommandSourceStack src, double percent) {
        TimeManager.sleepPercentage = percent / 100.0;
        src.sendSuccess(() -> Component.translatable("message.medieval_coins.config_sleep", percent), true);
        return 1;
    }

    private static int manageJobXp(CommandSourceStack src, String action, String job, int amount, ServerPlayer target) {
        if (!JOBS.contains(job)) {
            src.sendFailure(Component.translatable("message.medieval_coins.job_unknown", job));
            return 0;
        }
        // Appel à l'API existante
        if (action.equals("add")) MedievalCoinsAPI.addJobXp(target, job, amount);
        else if (action.equals("set")) MedievalCoinsAPI.setJobXp(target, job, amount);
        else if (action.equals("remove")) MedievalCoinsAPI.removeJobXp(target, job, amount);
        
        return 1;
    }

    private static int refreshStats(CommandSourceStack src, ServerPlayer target) {
        PlayerStatsHandler.refreshPlayerStats(target);
        src.sendSuccess(() -> Component.translatable("message.medieval_coins.stats_refreshed_target", target.getName().getString()), true);
        return 1;
    }

    private static int refreshAllStats(CommandSourceStack src) {
        int count = 0;
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            PlayerStatsHandler.refreshPlayerStats(player);
            count++;
        }
        final int finalCount = count;
        src.sendSuccess(() -> Component.translatable("message.medieval_coins.stats_refreshed_all", finalCount), true);
        return count;
    }
}
