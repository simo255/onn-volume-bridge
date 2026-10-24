package io.github.simo255.onnvolumebridge;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VolumeHoldRepeaterTest {
    static final class Job implements VolumeHoldRepeater.Ticket {
        final long due; final Runnable task; boolean cancelled;
        Job(long due, Runnable task) { this.due = due; this.task = task; }
        public void cancel() { cancelled = true; }
    }
    static final class Clock implements VolumeHoldRepeater.Scheduler {
        long now; final PriorityQueue<Job> jobs = new PriorityQueue<>(Comparator.comparingLong(job -> job.due));
        public VolumeHoldRepeater.Ticket after(Runnable task, long delay) { Job job = new Job(now + delay, task); jobs.add(job); return job; }
        void advance(long duration) {
            long until = now + duration;
            while (!jobs.isEmpty() && jobs.peek().due <= until) {
                Job job = jobs.remove(); now = job.due; if (!job.cancelled) job.task.run();
            }
            now = until;
        }
    }
    static final class Fixture {
        final Clock clock = new Clock(); final List<VolumeKey> sent = new ArrayList<>(); boolean allowed = true;
        final VolumeHoldRepeater repeat = new VolumeHoldRepeater(clock, () -> clock.now, () -> allowed, sent::add);
    }
    @Test public void shortTapProducesExactlyOneCommand() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.UP); f.clock.advance(100); f.repeat.up(VolumeKey.UP); f.clock.advance(2000);
        assertEquals(Collections.singletonList(VolumeKey.UP), f.sent);
    }
    @Test public void holdingRepeatsWithoutAnyNativeRepeatEvents() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.DOWN); f.clock.advance(1000);
        assertEquals(12, f.sent.size()); assertTrue(f.sent.stream().allMatch(key -> key == VolumeKey.DOWN));
    }
    @Test public void releasingStopsFurtherCommandsImmediately() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.UP); f.clock.advance(1000); f.repeat.up(VolumeKey.UP);
        int stopped = f.sent.size(); f.clock.advance(5000); assertEquals(stopped, f.sent.size());
    }
    @Test public void duplicateDownDoesNotDoubleOrRestartTheHold() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.UP); f.clock.advance(200); f.repeat.down(VolumeKey.UP); f.clock.advance(50);
        assertEquals(2, f.sent.size());
    }
    @Test public void changingDirectionCancelsThePreviousDirection() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.UP); f.clock.advance(200); f.repeat.down(VolumeKey.DOWN); f.clock.advance(500);
        assertEquals(VolumeKey.UP, f.sent.get(0)); assertTrue(f.sent.size() > 2);
        assertTrue(f.sent.subList(1, f.sent.size()).stream().allMatch(key -> key == VolumeKey.DOWN));
    }
    @Test public void unrelatedReleaseDoesNotCancelTheActiveDirection() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.UP); f.repeat.up(VolumeKey.DOWN); f.clock.advance(250); assertEquals(2, f.sent.size());
    }
    @Test public void stopCancelsEvenAStaleScheduledCallback() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.UP); Job pending = f.clock.jobs.peek(); f.repeat.stop(); pending.task.run(); f.clock.advance(2000);
        assertEquals(1, f.sent.size());
    }
    @Test public void losingPermissionOrTheTvRouteStopsTheHold() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.DOWN); f.allowed = false; f.clock.advance(1000);
        assertEquals(1, f.sent.size()); f.allowed = true; f.clock.advance(1000); assertEquals(1, f.sent.size());
    }
    @Test public void aLostReleaseCannotRepeatForever() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.UP); f.clock.advance(20000); int bounded = f.sent.size();
        assertTrue(bounded > 1 && bounded < 150); f.clock.advance(20000); assertEquals(bounded, f.sent.size());
    }
    @Test public void muteIsNeverRepeatedByTheVolumeReader() {
        Fixture f = new Fixture(); f.repeat.down(VolumeKey.MUTE); f.clock.advance(2000); assertTrue(f.sent.isEmpty());
    }
}
