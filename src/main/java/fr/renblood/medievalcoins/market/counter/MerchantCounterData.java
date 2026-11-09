package fr.renblood.medievalcoins.market.counter;

import net.minecraft.network.FriendlyByteBuf;
import java.util.List;

public record MerchantCounterData(
        String counterId, String name, String type, String cityId, String cityName,
        String ownerType, String ownerId, String ownerName,
        boolean active, boolean buyModeEnabled, boolean sellModeEnabled,
        String position, int configuredItems, long pendingOwnerBalanceInIron, Long breadPrice, Long ironPrice, Long woodPrice,
        List<MerchantCounterItemData> purchasableItems, List<MerchantCounterItemData> sellableItems,
        List<MarketCityData> availableCities,
        List<CounterItemConfigData> configuredItemsData, List<ReferenceItemData> referenceItems,
        List<PlayerMarketSlotData> playerMarketSlots
) {
    public MerchantCounterData withActive(boolean value) {
        return new MerchantCounterData(counterId, name, type, cityId, cityName, ownerType, ownerId, ownerName, value,
                buyModeEnabled, sellModeEnabled, position, configuredItems, pendingOwnerBalanceInIron, breadPrice, ironPrice,
                woodPrice, purchasableItems, sellableItems, availableCities, configuredItemsData, referenceItems, playerMarketSlots);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(counterId);
        buffer.writeUtf(name);
        buffer.writeUtf(type);
        buffer.writeUtf(cityId);
        buffer.writeUtf(cityName);
        buffer.writeUtf(ownerType);
        buffer.writeUtf(ownerId);
        buffer.writeUtf(ownerName);
        buffer.writeBoolean(active);
        buffer.writeBoolean(buyModeEnabled);
        buffer.writeBoolean(sellModeEnabled);
        buffer.writeUtf(position);
        buffer.writeVarInt(configuredItems);
        buffer.writeLong(pendingOwnerBalanceInIron);
        writeNullableLong(buffer, breadPrice);
        writeNullableLong(buffer, ironPrice);
        writeNullableLong(buffer, woodPrice);
        buffer.writeCollection(purchasableItems, (target, item) -> item.write(target));
        buffer.writeCollection(sellableItems, (target, item) -> item.write(target));
        buffer.writeCollection(availableCities, (target, city) -> city.write(target));
        buffer.writeCollection(configuredItemsData, (target, item) -> item.write(target));
        buffer.writeCollection(referenceItems, (target, item) -> item.write(target));
        buffer.writeCollection(playerMarketSlots, (target, item) -> item.write(target));
    }

    public static MerchantCounterData read(FriendlyByteBuf buffer) {
        return new MerchantCounterData(
                buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readUtf(), buffer.readVarInt(), buffer.readLong(),
                readNullableLong(buffer), readNullableLong(buffer), readNullableLong(buffer),
                buffer.readList(MerchantCounterItemData::read),
                buffer.readList(MerchantCounterItemData::read),
                buffer.readList(MarketCityData::read),
                buffer.readList(CounterItemConfigData::read),
                buffer.readList(ReferenceItemData::read),
                buffer.readList(PlayerMarketSlotData::read)
        );
    }

    private static void writeNullableLong(FriendlyByteBuf buffer, Long value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeLong(value);
    }

    private static Long readNullableLong(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readLong() : null;
    }
}
