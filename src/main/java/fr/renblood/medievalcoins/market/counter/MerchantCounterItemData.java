package fr.renblood.medievalcoins.market.counter;

import net.minecraft.network.FriendlyByteBuf;

public record MerchantCounterItemData(String purchaseKey, String itemId, String displayName, long unitPrice, int stockQuantity, boolean limitedStock) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(purchaseKey);
        buffer.writeUtf(itemId);
        buffer.writeUtf(displayName);
        buffer.writeLong(unitPrice);
        buffer.writeVarInt(stockQuantity);
        buffer.writeBoolean(limitedStock);
    }

    public static MerchantCounterItemData read(FriendlyByteBuf buffer) {
        return new MerchantCounterItemData(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readLong(), buffer.readVarInt(), buffer.readBoolean());
    }
}
