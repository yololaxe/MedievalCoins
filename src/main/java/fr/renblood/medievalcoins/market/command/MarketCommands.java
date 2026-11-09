package fr.renblood.medievalcoins.market.command;

import fr.renblood.medievalcoins.market.config.MarketConfig;
import fr.renblood.medievalcoins.market.model.MarketPriceCache;
import fr.renblood.medievalcoins.market.model.MarketSyncResult;
import fr.renblood.medievalcoins.market.service.MarketCacheManager;
import fr.renblood.medievalcoins.market.service.MarketSyncService;
import fr.renblood.medievalcoins.market.service.MerchantCounterLocalStore;
import fr.renblood.medievalcoins.market.service.MerchantCounterSyncService;
import fr.renblood.medievalcoins.market.service.MarketTransactionService;
import fr.renblood.medievalcoins.market.service.PendingMarketTransactionManager;
import fr.renblood.medievalcoins.market.service.MarketPriceService;
import fr.renblood.medievalcoins.market.service.MerchantCounterRegistry;
import fr.renblood.medievalcoins.market.service.MarketReferenceItemService;
import fr.renblood.medievalcoins.market.service.MarketTransactionHistoryCache;
import fr.renblood.medievalcoins.market.service.MarketPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.Comparator;
import java.util.stream.IntStream;
import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import java.util.Locale;

