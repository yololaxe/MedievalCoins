package fr.renblood.medievalcoins.market.counter;

import fr.renblood.medievalcoins.market.model.MarketTransactionDto;
import net.minecraft.network.FriendlyByteBuf;

public record MarketTransactionData(
        String id, String playerName, String itemId, String itemDisplayName, String action,
        int quantity, long unitPrice, long totalPrice, long ownerGain, String createdAt, boolean pending
) {
    public static MarketTransactionData from(MarketTransactionDto value, boolean pending) {
        return new MarketTransactionData(text(value.id, value.localTransactionId), text(value.playerName, "Joueur inconnu"),
                text(value.itemId, ""), text(value.itemDisplayName, value.itemId), text(value.action, "BUY"),
                value.quantity, value.unitPrice, value.totalPrice, value.ownerGain,
                text(value.createdAt, pending ? "Maintenant" : "Date inconnue"), pending);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(id); buffer.writeUtf(playerName); buffer.writeUtf(itemId); buffer.writeUtf(itemDisplayName);
        buffer.writeUtf(action); buffer.writeVarInt(quantity); buffer.writeLong(unitPrice); buffer.writeLong(totalPrice);
        buffer.writeLong(ownerGain); buffer.writeUtf(createdAt); buffer.writeBoolean(pending);
    }

    public static MarketTransactionData read(FriendlyByteBuf buffer) {
        return new MarketTransactionData(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                buffer.readUtf(), buffer.readVarInt(), buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readUtf(), buffer.readBoolean());
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback == null ? "" : fallback : value;
    }
}
