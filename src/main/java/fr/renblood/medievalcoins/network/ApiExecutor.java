package fr.renblood.medievalcoins.network;

import fr.renblood.medievalcoins.MedievalCoin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ApiExecutor {
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "MedievalCoins-Api-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    };
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            2, 4, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(256),
            THREAD_FACTORY,
            new ThreadPoolExecutor.AbortPolicy()
    );

    private ApiExecutor() {
    }

    public static void execute(Runnable task) {
        try {
            EXECUTOR.execute(task);
        } catch (RuntimeException exception) {
            MedievalCoin.LOGGER.error("File d'attente API saturee; tache refusee", exception);
        }
    }

    public static <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, EXECUTOR);
    }

    public static CompletableFuture<Void> run(Runnable task) {
        return CompletableFuture.runAsync(task, EXECUTOR);
    }

    public static Executor executor() {
        return EXECUTOR;
    }
}
