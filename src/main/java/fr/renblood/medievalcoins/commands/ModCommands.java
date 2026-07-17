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
import fr.renblood.medievalcoins.land.LandCommands;
import fr.renblood.medievalcoins.network.ApiClient;
import fr.renblood.medievalcoins.network.PlayerCache;
import fr.renblood.medievalcoins.market.command.MarketCommands;
import fr.renblood.medievalcoins.market.service.MarketPermissions;
import fr.renblood.medievalcoins.tutorial.TutorialManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
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

        LandCommands.register(dispatcher);

        dispatcher.register(Commands.literal("mc")
                .executes(c -> showPlayerHelp(c.getSource()))
                .then(Commands.literal("help").executes(c -> showPlayerHelp(c.getSource())))
                .then(Commands.literal("status").executes(c -> showPlayerStatus(c.getSource())))
                .then(tutoCommands())
                .then(Commands.literal("ability")
                        .executes(c -> showAbilityHelp(c.getSource()))
                        .then(Commands.literal("help").executes(c -> showAbilityHelp(c.getSource()))))
                .then(Commands.literal("heal")
                        .requires(ModCommands::canUseAdmin)
                        .executes(c -> healPlayer(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> healPlayer(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("feed")
                        .requires(ModCommands::canUseAdmin)
                        .executes(c -> feedPlayer(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> feedPlayer(c.getSource(), EntityArgument.getPlayer(c, "target")))))
                .then(Commands.literal("fly-speed")
                        .requires(ModCommands::canUseAdmin)
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes(c -> setFlySpeed(
                                        c.getSource(),
                                        c.getSource().getPlayerOrException(),
                                        DoubleArgumentType.getDouble(c, "speed")))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(c -> setFlySpeed(
                                                c.getSource(),
                                                EntityArgument.getPlayer(c, "target"),
                                                DoubleArgumentType.getDouble(c, "speed"))))))
                .then(Commands.literal("admin")
                        .requires(ModCommands::canUseAdmin)
                        .executes(c -> showAdminHelp(c.getSource()))
                        .then(Commands.literal("help").executes(c -> showAdminHelp(c.getSource())))
                        .then(Commands.literal("panel").executes(c -> showAdminPanel(c.getSource())))
                        .then(Commands.literal("status").executes(c -> showAdminStatus(c.getSource())))
                        .then(Commands.literal("api")
                                .then(Commands.literal("status").executes(c -> showAdminStatus(c.getSource())))
                                .then(Commands.literal("refresh-cache").executes(c -> refreshPlayers(c.getSource()))))
                        .then(Commands.literal("reload").executes(c -> reload(c.getSource())))
                        .then(MarketCommands.command())
                        .then(playerAdminCommands())
                        .then(configCommands())
                        .then(jobCommands())
                        .then(statsCommands())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tutoCommands() {
        return Commands.literal("tuto")
                .executes(c -> TutorialManager.status(c.getSource().getPlayerOrException()))
                .then(Commands.literal("guide")
                        .executes(c -> TutorialManager.toggleGuide(c.getSource().getPlayerOrException())))
                .then(Commands.literal("confirm-counter-break")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(c -> TutorialManager.confirmCounterBreak(
                                                        c.getSource().getPlayerOrException(),
                                                        new BlockPos(
                                                                IntegerArgumentType.getInteger(c, "x"),
                                                                IntegerArgumentType.getInteger(c, "y"),
                                                                IntegerArgumentType.getInteger(c, "z"))))))))
                .then(Commands.literal("begin")
                        .requires(ModCommands::canUseAdmin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> TutorialManager.begin(EntityArgument.getPlayer(c, "player"), true))))
                .then(Commands.literal("next")
                        .requires(ModCommands::canUseAdmin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> TutorialManager.skipNext(EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("end")
                        .requires(ModCommands::canUseAdmin)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> TutorialManager.end(EntityArgument.getPlayer(c, "player")))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> playerAdminCommands() {
        return Commands.literal("player")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(c -> showPlayerInfo(c.getSource(), EntityArgument.getPlayer(c, "target")))
                        .then(Commands.literal("info")
                                .executes(c -> showPlayerInfo(c.getSource(), EntityArgument.getPlayer(c, "target"))))
                        .then(Commands.literal("heal")
                                .executes(c -> healPlayer(c.getSource(), EntityArgument.getPlayer(c, "target"))))
                        .then(Commands.literal("feed")
                                .executes(c -> feedPlayer(c.getSource(), EntityArgument.getPlayer(c, "target"))))
                        .then(Commands.literal("teleport-here")
                                .executes(c -> teleportHere(c.getSource(), EntityArgument.getPlayer(c, "target"))))
                        .then(Commands.literal("refresh-stats")
                                .executes(c -> refreshStats(c.getSource(), EntityArgument.getPlayer(c, "target")))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> configCommands() {
        return Commands.literal("config")
                .then(Commands.literal("api")
                        .then(Commands.literal("url")
                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                        .executes(c -> setApiUrl(c.getSource(), StringArgumentType.getString(c, "url")))))
                        .then(Commands.literal("key")
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .executes(c -> setApiKey(c.getSource(), StringArgumentType.getString(c, "key")))))
                        .then(Commands.literal("ping").executes(c -> pingApi(c.getSource()))))
                .then(Commands.literal("refresh-cache").executes(c -> refreshPlayers(c.getSource())))
                .then(Commands.literal("debug")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(c -> setDebugMode(c.getSource(), BoolArgumentType.getBool(c, "enabled")))))
                .then(Commands.literal("time")
                        .then(Commands.literal("day-length")
                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1, 100.0))
                                        .executes(c -> setDayLength(c.getSource(), DoubleArgumentType.getDouble(c, "multiplier")))))
                        .then(Commands.literal("sleep-percentage")
                                .then(Commands.argument("percentage", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(c -> setSleepPercentage(c.getSource(), DoubleArgumentType.getDouble(c, "percentage")))))
                        .then(Commands.literal("sleep-duration")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                                        .executes(c -> setSleepDuration(c.getSource(), IntegerArgumentType.getInteger(c, "seconds"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> jobCommands() {
        return Commands.literal("job")
                .then(Commands.literal("xp")
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.asList("add", "set", "remove"), b))
                                .then(Commands.argument("job", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(JOBS, b))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("target", EntityArgument.player())
                                                        .executes(c -> manageJobXp(
                                                                c.getSource(),
                                                                StringArgumentType.getString(c, "action"),
                                                                StringArgumentType.getString(c, "job"),
                                                                IntegerArgumentType.getInteger(c, "amount"),
                                                                EntityArgument.getPlayer(c, "target"))))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> statsCommands() {
        return Commands.literal("stats")
                .then(Commands.literal("refresh")
                        .then(Commands.literal("all").executes(c -> refreshAllStats(c.getSource())))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(c -> refreshStats(c.getSource(), EntityArgument.getPlayer(c, "target")))));
    }

    private static int showPlayerHelp(CommandSourceStack src) {
        sendHeader(src, "Medieval Coins - Commandes joueur");
        sendLine(src, "/mc quest", "Ouvrir le journal de quetes.");
        sendLine(src, "/mc tuto", "Afficher l'etat du tutoriel Nagaki.");
        sendLine(src, "/mc quest finish <quete> <admin>", "Demander la validation manuelle d'une quete.");
        sendLine(src, "/mc ability <capacite>", "Activer ou desactiver une capacite.");
        sendLine(src, "/mc site", "Afficher le lien du site.");
        sendLine(src, "/mc status", "Afficher votre etat et les capacites disponibles.");
        if (canUseAdmin(src)) {
            sendLine(src, "/mc admin help", "Afficher les commandes d'administration.");
        }
        return 1;
    }

    private static int showAdminHelp(CommandSourceStack src) {
        sendHeader(src, "Medieval Coins - Administration");
        sendLine(src, "/mc admin status", "Etat du serveur, de l'API et du cache.");
        sendLine(src, "/mc admin panel", "Ouvrir le tableau de bord admin.");
        sendLine(src, "/mc admin player <joueur>", "Informations et actions sur un joueur.");
        sendLine(src, "/mc admin quest ...", "Validation et attribution des quetes.");
        sendLine(src, "/mc admin job xp <add|set|remove> <metier> <montant> <joueur>", "Gerer l'XP metier.");
        sendLine(src, "/mc admin stats refresh <joueur|all>", "Rafraichir les statistiques.");
        sendLine(src, "/mc admin divine <status|next>", "Gerer la session divine.");
        sendLine(src, "/mc admin config ...", "Configurer l'API, le temps et les capacites.");
        sendLine(src, "/mc admin config time sleep-duration <secondes>", "Regler le temps minimum passe au lit.");
        sendLine(src, "/mc admin reload", "Recharger la configuration et les statistiques.");
        sendLine(src, "/mc admin market <reload|sync|info|debug>", "Gerer les prix et comptoirs marchands.");
        sendLine(src, "/mc tuto begin/next/end <joueur>", "Lancer, avancer ou terminer le tutoriel Nagaki.");
        sendLine(src, "/mc heal [joueur]", "Soigner completement un joueur.");
        sendLine(src, "/mc feed [joueur]", "Restaurer la faim et la saturation.");
        sendLine(src, "/mc fly-speed <0.0-1.0> [joueur]", "Modifier la vitesse de vol.");
        return 1;
    }

    private static int showAbilityHelp(CommandSourceStack src) {
        sendHeader(src, "Capacites");
        sendLine(src, "/mc ability magnet [portee]", "Attirer les objets proches.");
        sendLine(src, "/mc ability nofall", "Activer ou desactiver la protection de chute.");
        sendLine(src, "/mc ability jump-boost", "Activer ou desactiver le saut ameliore.");
        sendLine(src, "/mc ability vanish", "Activer ou desactiver l'invisibilite.");
        sendLine(src, "/mc ability torch", "Activer ou desactiver la torche speciale.");
        sendLine(src, "/mc ability fertilize", "Activer ou desactiver la fertilisation.");
        sendLine(src, "/mc ability unbark", "Ecorcer les buches tenues.");
        sendLine(src, "/mc ability firecamp", "Installer un campement temporaire.");
        return 1;
    }

    private static int showAdminPanel(CommandSourceStack src) {
        sendHeader(src, "Tableau de bord admin");
        sendAdminAction(src, "[Etat API et cache]", "/mc admin status");
        sendAdminAction(src, "[Synchroniser le marche]", "/mc admin market sync");
        sendAdminAction(src, "[Recharger le marche]", "/mc admin market reload");
        sendAdminAction(src, "[Rafraichir les joueurs]", "/mc admin api refresh-cache");
        sendAdminAction(src, "[Aide quetes]", "/mc admin quest");
        src.sendSuccess(() -> Component.literal(
                "La gestion visuelle complete PNJ/quetes necessite les endpoints backend d'ecriture correspondants.")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static void sendAdminAction(CommandSourceStack src, String label, String command) {
        src.sendSuccess(() -> Component.literal(label).withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))), false);
    }

    private static int showPlayerStatus(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Cette commande doit etre executee en jeu."));
            return 0;
        }
        sendHeader(src, "Votre statut");
        src.sendSuccess(() -> Component.literal("Joueur : " + player.getName().getString()), false);
        src.sendSuccess(() -> Component.literal(String.format("Vie : %.1f/%.1f | Nourriture : %d/20",
                player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel())), false);
        src.sendSuccess(() -> Component.literal("Position : " + formatPosition(player)), false);
        src.sendSuccess(() -> Component.literal("Utilisez /mc ability <capacite> pour gerer vos capacites."), false);
        return 1;
    }

    private static int showAdminStatus(CommandSourceStack src) {
        ModConfig cfg = ModConfig.load();
        long started = System.nanoTime();
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
            boolean apiOnline = HttpHelper.ping(cfg.apiUrl);
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            src.getServer().execute(() -> {
                sendHeader(src, "Etat Medieval Coins");
                src.sendSuccess(() -> Component.literal("API : " + (apiOnline ? "en ligne" : "indisponible")
                        + " (" + latencyMs + " ms)"), false);
                src.sendSuccess(() -> Component.literal("Joueurs connectes : "
                        + src.getServer().getPlayerCount() + "/" + src.getServer().getMaxPlayers()), false);
                src.sendSuccess(() -> Component.literal("Joueurs en cache : " + PlayerCache.getPlayers().size()), false);
                src.sendSuccess(() -> Component.literal("Debug : " + (MedievalCoin.DEBUG_MODE ? "actif" : "inactif")
                        + " | Jour x" + TimeManager.dayLengthMultiplier
                        + " | Sommeil " + Math.round(TimeManager.sleepPercentage * 100.0) + "% pendant "
                        + TimeManager.sleepDurationSeconds + "s"), false);
            });
        });
        return 1;
    }

    private static int showPlayerInfo(CommandSourceStack src, ServerPlayer target) {
        sendHeader(src, "Joueur " + target.getName().getString());
        src.sendSuccess(() -> Component.literal("UUID : " + target.getStringUUID()), false);
        src.sendSuccess(() -> Component.literal("Position : " + formatPosition(target)
                + " | Dimension : " + target.level().dimension().location()), false);
        src.sendSuccess(() -> Component.literal(String.format("Vie : %.1f/%.1f | Nourriture : %d/20 | XP : %d",
                target.getHealth(), target.getMaxHealth(), target.getFoodData().getFoodLevel(), target.experienceLevel)), false);
        src.sendSuccess(() -> Component.literal("Mode de jeu : " + target.gameMode.getGameModeForPlayer().getName()), false);
        return 1;
    }

    private static int healPlayer(CommandSourceStack src, ServerPlayer target) {
        target.setHealth(target.getMaxHealth());
        src.sendSuccess(() -> Component.literal(target.getName().getString() + " a ete soigne."), true);
        return 1;
    }

    private static int feedPlayer(CommandSourceStack src, ServerPlayer target) {
        target.getFoodData().setFoodLevel(20);
        target.getFoodData().setSaturation(20.0F);
        src.sendSuccess(() -> Component.literal(target.getName().getString() + " a ete nourri."), true);
        return 1;
    }

    private static int setFlySpeed(CommandSourceStack src, ServerPlayer target, double speed) {
        target.getAbilities().setFlyingSpeed((float) speed);
        target.onUpdateAbilities();
        src.sendSuccess(() -> Component.literal("Vitesse de vol de " + target.getName().getString()
                + " reglee sur " + speed + "."), true);
        return 1;
    }

    private static int teleportHere(CommandSourceStack src, ServerPlayer target) {
        if (!(src.getEntity() instanceof ServerPlayer admin)) {
            src.sendFailure(Component.literal("Cette action doit etre executee en jeu."));
            return 0;
        }
        BackCommand.remember(target);
        target.teleportTo(admin.serverLevel(), admin.getX(), admin.getY(), admin.getZ(), admin.getYRot(), admin.getXRot());
        src.sendSuccess(() -> Component.literal(target.getName().getString() + " a ete teleporte vers vous."), true);
        return 1;
    }

    private static int reload(CommandSourceStack src) {
        ModConfig.reload();
        int refreshed = refreshAllStats(src);
        refreshPlayers(src);
        src.sendSuccess(() -> Component.literal("Configuration rechargee. Rafraichissement lance pour "
                + refreshed + " joueur(s)."), true);
        return 1;
    }

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
        fr.renblood.medievalcoins.network.ApiExecutor.execute(() -> {
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
        });
        return 1;
    }

    private static int setDebugMode(CommandSourceStack src, boolean enabled) {
        MedievalCoin.DEBUG_MODE = enabled;
        src.sendSuccess(() -> Component.literal("Mode debug " + (enabled ? "active" : "desactive") + "."), true);
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

    private static int setSleepDuration(CommandSourceStack src, int seconds) {
        TimeManager.sleepDurationSeconds = seconds;
        src.sendSuccess(() -> Component.translatable("message.medieval_coins.config_sleep_duration", seconds), true);
        return 1;
    }

    private static int manageJobXp(CommandSourceStack src, String action, String job, int amount, ServerPlayer target) {
        if (!JOBS.contains(job)) {
            src.sendFailure(Component.translatable("message.medieval_coins.job_unknown", job));
            return 0;
        }
        if (!List.of("add", "set", "remove").contains(action)) {
            src.sendFailure(Component.literal("Action inconnue : " + action + ". Utilisez add, set ou remove."));
            return 0;
        }
        if (action.equals("add")) MedievalCoinsAPI.addJobXp(target, job, amount);
        else if (action.equals("set")) MedievalCoinsAPI.setJobXp(target, job, amount);
        else MedievalCoinsAPI.removeJobXp(target, job, amount);
        src.sendSuccess(() -> Component.literal("Modification de l'XP " + job + " lancee pour "
                + target.getName().getString() + "."), true);
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

    private static String formatPosition(ServerPlayer player) {
        return player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ();
    }

    private static void sendHeader(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal("=== " + text + " ===").withStyle(ChatFormatting.GOLD), false);
    }

    private static void sendLine(CommandSourceStack src, String command, String description) {
        src.sendSuccess(() -> Component.literal(command).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY)), false);
    }

    private static boolean canUseAdmin(CommandSourceStack source) {
        return MarketPermissions.isAdmin(source);
    }
}
