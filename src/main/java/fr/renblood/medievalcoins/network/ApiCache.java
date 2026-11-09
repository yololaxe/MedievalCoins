package fr.renblood.medievalcoins.network;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public final class ApiCache {
    private static final ConcurrentHashMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ApiCache() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        Entry entry = ENTRIES.get(key);
        if (entry == null) return null;
        if (entry.expiresAtNanos < System.nanoTime()) {
            ENTRIES.remove(key, entry);
            return null;
        }
        return (T) entry.value;
    }

    public static void put(String key, Object value, Duration ttl) {
        if (value == null || ttl.isZero() || ttl.isNegative()) return;
        ENTRIES.put(key, new Entry(value, System.nanoTime() + ttl.toNanos()));
    }

    public static void invalidatePrefix(String prefix) {
        ENTRIES.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void clear() {
        ENTRIES.clear();
    }

    private record Entry(Object value, long expiresAtNanos) {
    }
}
