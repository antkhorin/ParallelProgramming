package ru.ifmo.mpp.hashmap;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import static ru.ifmo.mpp.hashmap.IntIntHashMap.RehashProgress.*;

/**
 * Int-to-Int hash map with open addressing and linear probes.
 *
 * @author Хорин.
 */
public class IntIntHashMap {
    private static final int MAGIC = 0x9E3779B9; // golden ratio
    private static final int INITIAL_CAPACITY = 2; // !!! DO NOT CHANGE INITIAL CAPACITY !!!
    private static final int MAX_PROBES = 8; // max number of probes to find an item

    private static final int NULL_KEY = 0; // missing key (initial value)
    private static final int NULL_VALUE = 0; // missing value (initial value)
    private static final int DEL_VALUE = Integer.MAX_VALUE; // mark for removed value
    private static final int NEEDS_REHASH = -1; // returned by putInternal to indicate that rehash is needed
    private static final int DONE_VALUE = Integer.MIN_VALUE;

    // Checks is the value is in the range of allowed values
    private static boolean isValue(int value) {
        return value > 0 && value < DEL_VALUE; // the range or allowed values
    }

    // Converts internal value to the public results of the methods
    private static int toValue(int value) {
        return isValue(value) ? value : 0;
    }

    private AtomicReference<Core> core = new AtomicReference<>(new Core(INITIAL_CAPACITY));

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
            Core oldCore;
            while (true) {
                oldCore = core.get();
                if (oldCore.rehashProgress.get() != FINISH)
                    break;
                core.compareAndSet(oldCore, oldCore.next.get());
            }
            int oldValue = core.get().putInternal(key, value);
            if (oldValue != NEEDS_REHASH)
                return oldValue;
            core.get().rehash();
        }
    }

    private static class Core {
        final AtomicIntegerArray map; // pairs of key, value here
        final int shift;
        final AtomicReference<Core> next = new AtomicReference<>();
        final AtomicReference<RehashProgress> rehashProgress = new AtomicReference<>(BEFORE);

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
            int anotherKey;
            while ((anotherKey = map.get(index)) != key) {
                if (anotherKey == NULL_KEY || ++probes >= MAX_PROBES)
                    return NULL_VALUE;
                if (index == 0)
                    index = map.length();
                index -= 2;
            }
            int value = map.get(index + 1);
            if (value == DONE_VALUE)
                return next.get().getInternal(key);
            return value & Integer.MAX_VALUE;
        }

        int putInternal(int key, int value) {
            int index = index(key);
            int probes = 0;
            int anotherKey;
            while ((anotherKey = map.get(index)) != key) {
                if (anotherKey == NULL_KEY) {
                    if (value == DEL_VALUE)
                        return NULL_VALUE;
                    if (map.compareAndSet(index, NULL_KEY, key) || map.get(index) == key)
                        break;
                }
                if (++probes >= MAX_PROBES)
                    return NEEDS_REHASH;
                if (index == 0)
                    index = map.length();
                index -= 2;
            }
            int oldValue;
            do {
                oldValue = map.get(index + 1);
                if (oldValue < 0) {
                    migrate(index);
                    int result = next.get().putInternal(key, value);
                    if (result == NEEDS_REHASH) {
                        System.out.println("PUT");
                        next.get().rehash();
                        return next.get().putInternal(key, value);
                    }
                    return result;
                }
            } while (!map.compareAndSet(index + 1, oldValue, value));
            return oldValue;
        }

        boolean putIfAbsent(int key, int value) {
            int index = index(key);
            int probes = 0;
            int anotherKey;
            while ((anotherKey = map.get(index)) != key) {
                if (anotherKey == NULL_KEY)
                    if (map.compareAndSet(index, NULL_KEY, key) || map.get(index) == key)
                        break;
                if (++probes >= MAX_PROBES)
                    return true;
                if (index == 0)
                    index = map.length();
                index -= 2;
            }
            if (!map.compareAndSet(index + 1, NULL_VALUE, value) && map.get(index + 1) == DONE_VALUE)
                if (next.get().putIfAbsent(key, value)) {
                    System.out.println("ABSENT");
                    next.get().rehash();
                    next.get().putIfAbsent(key, value);
                }
            return false;
        }

        void rehash() {
            if (rehashProgress.get() == BEFORE) {
                next.compareAndSet(null, new Core(map.length()));
                rehashProgress.compareAndSet(BEFORE, START);
            }
            for (int index = 0; index < map.length(); index += 2) {
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
                migrate(index);
            }
            rehashProgress.compareAndSet(START, FINISH);
        }

        void migrate(int index) {
            if (map.get(index + 1) == DONE_VALUE)
                return;
            int value;
            do {
                value = map.get(index + 1);
                if (value == DEL_VALUE)
                    if (map.compareAndSet(index + 1, DEL_VALUE, DONE_VALUE))
                        return;
            }
            while (value == DEL_VALUE || value >= 0 && !map.compareAndSet(index + 1, value, value | Integer.MIN_VALUE));
            value = map.get(index + 1);
            if (value != DONE_VALUE) {
                if (next.get().putIfAbsent(map.get(index), value & Integer.MAX_VALUE)) {
                    System.out.println("MIGRATE");
                    next.get().rehash();
                    next.get().putIfAbsent(map.get(index), value & Integer.MAX_VALUE);
                }
                map.set(index + 1, DONE_VALUE);
            }
        }

        /**
         * Returns an initial index in map to look for a given key.
         */
        int index(int key) {
            return ((key * MAGIC) >>> shift) * 2;
//            int capacity = 1 << (32 - shift);
//            return key % capacity * 2;
        }
    }

    enum RehashProgress {
        BEFORE,
        START,
        FINISH
    }
}