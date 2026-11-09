package fr.renblood.medievalcoins.market.model;

public class MarketTransactionDto {
    public String id;
    public String localTransactionId;
    public String counterId;
    public String playerUuid;
    public String playerName;
    public String itemId;
    public String itemDisplayName;
    public String action = "BUY";
    public int quantity;
    public long unitPrice;
    public long totalPrice;
    public long ownerGain;
    public String createdAt;
}
