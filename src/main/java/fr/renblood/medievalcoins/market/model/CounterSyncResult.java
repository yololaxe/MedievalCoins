package fr.renblood.medievalcoins.market.model;

import java.util.List;

public record CounterSyncResult(
        int received, int updated, int notFound, int pendingSent, int errors,
        List<String> errorMessages
) {
}
