package fr.renblood.medievalcoins.market.model;

public class MerchantCounterDto {
    public String counterId;
    public String name;
    public String type;
    public String cityId;
    public String cityName;
    public String ownerType;
    public String ownerId;
    public String ownerName;
    public boolean active = true;
    public boolean buyModeEnabled = true;
    public boolean sellModeEnabled;
    public boolean autoPrice = true;
    public String dimension;
    public Integer x;
    public Integer y;
    public Integer z;
    public Long lastOwnerInteractionAt;
    public Long lastTransactionAt;
}
