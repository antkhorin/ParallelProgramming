package ru.ifmo.mpp.hashmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;

/**
 * Int-to-Int hash map with open addressing and linear probes.
 * <p>
 * TODO: This class is <b>NOT</b> thread-safe.
 *
 * @author <Фамилия>.
 */
public class IntIntHashMapA {
    private static final int MAGIC = 0x9E3779B9; // golden ratio
    private static final int INITIAL_CAPACITY = 4; // !!! DO NOT CHANGE INITIAL CAPACITY !!!
    private static final int MAX_PROBES = 2; // max number of probes to find an item

    private static final int NULL_KEY = 0; // missing key (initial value)
    private static final int NULL_VALUE = 0; // missing value (initial value)
    private static final int DEL_VALUE = Integer.MAX_VALUE; // mark for removed value
    private static final int DONE_VALUE = Integer.MIN_VALUE;
    private static final int NEEDS_REHASH = -1; // returned by putInternal to indicate that rehash is needed

    // Checks is the value is in the range of allowed values
    private static boolean isValue(int value) {
        return value > 0 && value < DEL_VALUE; // the range or allowed values
    }

    @SuppressWarnings("NumericOverflow")
    private static int toCopy(int value) {
        return value | Integer.MIN_VALUE;
    }

    private static boolean isCopying(int value) {
        return value < 0 && value != DONE_VALUE;
    }

    // Converts internal value to the public results of the methods
    private static int toValue(int value) {
        if (value < 0){
            throw new RuntimeException();
        }
        return isValue(value) ? value : 0;
    }

    private final AtomicReference<Core> core = new AtomicReference<>(new Core(INITIAL_CAPACITY));

    /**
     * Returns value for the corresponding key or zero if this key is not present.
     *
     * @param key a positive key.
     * @return value for the corresponding or zero if this key is not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    public int get(int key) {
        if (key <= 0) throw new IllegalArgumentException("Key must be positive: " + key);
        return toValue(core.get().getInternal(key));
    }

    /**
     * Changes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key   a positive key.
     * @param value a positive value.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key or value are not positive, or value is equal to
     *                                  {@link Integer#MAX_VALUE} which is reserved.
     */
    public int put(int key, int value) {
        if (key <= 0) throw new IllegalArgumentException("Key must be positive: " + key);
        if (!isValue(value)) throw new IllegalArgumentException("Invalid value: " + value);
        return toValue(putAndRehashWhileNeeded(key, value));
    }

