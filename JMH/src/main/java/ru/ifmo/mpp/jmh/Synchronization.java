package ru.ifmo.mpp.jmh;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(5)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class Synchronization {

    @State(Scope.Thread)
    public static class StateLockNotShared {
        long x;
        Lock lock = new ReentrantLock();
    }

    @Benchmark
    @Group("lockNotShared")
    public long reader(StateLockNotShared s) {
        s.lock.lock();
        try {
            return s.x;
        } finally {
            s.lock.unlock();
        }
    }

    @Benchmark
    @Group("lockNotShared")
    public void writer(StateLockNotShared s) {
        s.lock.lock();
        try {
            s.x = 1;
        } finally {
            s.lock.unlock();
        }
    }


    @State(Scope.Benchmark)
    public static class StateLockShared {
        long x;
        Lock lock = new ReentrantLock();
    }

    @Benchmark
    @Group("lockShared")
    public long reader(StateLockShared s) {
        s.lock.lock();
        try {
            return s.x;
        } finally {
            s.lock.unlock();
        }
    }

    @Benchmark
    @Group("lockShared")
    public void writer(StateLockShared s) {
        s.lock.lock();
        try {
            s.x = 2;
        } finally {
            s.lock.unlock();
        }
    }


    @State(Scope.Thread)
    public static class StateSynchronizedNotShared {
        long x;

        synchronized long get() {
            return x;
        }

        synchronized void set(long x) {
            this.x = x;
        }
    }

    @Benchmark
    @Group("synchronizedNotShared")
    public long reader(StateSynchronizedNotShared s) {
        return s.get();
    }

    @Benchmark
    @Group("synchronizedNotShared")
    public void writer(StateSynchronizedNotShared s) {
        s.set(3);
    }


    @State(Scope.Benchmark)
    public static class StateSynchronizedShared {
        long x;

        synchronized long get() {
            return x;
        }

        synchronized void set(long x) {
            this.x = x;
        }
    }

    @Benchmark
    @Group("synchronizedShared")
    public long reader(StateSynchronizedShared s) {
        return s.get();
    }


    @Benchmark
    @Group("synchronizedShared")
    public void writer(StateSynchronizedShared s) {
        s.set(4);
    }


    @State(Scope.Thread)
    public static class StateVolatileNotShared {
        volatile long x;
    }

    @Benchmark
    @Group("volatileNotShared")
    public long reader(StateVolatileNotShared s) {
        return s.x;
    }

    @Benchmark
    @Group("volatileNotShared")
    public void writer(StateVolatileNotShared s) {
        s.x = 5;
    }


    @State(Scope.Benchmark)
    public static class StateVolatileShared {
        volatile long x;
    }

    @Benchmark
    @Group("volatileShared")
    public long reader(StateVolatileShared s) {
        return s.x;
    }

    @Benchmark
    @Group("volatileShared")
    public void writer(StateVolatileShared s) {
        s.x = 6;
    }


    @Threads(2)
    public static class Threads2 extends Synchronization{}

    @Threads(4)
    public static class Threads4 extends Synchronization{}

    @Threads(8)
    public static class Threads8 extends Synchronization{}

    @Threads(16)
    public static class Threads16 extends Synchronization{}

    @Threads(32)
    public static class Threads32 extends Synchronization{}
}
