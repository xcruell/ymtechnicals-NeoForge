package com.yesmenn.technicals.creeper.server;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Background threads for everything slow of the Creeper system (tool installation, downloads, conversion, hashing, file
 * reads), so the server thread never waits for disk, network or external programs. External processes are tracked and
 * killed when the server stops.
 */
public final class CreeperWorker {
    private static final AtomicInteger THREADS = new AtomicInteger();
    /** Downloads (one at a time, they are heavy). */
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor(CreeperWorker::thread);
    /** Short jobs: reading songs for transfers, saving the library index. */
    private static final ExecutorService IO = Executors.newFixedThreadPool(2, CreeperWorker::thread);
    private static final Set<Process> PROCESSES = ConcurrentHashMap.newKeySet();
    /** Increases when a server stops; jobs of an older generation drop their results. */
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private CreeperWorker() {
    }

    private static Thread thread(Runnable runnable) {
        Thread thread = new Thread(runnable, "ymtechnicals-creeper-" + THREADS.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        return thread;
    }

    public static <T> CompletableFuture<T> download(Supplier<T> job) {
        return CompletableFuture.supplyAsync(job, DOWNLOADS);
    }

    public static <T> CompletableFuture<T> io(Supplier<T> job) {
        return CompletableFuture.supplyAsync(job, IO);
    }

    public static int generation() {
        return GENERATION.get();
    }

    public static void track(Process process) {
        PROCESSES.add(process);
    }

    public static void untrack(Process process) {
        PROCESSES.remove(process);
    }

    /** Server stops: kill running yt-dlp/ffmpeg processes and invalidate running jobs. */
    public static void stopAll() {
        GENERATION.incrementAndGet();
        for (Process process : PROCESSES) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        PROCESSES.clear();
    }
}