public final class MarketCommands {
    private MarketCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("market")
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
                .then(Commands.literal("sync").executes(context -> sync(context.getSource())))
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("debug").executes(context -> debug(context.getSource())))
                .then(adminCommands())
                .then(cleanupCommands());
    }

    private static final SuggestionProvider<CommandSourceStack> COUNTER_IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(MerchantCounterRegistry.loaded().stream()
                    .map(counter -> counter.getCounterId()), builder);

    private static final SuggestionProvider<CommandSourceStack> COUNTER_ITEMS = (context, builder) -> {
        var counter = MerchantCounterRegistry.findLoaded(StringArgumentType.getString(context, "counterId"));
        if (counter == null) return builder.buildFuture();
        if (counter.isPlayerMarket()) {
            return SharedSuggestionProvider.suggest(IntStream.range(0, MerchantCounterBlockEntity.PLAYER_MARKET_SLOTS)
                    .filter(slot -> !counter.playerMarketStack(slot).isEmpty()).mapToObj(Integer::toString), builder);
        }
        String remaining = builder.getRemainingLowerCase();
        String prefix = remaining.startsWith("\"") ? remaining.substring(1) : remaining;
        counter.getConfiguredItems().stream().filter(item -> item.itemId().startsWith(prefix))
                .forEach(item -> builder.suggest(StringArgumentType.escapeIfRequired(item.itemId()),
                        Component.literal(item.displayName() == null ? item.itemId() : item.displayName())));
        return builder.buildFuture();
    };

    private static LiteralArgumentBuilder<CommandSourceStack> adminCommands() {
        return Commands.literal("admin")
                .requires(MarketPermissions::isAdmin)
                .then(Commands.literal("disable")
                        .then(Commands.argument("counterId", StringArgumentType.word()).suggests(COUNTER_IDS)
                                .executes(context -> setActive(context.getSource(),
                                        StringArgumentType.getString(context, "counterId"), false))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("counterId", StringArgumentType.word()).suggests(COUNTER_IDS)
                                .executes(context -> setActive(context.getSource(),
                                        StringArgumentType.getString(context, "counterId"), true))))
                .then(Commands.literal("owner")
                        .then(Commands.argument("counterId", StringArgumentType.word()).suggests(COUNTER_IDS)
                                .executes(context -> ownerInfo(context.getSource(),
                                        StringArgumentType.getString(context, "counterId")))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("counterId", StringArgumentType.word()).suggests(COUNTER_IDS)
                                .executes(context -> inspect(context.getSource(),
                                        StringArgumentType.getString(context, "counterId")))))
                .then(Commands.literal("official")
                        .then(Commands.argument("counterId", StringArgumentType.word()).suggests(COUNTER_IDS)
                                .executes(context -> makeOfficial(context.getSource(),
                                        StringArgumentType.getString(context, "counterId")))))
                .then(Commands.literal("remove-item")
                        .then(Commands.argument("counterId", StringArgumentType.word()).suggests(COUNTER_IDS)
                                .then(Commands.argument("slotOrItemId", StringArgumentType.string()).suggests(COUNTER_ITEMS)
                                        .executes(context -> removeItem(context.getSource(),
                                                StringArgumentType.getString(context, "counterId"),
                                                StringArgumentType.getString(context, "slotOrItemId"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cleanupCommands() {
        return Commands.literal("cleanup")
                .requires(MarketPermissions::isAdmin)
                .then(Commands.literal("inactive")
                        .executes(context -> listInactive(context.getSource(), MarketConfig.load().inactiveMarketDaysBeforeDisable))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                .executes(context -> listInactive(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "days")))));
    }

    private static int sync(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Synchronisation des comptoirs en cours..."), false);
        MerchantCounterSyncService.syncAll(source.getServer()).thenCombine(MarketTransactionService.retryPending(),
                (result, transactionsSent) -> new Object[]{result, transactionsSent}).thenAccept(values ->
                source.getServer().execute(() -> {
                    var result = (fr.renblood.medievalcoins.market.model.CounterSyncResult) values[0];
                    int transactionsSent = (Integer) values[1];
                    source.sendSuccess(() -> Component.literal("=== Synchronisation des comptoirs ==="), true);
                    source.sendSuccess(() -> Component.literal("Comptoirs recus: " + result.received()), false);
                    source.sendSuccess(() -> Component.literal("Comptoirs mis a jour: " + result.updated()), false);
                    source.sendSuccess(() -> Component.literal("Comptoirs non trouves en jeu: " + result.notFound()), false);
                    source.sendSuccess(() -> Component.literal("Pending envoyes: " + result.pendingSent()), false);
                    source.sendSuccess(() -> Component.literal("Erreurs: " + result.errors()), false);
                    source.sendSuccess(() -> Component.literal("Transactions pending envoyees: " + transactionsSent), false);
                    source.sendSuccess(() -> Component.literal("Transactions encore pending: " + PendingMarketTransactionManager.count()), false);
                    result.errorMessages().stream().limit(3).forEach(message ->
                            source.sendFailure(Component.literal("- " + message)));
                }));
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Chargement des prix du marche en cours..."), false);
        MarketSyncService.reloadPricesFromBackend().thenAccept(result ->
                source.getServer().execute(() -> sendReloadResult(source, result)));
        return 1;
    }

    private static void sendReloadResult(CommandSourceStack source, MarketSyncResult result) {
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Impossible de charger les prix du marche : backend indisponible et aucun cache local trouve."));
            return;
        }
        if (result.usingCache()) {
            source.sendSuccess(() -> Component.literal("Backend indisponible, utilisation du cache local."), true);
        } else {
            source.sendSuccess(() -> Component.literal("Prix du marche recharges avec succes."), true);
        }
        sendSummary(source, result.data());
    }

    private static int debug(CommandSourceStack source) {
        MarketConfig config = MarketConfig.load();
        source.sendSuccess(() -> Component.literal("=== Debug marche ==="), false);
        source.sendSuccess(() -> Component.literal("backendUrl: " + config.normalizedBackendUrl()), false);
        source.sendSuccess(() -> Component.literal("cachePresent: " + MarketCacheManager.exists()), false);
        source.sendSuccess(() -> Component.literal("usingCache: " + MarketSyncService.isUsingCache()), false);
        source.sendSuccess(() -> Component.literal("lastSyncStatus: " + MarketSyncService.getLastSyncStatus()), false);
        source.sendSuccess(() -> Component.literal("comptoirsLocaux: " + MerchantCounterLocalStore.count()), false);
        source.sendSuccess(() -> Component.literal("comptoirsPendingSync: " + MerchantCounterLocalStore.pendingCount()), false);
        source.sendSuccess(() -> Component.literal("transactionsPendingSync: " + PendingMarketTransactionManager.count()), false);
        source.sendSuccess(() -> Component.literal("referenceItems: " + MarketReferenceItemService.getBackendItems().size()), false);
        source.sendSuccess(() -> Component.literal("referenceItemsLastLoadedAt: " + MarketReferenceItemService.getLastLoadedAt()), false);
        Boolean reachable = MarketSyncService.getBackendReachable();
        source.sendSuccess(() -> Component.literal("backendReachable: " + (reachable == null ? "inconnu" : reachable ? "oui" : "non")), false);
        MarketSyncService.getCurrentPrices().ifPresentOrElse(
                prices -> sendSummary(source, prices),
                () -> source.sendSuccess(() -> Component.literal("Aucun prix actuellement charge."), false)
        );
        return 1;
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("=== Informations marche ==="), false);
        var cities = MarketPriceService.getAvailableCities();
        if (cities.isEmpty()) {
            source.sendFailure(Component.literal("Aucune ville chargee. Lancez /mc admin market reload."));
        } else {
            source.sendSuccess(() -> Component.literal("Villes chargees: " + cities.size()), false);
            cities.forEach(city -> {
                source.sendSuccess(() -> Component.literal("Ville : " + city.cityName()), false);
                source.sendSuccess(() -> Component.literal("cityId : " + city.cityId()), false);
            });
        }
        MarketSyncService.getCurrentPrices().ifPresent(prices -> sendSummary(source, prices));
        source.sendSuccess(() -> Component.literal("=== Comptoirs charges ==="), false);
        MerchantCounterRegistry.loaded().forEach(counter -> {
            var items = counter.getConfiguredItems();
            source.sendSuccess(() -> Component.literal(counter.getCounterId() + " | items configures: " + items.size()), false);
            if (counter.isPlayerMarket()) source.sendSuccess(() -> Component.literal("Gains proprietaire en attente: "
                    + fr.renblood.medievalcoins.market.service.MarketMoneyService.formatMoney(counter.getPendingOwnerBalanceInIron())), false);
            var pendingTransactions = PendingMarketTransactionManager.entriesForCounter(counter.getCounterId());
            source.sendSuccess(() -> Component.literal("Transactions pending liees: " + pendingTransactions.size()), false);
            var knownTransactions = MarketTransactionHistoryCache.get(counter.getCounterId());
            var last = !pendingTransactions.isEmpty() ? pendingTransactions.get(pendingTransactions.size() - 1)
                    : !knownTransactions.isEmpty() ? knownTransactions.get(0) : null;
            if (last != null) {
                source.sendSuccess(() -> Component.literal("Derniere transaction connue: " + last.action + " "
                        + last.quantity + " x " + last.itemId + " | total=" + last.totalPrice), false);
            }
            items.stream().limit(10).forEach(item -> source.sendSuccess(() -> Component.literal(
                    "- " + item.itemId() + " | stockMode=" + item.stockMode() + " | autoPrice=" + item.autoPrice()), false));
        });
        return 1;
    }

    private static int setActive(CommandSourceStack source, String counterId, boolean active) {
        var counter = MerchantCounterRegistry.findLoaded(counterId);
        if (counter == null) {
            source.sendFailure(Component.literal("Comptoir introuvable: " + counterId));
            return 0;
        }
        counter.applyAdminUpdate(counter.snapshot().withActive(active));
        source.sendSuccess(() -> Component.literal("Comptoir " + counterId + " -> " + (active ? "actif" : "desactive")), true);
        return 1;
    }

    private static int ownerInfo(CommandSourceStack source, String counterId) {
        var counter = MerchantCounterRegistry.findLoaded(counterId);
        if (counter == null) {
            source.sendFailure(Component.literal("Comptoir introuvable: " + counterId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Owner: " + counter.getOwnerName() + " | " + counter.getOwnerId()), false);
        return 1;
    }

    private static int inspect(CommandSourceStack source, String counterId) {
        var counter = MerchantCounterRegistry.findLoaded(counterId);
        if (counter == null) {
            source.sendFailure(Component.literal("Comptoir introuvable: " + counterId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Type=" + counter.getCounterType() + " active=" + counter.isActive()
                + " owner=" + counter.getOwnerName()), false);
        source.sendSuccess(() -> Component.literal("Derniere interaction owner=" + counter.getLastOwnerInteractionAt()
                + " | derniere transaction=" + counter.getLastTransactionAt()), false);
        source.sendSuccess(() -> Component.literal("Gains en attente=" + counter.getPendingOwnerBalanceInIron()
                + " | items configures=" + counter.getConfiguredItems().size()), false);
        return 1;
    }

    private static int makeOfficial(CommandSourceStack source, String counterId) {
        var counter = MerchantCounterRegistry.findLoaded(counterId);
        if (counter == null) {
            source.sendFailure(Component.literal("Comptoir introuvable: " + counterId));
            return 0;
        }

        var data = counter.snapshot();
        counter.applyAdminUpdate(new fr.renblood.medievalcoins.market.counter.MerchantCounterData(
                data.counterId(),
                data.name().isBlank() ? "Comptoir marchand" : data.name(),
                "SHOP",
                data.cityId(),
                data.cityName(),
                "ADMIN",
                "server",
                "Administration",
                true,
                true,
                data.sellModeEnabled(),
                data.position(),
                data.configuredItems(),
                data.pendingOwnerBalanceInIron(),
                data.breadPrice(),
                data.ironPrice(),
                data.woodPrice(),
                data.purchasableItems(),
                data.sellableItems(),
                data.availableCities(),
                data.configuredItemsData(),
                data.referenceItems(),
                data.playerMarketSlots()
        ));
        source.sendSuccess(() -> Component.literal("Comptoir " + counterId + " converti en comptoir admin SHOP."), true);
        return 1;
    }

    private static int removeItem(CommandSourceStack source, String counterId, String slotOrItemId) {
        var counter = MerchantCounterRegistry.findLoaded(counterId);
        if (counter == null) {
            source.sendFailure(Component.literal("Comptoir introuvable: " + counterId));
            return 0;
        }
        Integer slot = parseSlot(slotOrItemId);
        if (slot != null && counter.isPlayerMarket()) {
            if (!counter.clearPlayerSlotForAdmin(slot)) {
                source.sendFailure(Component.literal("Slot introuvable ou vide."));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("Slot " + slot + " retire du comptoir."), true);
            return 1;
        }
        if (!counter.removeConfiguredItemForAdmin(slotOrItemId)) {
            source.sendFailure(Component.literal("Item introuvable: " + slotOrItemId));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Item retire: " + slotOrItemId), true);
        return 1;
    }

    private static int listInactive(CommandSourceStack source, int days) {
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        var inactive = MerchantCounterRegistry.loaded().stream()
                .filter(counter -> Math.max(counter.getLastOwnerInteractionAt(), counter.getLastTransactionAt()) < cutoff)
                .sorted(Comparator.comparingLong(counter -> Math.max(counter.getLastOwnerInteractionAt(), counter.getLastTransactionAt())))
                .toList();
        if (inactive.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Aucun comptoir inactif depuis " + days + " jours."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Comptoirs inactifs depuis " + days + " jours: " + inactive.size()), false);
        inactive.stream().limit(20).forEach(counter -> source.sendSuccess(() -> Component.literal(
                counter.getCounterId() + " | owner=" + counter.getOwnerName() + " | type=" + counter.getCounterType()), false));
        return 1;
    }

    private static Integer parseSlot(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void sendSummary(CommandSourceStack source, MarketPriceCache prices) {
        long cities = prices.prices.stream()
                .map(price -> price.cityId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .count();
        source.sendSuccess(() -> Component.literal("Prix charges: " + prices.prices.size() + " | Villes: " + cities), false);
        source.sendSuccess(() -> Component.literal("calculationVersion: " + display(prices.calculationVersion)), false);
        source.sendSuccess(() -> Component.literal("calculatedAt: " + display(prices.calculatedAt)), false);
        source.sendSuccess(() -> Component.literal("cachedAt: " + display(prices.cachedAt)), false);
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "indisponible" : value;
    }
}
