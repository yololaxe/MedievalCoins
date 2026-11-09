package fr.renblood.medievalcoins.market.service;

import fr.renblood.medievalcoins.MedievalCoin;
import fr.renblood.medievalcoins.market.model.MarketTransactionDto;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public final class MarketTransactionService {
    private MarketTransactionService() {
    }

    public static MarketTransactionDto purchase(String counterId, String playerUuid, String playerName,
                                                String itemId, String displayName, int quantity,
                                                long unitPrice, long ownerGain) {
        MarketTransactionDto transaction = new MarketTransactionDto();
        transaction.localTransactionId = UUID.randomUUID().toString();
        transaction.counterId = counterId;
        transaction.playerUuid = playerUuid;
        transaction.playerName = playerName;
        transaction.itemId = itemId;
        transaction.itemDisplayName = displayName;
        transaction.quantity = quantity;
        transaction.unitPrice = unitPrice;
        transaction.totalPrice = unitPrice * quantity;
        transaction.ownerGain = ownerGain;
        transaction.createdAt = Instant.now().toString();
        return transaction;
    }

    public static MarketTransactionDto sale(String counterId, String playerUuid, String playerName,
                                            String itemId, String displayName, int quantity,
                                            long unitPrice) {
        MarketTransactionDto transaction = purchase(counterId, playerUuid, playerName, itemId, displayName,
                quantity, unitPrice, -(unitPrice * quantity));
        transaction.action = "SELL";
        return transaction;
    }

    public static void sendOrQueue(MarketTransactionDto transaction) {
        MarketApiService.sendTransaction(transaction).whenComplete((ignored, failure) -> {
            if (failure == null) {
                PendingMarketTransactionManager.remove(transaction.localTransactionId);
                MedievalCoin.LOGGER.info("Transaction marche {} envoyee: {}", transaction.action, transaction.localTransactionId);
            } else {
                PendingMarketTransactionManager.add(transaction);
                MedievalCoin.LOGGER.warn("Transaction marche {} mise en attente: {}", transaction.action, transaction.localTransactionId);
            }
        });
    }

    public static CompletableFuture<Integer> retryPending() {
        List<MarketTransactionDto> entries = PendingMarketTransactionManager.entries();
        List<CompletableFuture<Boolean>> attempts = entries.stream().map(transaction ->
                MarketApiService.sendTransaction(transaction).handle((ignored, failure) -> {
                    if (failure == null) {
                        PendingMarketTransactionManager.remove(transaction.localTransactionId);
                        return true;
                    }
                    return false;
                })).toList();
        return CompletableFuture.allOf(attempts.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> (int) attempts.stream().filter(CompletableFuture::join).count());
    }
}
