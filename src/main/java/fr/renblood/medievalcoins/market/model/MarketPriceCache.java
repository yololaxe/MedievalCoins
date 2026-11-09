package fr.renblood.medievalcoins.market.model;

import java.util.ArrayList;
import java.util.List;

public class MarketPriceCache {
    public String calculationVersion;
    public String calculatedAt;
    public String cachedAt;
    public List<MarketPriceDto> prices = new ArrayList<>();
}
