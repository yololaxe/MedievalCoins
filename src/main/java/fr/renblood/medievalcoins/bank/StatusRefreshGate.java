package fr.renblood.medievalcoins.bank;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Bounded, monotonic cooldown; a running request is never evicted to admit another. */
public final class StatusRefreshGate<K> {
    public static final class Ticket {
        private boolean running = true;
        private long completed;
    }
    private final Map<K, Ticket> requests = new HashMap<>();
    private final int capacity;
    private final long cooldown;
    private final LongSupplier clock;

    public StatusRefreshGate(int capacity, long cooldown, LongSupplier clock) {
        if (capacity < 1 || cooldown < 0) throw new IllegalArgumentException("Invalid refresh bounds");
        this.capacity = capacity; this.cooldown = cooldown; this.clock = clock;
    }
    public synchronized Ticket begin(K key) {
        long now = clock.getAsLong();
        requests.entrySet().removeIf(entry -> !entry.getValue().running && now - entry.getValue().completed >= cooldown);
        if (requests.containsKey(key) || requests.size() >= capacity) return null;
        Ticket ticket = new Ticket(); requests.put(key, ticket); return ticket;
    }
    public synchronized boolean complete(K key, Ticket ticket) {
        if (requests.get(key) != ticket || !ticket.running) return false;
        ticket.running = false; ticket.completed = clock.getAsLong(); return true;
    }
    public synchronized void rejected(K key, Ticket ticket) { requests.remove(key, ticket); }
    public synchronized boolean contains(K key) { return requests.containsKey(key); }
    public synchronized void removeIf(Predicate<K> predicate) { requests.keySet().removeIf(predicate); }
}
