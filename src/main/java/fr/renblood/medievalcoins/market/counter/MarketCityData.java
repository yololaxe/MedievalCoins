package fr.renblood.medievalcoins.market.counter;

import net.minecraft.network.FriendlyByteBuf;

public record MarketCityData(String cityId, String cityName) {
    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(cityId);
        buffer.writeUtf(cityName);
    }

    public static MarketCityData read(FriendlyByteBuf buffer) {
        return new MarketCityData(buffer.readUtf(), buffer.readUtf());
    }
}