    /**
     * Removes value for the corresponding key and returns old value or zero if key was not present.
     *
     * @param key a positive key.
     * @return old value or zero if this key was not present.
     * @throws IllegalArgumentException if key is not positive.
     */
    public int remove(int key) {
        if (key <= 0) throw new IllegalArgumentException("Key must be positive: " + key);
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE));
    }

    private int putAndRehashWhileNeeded(int key, int value) {
        while (true) {
            int oldValue = core.get().putInternal(key, value);
            if (oldValue != NEEDS_REHASH)
                return oldValue;
            core.set(core.get().rehash());
        }
    }

    private static class Core {
        final AtomicIntegerArray map; // pairs of key, value here
        final int shift;
        final AtomicReference<Core> next = new AtomicReference<>();

        /**
         * Creates new core with a given capacity for (key, value) pair.
         * The actual size of the map is twice as big.
         */
        Core(int capacity) {
            map = new AtomicIntegerArray(2 * capacity);
            int mask = capacity - 1;
            assert mask > 0 && (mask & capacity) == 0 : "Capacity must be power of 2: " + capacity;
            shift = 32 - Integer.bitCount(mask);
        }

        int getInternal(int key) {
            int index = index(key);
            int probes = 0;
            while (map.get(index) != key) { // optimize for successful lookup
                if (map.get(index) == NULL_KEY)
                    return NULL_VALUE; // not found -- no value
                if (++probes >= MAX_PROBES)
                    return NULL_VALUE;
                if (index == 0)
                    index = map.length();
                index -= 2;
            }
            int value = map.get(index + 1);
            if (value < 0) {
//                return value & Integer.MAX_VALUE;
                move(index);
                return next.get().getInternal(key);
            }
            // found key -- return value
            return value;
        }

        int putInternal(int key, int value) {
            if (value == 0) throw new RuntimeException();
            int index = index(key);
            int probes = 0;
            int val;
            while (map.get(index) != key) {
                if (map.get(index) == NULL_KEY) {
                    if (value == DEL_VALUE) {
                        return NULL_VALUE;
                    }
                    if (map.compareAndSet(index, NULL_KEY, key)) {
                        break;
                    }
                }
                if (map.get(index) == key) {
                    break;
                }
                if (++probes >= MAX_PROBES)
                    return NEEDS_REHASH;
                if (index == 0) {
                    index = map.length();
                }
                index -= 2;
            }
            int oldValue;
            do {
                oldValue = map.get(index + 1);
                if (oldValue < 0) {
                    move(index);
                    int res = next.get().putInternal(key, value);
                    if (res == NEEDS_REHASH) {
                        System.out.println("NESTED REHASH\nNESTED REHASH\n");
                        next.get().next.set(next.get().rehash());
                    }
                    return res;
                } else if (map.compareAndSet(index + 1, oldValue, value)) {
                    return oldValue;
                }
            } while (true);
        }

        void putIfAbsent(int key, int value) {
            int index = index(key);
            int probes = 0;
            while (map.get(index) != key) {
                if (map.get(index) == NULL_KEY) {
                    if (map.compareAndSet(index, NULL_KEY, key)) {
                        break;
                    }
                }
                if (map.get(index) == key) {
                    break;
                }
                if (++probes >= MAX_PROBES) {
                    System.out.println("ABSENT\n");
                    return;
                }
                if (index == 0) {
                    index = map.length();
                }
                index -= 2;
            }
            map.compareAndSet(index + 1, NULL_VALUE, value);
        }

        //use for value < 0
        void move(int index) {
            if (map.get(index + 1) >= 0){
                throw new RuntimeException();
            }
            int val = map.get(index + 1);
            if (val != DONE_VALUE) {
                next.get().putIfAbsent(map.get(index), val & Integer.MAX_VALUE);
            }
            map.set(index + 1, DONE_VALUE);
        }

        Core rehash() {
            if (next.get() == null) {
                next.compareAndSet(null, new Core(map.length())); // map.length is twice the current capacity
            }
            for (int index = 0; index < map.length(); index += 2) {
                int oldValue = map.get(index + 1);
                if (oldValue == DONE_VALUE) continue;
                do {
                    oldValue = map.get(index + 1);
                    if (oldValue == DEL_VALUE) {
                        if (map.compareAndSet(index + 1, DEL_VALUE, DONE_VALUE)) {
                            break;
                        }
                    }
                    if (oldValue < 0) break;
                } while (oldValue == DEL_VALUE || !map.compareAndSet(index + 1, oldValue, toCopy(oldValue)));
                if (shift >= 0 && index == map.length() - 2) {
                    int x = 14;
                    for (int i = 1; i < (shift * shift * shift * shift * shift); i++) {
                        x += MAGIC;
                        x %= i;
                    }
                    if (x == 123456789) {
                        System.out.println("AAA");
                    }
                }
                move(index);
            }
            return next.get();
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        int index(int key) {
            return ((key * MAGIC) >>> shift) * 2;
//            int capacity = 1 << (32 - shift);
//            return key % (capacity) * 2;
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(100);
        final IntIntHashMapA map = new IntIntHashMapA();
        for (int i = 1; i < 10; i++) {
            System.out.println(map.core.get().index(i));
        }
        map.put(1, 2);
        map.put(6, 3);
        pool.submit(() -> map.put(3, 4));
        int x = 14;
        for (int i = 1; i < (10000); i++) {
            x += MAGIC;
            x %= i;
        }
        map.put(3, 5);
        if (x == 123456789) {
            System.out.println("AAA");
        }
        Random random = new Random();
        for (int i = 0; i < 1000000; i++) {
            pool.submit(() -> {
                for (int j = 0; j < 10; j++) {
                    map.put(random.nextInt(50000000) + 1, random.nextInt(100) + 1);
                }
//                for (int j = 0; j < 3; j++) {
//                    map.remove(random.nextInt(100000000));
//                }
            });
        }
        if (pool.awaitTermination(20, TimeUnit.SECONDS)) {
            System.out.println("YES");
        } else {
            System.out.println("NO");
        }
        List<Runnable> l = pool.shutdownNow();
        System.out.println("A");
    }
}
