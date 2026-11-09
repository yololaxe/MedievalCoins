package fr.renblood.medievalcoins.land;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkHooks;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LandCommands {
    private static final Pattern HEX_COLOR = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    private LandCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("land")
                .then(Commands.literal("reload")
                        .requires(LandConfig::canAdmin)
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("map")
                        .executes(context -> openMap(context.getSource())))
                .then(Commands.literal("create")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> create(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("delete")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                .executes(context -> delete(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("add-current-chunk")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("plotId", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                .executes(context -> addCurrentChunk(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "plotId"),
                                        false))
                                .then(Commands.literal("force")
                                        .executes(context -> addCurrentChunk(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "plotId"),
                                                true)))))
                            .then(Commands.literal("claim")
                                .requires(LandConfig::canAdmin)
                                .then(Commands.argument("plotId", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                    .executes(context -> addCurrentChunk(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "plotId"),
                                        false))))
                .then(Commands.literal("remove-current-chunk")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("plotId", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                .executes(context -> removeCurrentChunk(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "plotId")))))
                            .then(Commands.literal("unclaim")
                                .requires(LandConfig::canAdmin)
                                .then(Commands.argument("plotId", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                    .executes(context -> removeCurrentChunk(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "plotId")))))
                .then(Commands.literal("set-owner")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("plotId", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                .then(Commands.argument("ownerType", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(ownerTypes(), builder))
                                        .then(Commands.argument("ownerName", StringArgumentType.greedyString())
                                                .executes(context -> setOwner(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "plotId"),
                                                        StringArgumentType.getString(context, "ownerType"),
                                                        StringArgumentType.getString(context, "ownerName")))))))
                .then(Commands.literal("set-city")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("plotId", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                .then(Commands.argument("city", StringArgumentType.greedyString())
                                        .executes(context -> setCity(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "plotId"),
                                                StringArgumentType.getString(context, "city"))))))
                .then(Commands.literal("set-color")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("plotId", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                .then(Commands.argument("hexColor", StringArgumentType.word())
                                        .executes(context -> setColor(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "plotId"),
                                                StringArgumentType.getString(context, "hexColor"))))))
                .then(Commands.literal("set-description")
                        .requires(LandConfig::canAdmin)
                        .then(Commands.argument("plotId", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(plotIds(), builder))
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(context -> setDescription(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "plotId"),
                                                StringArgumentType.getString(context, "description"))))))
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.argument("city", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(LandRegistry.getCities(), builder))
                                .executes(context -> list(context.getSource(), StringArgumentType.getString(context, "city"))))));
    }

    private static int reload(CommandSourceStack source) {
        LandConfig.reload();
        LandPlayerTracker.clear();
        int count = LandStorage.reloadRegistry();
        source.sendSuccess(() -> Component.literal("Cadastre recharge : " + count
                + " parcelle(s). Configuration land_config.json rechargee."), true);
        return count;
    }

    private static int openMap(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }
        if (!LandConfig.get().isEnableLandSystem()) {
            source.sendFailure(Component.literal("Le systeme de cadastre est desactive dans land_config.json."));
            return 0;
        }

        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new LandMapMenu(containerId, inventory, player),
                        Component.literal("Cadastre local")),
                LandMapMenu::writeOpenData);
        return 1;
    }

    private static int create(CommandSourceStack source, String id, String name) {
        List<LandPlot> plots = LandStorage.load();
        if (findPlot(plots, id).isPresent()) {
            source.sendFailure(Component.literal("Une parcelle existe deja avec l'id : " + id));
            return 0;
        }

        plots.add(new LandPlot(id, name));
        return save(source, plots, "Parcelle creee : " + id + " (" + name + ").");
    }

    private static int delete(CommandSourceStack source, String id) {
        List<LandPlot> plots = LandStorage.load();
        boolean removed = plots.removeIf(plot -> plot.getId().equalsIgnoreCase(id));
        if (!removed) {
            source.sendFailure(Component.literal("Parcelle introuvable : " + id));
            return 0;
        }

        return save(source, plots, "Parcelle supprimee : " + id + ".");
    }

    private static int addCurrentChunk(CommandSourceStack source, String plotId, boolean force) {
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        List<LandPlot> plots = LandStorage.load();
        Optional<LandPlot> target = findPlot(plots, plotId);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Parcelle introuvable : " + plotId));
            return 0;
        }

        String dimension = player.level().dimension().location().toString();
        ChunkPos chunk = player.chunkPosition();
        Optional<LandPlot> existing = findPlotAt(plots, dimension, chunk.x, chunk.z);

        if (existing.isPresent() && !existing.get().getId().equalsIgnoreCase(plotId)) {
            if (!force) {
                source.sendFailure(Component.literal("Chunk deja attribue a la parcelle "
                        + existing.get().getId() + ". Utilisez /land add-current-chunk " + plotId + " force pour le deplacer."));
                return 0;
            }
            existing.get().removeChunk(chunk.x, chunk.z);
        }

        LandPlot plot = target.get();
        if (!plot.getDimension().isBlank() && !plot.getDimension().equalsIgnoreCase(dimension)) {
            source.sendFailure(Component.literal("La parcelle " + plotId + " est dans la dimension "
                    + plot.getDimension() + ", pas " + dimension + "."));
            return 0;
        }

        plot.setDimension(dimension);
        plot.addChunk(chunk.x, chunk.z);
        return save(source, plots, "Chunk " + chunk.x + "," + chunk.z + " ajoute a la parcelle " + plotId + ".");
    }

    private static int removeCurrentChunk(CommandSourceStack source, String plotId) {
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        List<LandPlot> plots = LandStorage.load();
        Optional<LandPlot> target = findPlot(plots, plotId);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Parcelle introuvable : " + plotId));
            return 0;
        }

        ChunkPos chunk = player.chunkPosition();
        String dimension = player.level().dimension().location().toString();
        if (!target.get().getDimension().isBlank() && !target.get().getDimension().equalsIgnoreCase(dimension)) {
            source.sendFailure(Component.literal("La parcelle " + plotId + " est dans la dimension "
                    + target.get().getDimension() + ", pas " + dimension + "."));
            return 0;
        }

        if (!target.get().removeChunk(chunk.x, chunk.z)) {
            source.sendFailure(Component.literal("Le chunk " + chunk.x + "," + chunk.z
                    + " n'appartient pas a la parcelle " + plotId + "."));
            return 0;
        }

        return save(source, plots, "Chunk " + chunk.x + "," + chunk.z + " retire de la parcelle " + plotId + ".");
    }

    private static int setOwner(CommandSourceStack source, String plotId, String ownerTypeName, String ownerName) {
        if (!LandOwnerType.isValid(ownerTypeName)) {
            source.sendFailure(Component.literal("ownerType invalide : " + ownerTypeName
                    + ". Valeurs acceptees : " + String.join(", ", ownerTypes())));
            return 0;
        }

        List<LandPlot> plots = LandStorage.load();
        Optional<LandPlot> target = findPlot(plots, plotId);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Parcelle introuvable : " + plotId));
            return 0;
        }

        LandOwnerType ownerType = LandOwnerType.fromJson(ownerTypeName);
        target.get().setOwner(ownerType, ownerName);
        return save(source, plots, "Proprietaire de " + plotId + " defini sur "
                + ownerName + " (" + ownerType.name() + ").");
    }

    private static int setCity(CommandSourceStack source, String plotId, String city) {
        List<LandPlot> plots = LandStorage.load();
        Optional<LandPlot> target = findPlot(plots, plotId);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Parcelle introuvable : " + plotId));
            return 0;
        }

        target.get().setCity(city);
        return save(source, plots, "Ville de " + plotId + " definie sur : " + city + ".");
    }

    private static int setColor(CommandSourceStack source, String plotId, String hexColor) {
        if (!HEX_COLOR.matcher(hexColor).matches()) {
            source.sendFailure(Component.literal("Couleur invalide : " + hexColor + ". Format attendu : 3B82F6."));
            return 0;
        }

        List<LandPlot> plots = LandStorage.load();
        Optional<LandPlot> target = findPlot(plots, plotId);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Parcelle introuvable : " + plotId));
            return 0;
        }

        String normalizedColor = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
        target.get().setColor(normalizedColor.toUpperCase());
        return save(source, plots, "Couleur de " + plotId + " definie sur : " + normalizedColor.toUpperCase() + ".");
    }

    private static int setDescription(CommandSourceStack source, String plotId, String description) {
        List<LandPlot> plots = LandStorage.load();
        Optional<LandPlot> target = findPlot(plots, plotId);
        if (target.isEmpty()) {
            source.sendFailure(Component.literal("Parcelle introuvable : " + plotId));
            return 0;
        }

        target.get().setDescription(description);
        return save(source, plots, "Description de " + plotId + " mise a jour.");
    }

    private static int info(CommandSourceStack source) {
        ServerPlayer player = getPlayer(source);
        if (player == null) {
            return 0;
        }

        ChunkPos chunk = player.chunkPosition();
        return LandRegistry.findAt(player.level().dimension(), chunk.x, chunk.z)
                .map(plot -> sendPlotInfo(source, plot, chunk))
                .orElseGet(() -> {
                    source.sendFailure(Component.literal("Aucune parcelle sur ce chunk ("
                            + player.level().dimension().location() + " " + chunk.x + "," + chunk.z + ")."));
                    return 0;
                });
    }

    private static int list(CommandSourceStack source, String city) {
        List<LandPlot> plots = city == null || city.isBlank()
                ? LandRegistry.getPlots()
                : LandRegistry.getPlotsByCity(city);

        if (plots.isEmpty()) {
            source.sendFailure(Component.literal(city == null || city.isBlank()
                    ? "Aucune parcelle chargee."
                    : "Aucune parcelle chargee pour la ville : " + city));
            return 0;
        }

        String title = city == null || city.isBlank()
                ? "Parcelles chargees (" + plots.size() + ")"
                : "Parcelles de " + city + " (" + plots.size() + ")";
        source.sendSuccess(() -> Component.literal("=== " + title + " ===").withStyle(ChatFormatting.GOLD), false);

        for (LandPlot plot : plots) {
            source.sendSuccess(() -> Component.literal("- " + plot.getId() + " | " + plot.getDisplayName()
                    + " | " + plot.getCity()
                    + " | " + plot.getDimension()
                    + " | " + plot.getChunks().size() + " chunk(s)").withStyle(ChatFormatting.GRAY), false);
        }

        return plots.size();
    }

    private static int sendPlotInfo(CommandSourceStack source, LandPlot plot, ChunkPos chunk) {
        source.sendSuccess(() -> Component.literal("=== Parcelle : " + plot.getDisplayName() + " ===")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("ID : " + plot.getId()), false);
        source.sendSuccess(() -> Component.literal("Ville : " + valueOrDash(plot.getCity())), false);
        source.sendSuccess(() -> Component.literal("Dimension : " + plot.getDimension()), false);
        source.sendSuccess(() -> Component.literal("Chunk : " + chunk.x + "," + chunk.z), false);
        source.sendSuccess(() -> Component.literal("Proprietaire : " + valueOrDash(plot.getOwner())
                + " (" + plot.getOwnerType().name().toLowerCase() + ")"), false);
        source.sendSuccess(() -> Component.literal("Couleur : " + valueOrDash(plot.getColor())), false);
        if (!plot.getDescription().isBlank()) {
            source.sendSuccess(() -> Component.literal(plot.getDescription()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Cette commande doit etre executee par un joueur."));
            return null;
        }
    }

    private static Optional<LandPlot> findPlot(List<LandPlot> plots, String id) {
        return plots.stream()
                .filter(plot -> plot.getId().equalsIgnoreCase(id))
                .findFirst();
    }

    private static Optional<LandPlot> findPlotAt(List<LandPlot> plots, String dimension, int chunkX, int chunkZ) {
        return plots.stream()
                .filter(plot -> plot.getDimension().equalsIgnoreCase(dimension))
                .filter(plot -> plot.hasChunk(chunkX, chunkZ))
                .findFirst();
    }

    private static int save(CommandSourceStack source, List<LandPlot> plots, String message) {
        try {
            LandStorage.saveAndReload(plots);
            source.sendSuccess(() -> Component.literal(message), true);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Impossible de sauvegarder lands.json : " + e.getMessage()));
            return 0;
        }
    }

    private static List<String> plotIds() {
        return LandRegistry.getPlots().stream()
                .map(LandPlot::getId)
                .toList();
    }

    private static List<String> ownerTypes() {
        return Arrays.stream(LandOwnerType.values())
                .map(Enum::name)
                .toList();
    }
}
