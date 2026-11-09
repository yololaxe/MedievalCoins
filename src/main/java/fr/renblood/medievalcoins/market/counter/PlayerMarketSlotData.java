package fr.renblood.medievalcoins.market.counter;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record PlayerMarketSlotData(int slot, ItemStack stack, long price, boolean enabled,
                                   int reserved, int incoming, String receipt, boolean conflict) {
    public PlayerMarketSlotData(int slot, ItemStack stack, long price, boolean enabled) {
        this(slot, stack, price, enabled, 0, 0, "", false);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slot);
        buffer.writeItem(stack);
        buffer.writeLong(price);
        buffer.writeBoolean(enabled);
        buffer.writeVarInt(reserved);
        buffer.writeVarInt(incoming);
        buffer.writeUtf(receipt);
        buffer.writeBoolean(conflict);
    }

    public static PlayerMarketSlotData read(FriendlyByteBuf buffer) {
        return new PlayerMarketSlotData(buffer.readVarInt(), buffer.readItem(), buffer.readLong(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(), buffer.readBoolean());
    }
}
