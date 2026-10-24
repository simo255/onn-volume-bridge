package io.github.simo255.onnvolumebridge;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Monotonic, event-driven held-volume repeat. No native REPEAT packets are required. */
final class VolumeHoldRepeater {
    static final long FIRST_REPEAT_MS = 250;
    static final long REPEAT_MS = 75;
    static final long MAX_HOLD_MS = 10000;
    interface Ticket { void cancel(); }
    interface Scheduler { Ticket after(Runnable task, long delayMs); }
    private final Scheduler scheduler;
    private final LongSupplier now;
    private final BooleanSupplier allowed;
    private final Consumer<VolumeKey> output;
    private Ticket pending;
    private VolumeKey held;
    private long generation, started;

    VolumeHoldRepeater(Scheduler scheduler, LongSupplier now, BooleanSupplier allowed, Consumer<VolumeKey> output) {
        this.scheduler = scheduler; this.now = now; this.allowed = allowed; this.output = output;
    }

    synchronized void down(VolumeKey key) {
        if (key == VolumeKey.MUTE || !allowed.getAsBoolean()) { stop(); return; }
        if (held == key) return; // Duplicate DOWN and native REPEAT must not double the cadence.
        stop();
        held = key; started = now.getAsLong(); long run = generation;
        output.accept(key);
        pending = scheduler.after(() -> tick(run), FIRST_REPEAT_MS);
    }

    synchronized void up(VolumeKey key) { if (held == key) stop(); }

    synchronized void stop() {
        generation++; held = null;
        if (pending != null) pending.cancel();
        pending = null;
    }

    private synchronized void tick(long run) {
        if (run != generation || held == null) return;
        if (!allowed.getAsBoolean() || now.getAsLong() - started >= MAX_HOLD_MS) { stop(); return; }
        output.accept(held);
        pending = scheduler.after(() -> tick(run), REPEAT_MS);
    }
}
