package fr.renblood.medievalcoins.market.counter;

import net.minecraft.network.FriendlyByteBuf;

public record CounterItemConfigData(
        String itemId, String displayName, boolean buyEnabled, boolean sellEnabled, boolean autoPrice,
        Long manualBuyPrice, Long manualSellPrice, String stockMode, Integer stockQuantity,
        double localModifier, boolean active
) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(itemId);
        buffer.writeUtf(displayName);
        buffer.writeBoolean(buyEnabled);
        buffer.writeBoolean(sellEnabled);
        buffer.writeBoolean(autoPrice);
        writeLong(buffer, manualBuyPrice);
        writeLong(buffer, manualSellPrice);
        buffer.writeUtf(stockMode);
        writeInt(buffer, stockQuantity);
        buffer.writeDouble(localModifier);
        buffer.writeBoolean(active);
    }

    public static CounterItemConfigData read(FriendlyByteBuf buffer) {
        return new CounterItemConfigData(buffer.readUtf(), buffer.readUtf(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), readLong(buffer), readLong(buffer), buffer.readUtf(), readInt(buffer),
                buffer.readDouble(), buffer.readBoolean());
    }

    private static void writeLong(FriendlyByteBuf buffer, Long value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeLong(value);
    }

    private static Long readLong(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readLong() : null;
    }

    private static void writeInt(FriendlyByteBuf buffer, Integer value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeVarInt(value);
    }

    private static Integer readInt(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readVarInt() : null;
    }
}
