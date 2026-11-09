package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.market.model.MarketTransactionDto;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MarketTransactionHistoryCache {
    private static final Map<String, List<MarketTransactionDto>> HISTORIES = new ConcurrentHashMap<>();

    private MarketTransactionHistoryCache() {}

    public static void remember(String counterId, List<MarketTransactionDto> transactions) {
        HISTORIES.put(counterId, List.copyOf(transactions));
    }

    public static List<MarketTransactionDto> get(String counterId) {
        return HISTORIES.getOrDefault(counterId, List.of());
    }
}
