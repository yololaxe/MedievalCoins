package fr.renblood.medievalcoins.market.counter;

import net.minecraft.network.FriendlyByteBuf;

public record ReferenceItemData(String itemId, String displayName, String category, Long referencePrice) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(itemId);
        buffer.writeUtf(displayName);
        buffer.writeUtf(category);
        buffer.writeBoolean(referencePrice != null);
        if (referencePrice != null) buffer.writeLong(referencePrice);
    }

    public static ReferenceItemData read(FriendlyByteBuf buffer) {
        return new ReferenceItemData(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                buffer.readBoolean() ? buffer.readLong() : null);
    }
}
