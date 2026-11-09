package fr.renblood.medievalcoins.market.model;

public record MarketSyncResult(
        boolean success,
        boolean usingCache,
        String status,
        MarketPriceCache data
) {
    public static MarketSyncResult backend(MarketPriceCache data) {
        return new MarketSyncResult(true, false, "BACKEND_SUCCESS", data);
    }

    public static MarketSyncResult cache(MarketPriceCache data, String reason) {
        return new MarketSyncResult(true, true, "CACHE_FALLBACK: " + reason, data);
    }

    public static MarketSyncResult failure(String reason) {
        return new MarketSyncResult(false, false, "FAILED: " + reason, null);
    }
}
