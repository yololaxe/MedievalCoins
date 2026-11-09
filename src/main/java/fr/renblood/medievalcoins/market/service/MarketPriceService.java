package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.market.counter.MerchantCounterBlockEntity;
import fr.renblood.medievalcoins.market.model.MarketPriceDto;

import java.util.List;
import java.util.Optional;
import fr.renblood.medievalcoins.market.counter.MarketCityData;
import java.util.Comparator;
import fr.renblood.medievalcoins.market.counter.ReferenceItemData;

public final class MarketPriceService {
    private MarketPriceService() {
    }

    public static Optional<MarketPriceDto> getPrice(String cityId, String marketType, String itemId) {
        return MarketSyncService.getCurrentPrices().flatMap(cache -> cache.prices.stream()
                .filter(price -> matches(price.cityId, cityId))
                .filter(price -> blank(marketType) || matches(price.marketType, marketType))
                .filter(price -> matchesItem(price.itemId, itemId))
                .findFirst());
    }

    public static Optional<Long> getBuyPrice(String cityId, String marketType, String itemId) {
        return getPrice(cityId, marketType, itemId).map(price ->
                price.buyPrice != null ? price.buyPrice : price.calculatedPrice);
    }

    public static Optional<Long> getSellPrice(String cityId, String marketType, String itemId) {
        return getPrice(cityId, marketType, itemId).map(price ->
                price.sellPrice != null ? price.sellPrice
                        : price.buyPrice != null ? price.buyPrice : price.calculatedPrice);
    }

    public static List<MarketPriceDto> getPricesForCity(String cityId) {
        return getPricesForCity(cityId, null);
    }

    public static List<MarketPriceDto> getPricesForCity(String cityId, String cityName) {
        return MarketSyncService.getCurrentPrices().map(cache -> cache.prices.stream()
                .filter(price -> matches(price.cityId, cityId) || matches(price.cityName, cityName))
                .toList()).orElse(List.of());
    }

    public static List<MarketPriceDto> getPricesForCounter(MerchantCounterBlockEntity counter) {
        if (!counter.isAutoPrice()) return List.of();
        List<MarketPriceDto> cityPrices = getPricesForCity(counter.getCityId(), counter.getCityName());
        List<MarketPriceDto> typed = cityPrices.stream()
                .filter(price -> blank(counter.getCounterType()) || matches(price.marketType, counter.getCounterType()))
                .toList();
        return typed.isEmpty() && counter.isOfficial() ? cityPrices : typed;
    }

    public static List<MarketCityData> getAvailableCities() {
        return MarketSyncService.getCurrentPrices().map(cache -> cache.prices.stream()
                .filter(price -> !blank(price.cityId) && !blank(price.cityName))
                .map(price -> new MarketCityData(price.cityId, price.cityName))
                .distinct()
                .sorted(Comparator.comparing(MarketCityData::cityName, String.CASE_INSENSITIVE_ORDER))
                .toList()).orElse(List.of());
    }

    public static List<ReferenceItemData> getReferenceItems() {
        if (!MarketReferenceItemService.getBackendItems().isEmpty()) return MarketReferenceItemService.getBackendItems();
        return MarketSyncService.getCurrentPrices().map(cache -> cache.prices.stream()
                .filter(price -> !blank(price.itemId))
                .collect(java.util.stream.Collectors.toMap(price -> price.itemId,
                        price -> new ReferenceItemData(price.itemId,
                                blank(price.displayName) ? price.itemId : price.displayName,
                                blank(price.category) ? "AUTRE" : price.category,
                                price.buyPrice != null ? price.buyPrice : price.calculatedPrice),
                        (first, ignored) -> first))
                .values().stream()
                .sorted(Comparator.comparing(ReferenceItemData::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList()).orElse(List.of());
    }

    private static boolean matches(String value, String expected) {
        return !blank(value) && !blank(expected) && value.equalsIgnoreCase(expected);
    }

    private static boolean matchesItem(String value, String expected) {
        if (matches(value, expected)) return true;
        if (blank(value) || blank(expected)) return false;
        return value.toLowerCase().endsWith(":" + expected.toLowerCase());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
