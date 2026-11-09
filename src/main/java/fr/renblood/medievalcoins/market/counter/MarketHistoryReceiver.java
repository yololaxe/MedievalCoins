package fr.renblood.medievalcoins.market.counter;

import java.util.List;

public interface MarketHistoryReceiver {
    void receiveHistory(List<MarketTransactionData> transactions, boolean backendUnavailable);
}
