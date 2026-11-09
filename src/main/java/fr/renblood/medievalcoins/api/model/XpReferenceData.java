package fr.renblood.medievalcoins.api.model;

public record XpReferenceData(
        String action,
        String job,
        String itemId,
        String displayName,
        String category,
        int xpAmount
) {
    public boolean matches(String expectedAction, String expectedJob, String expectedItemId) {
        return matchesValue(action, expectedAction)
                && matchesValue(job, expectedJob)
                && matchesItem(itemId, expectedItemId);
    }

    private static boolean matchesValue(String value, String expected) {
        return expected == null || expected.isBlank()
                || (value != null && value.equalsIgnoreCase(expected));
    }

    private static boolean matchesItem(String value, String expected) {
        if (expected == null || expected.isBlank()) return true;
        if (value == null || value.isBlank()) return false;
        return value.equalsIgnoreCase(expected)
                || value.toLowerCase().endsWith(":" + expected.toLowerCase());
    }
}
