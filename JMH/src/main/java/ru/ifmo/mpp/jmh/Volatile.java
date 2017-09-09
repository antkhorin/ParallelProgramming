package ru.ifmo.mpp.jmh;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(5)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
public class Volatile {

    @State(Scope.Thread)
    public static class StateNotVolatileNotShared {
        int x;
    }

    @Benchmark
    @Group("notVolatileNotShared")
    public int reader(StateNotVolatileNotShared s) {
        return s.x;
    }

    @Benchmark
    @Group("notVolatileNotShared")
    public void writer(StateNotVolatileNotShared s) {
        s.x = 1;
    }


    @State(Scope.Benchmark)
    public static class StateNotVolatileShared {
        int x;
    }

    @Benchmark
    @Group("notVolatileShared")
    public int reader(StateNotVolatileShared s) {
        return s.x;
    }

    @Benchmark
    @Group("notVolatileShared")
    public void writer(StateNotVolatileShared s) {
        s.x = 2;
    }


    @State(Scope.Thread)
    public static class StateVolatileNotShared {
        volatile int x;
    }

    @Benchmark
    @Group("volatileNotShared")
    public int reader(StateVolatileNotShared s) {
        return s.x;
    }

    @Benchmark
    @Group("volatileNotShared")
    public void writer(StateVolatileNotShared s) {
        s.x = 3;
    }


    @State(Scope.Benchmark)
    public static class StateVolatileShared {
        volatile int x;
    }

    @Benchmark
    @Group("volatileShared")
    public int reader(StateVolatileShared s) {
        return s.x;
    }

    @Benchmark
    @Group("volatileShared")
    public void writer(StateVolatileShared s) {
        s.x = 4;
    }

    @Threads(2)
    public static class Threads2 extends Volatile{}

    @Threads(4)
    public static class Threads4 extends Volatile{}

    @Threads(8)
    public static class Threads8 extends Volatile{}

    @Threads(16)
    public static class Threads16 extends Volatile{}

    @Threads(32)
    public static class Threads32 extends Volatile{}
}
