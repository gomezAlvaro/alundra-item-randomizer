package com.alundra.randomizer.tools;

/**
 * A 32-bit Mersenne Twister (MT19937) pseudo-random number generator, bit-for-bit identical to C++'s
 * {@code std::mt19937}. Using the exact same generator as the original C++ randomizer means a given seed and preset
 * produce the exact same world in both implementations.
 */
public final class MersenneTwister {
    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIX_A = 0x9908B0DF;
    private static final int UPPER_MASK = 0x80000000;
    private static final int LOWER_MASK = 0x7FFFFFFF;

    private final int[] mt = new int[N];
    private int index;

    public MersenneTwister(long seed) {
        mt[0] = (int) seed;
        for (int i = 1; i < N; ++i)
            mt[i] = 1812433253 * (mt[i - 1] ^ (mt[i - 1] >>> 30)) + i;
        index = N;
    }

    /** @return the next random 32-bit value, as an unsigned value in a {@code long} */
    public long next() {
        if (index >= N)
            twist();

        int y = mt[index++];
        y ^= (y >>> 11);
        y ^= (y << 7) & 0x9D2C5680;
        y ^= (y << 15) & 0xEFC60000;
        y ^= (y >>> 18);
        return y & 0xFFFFFFFFL;
    }

    private void twist() {
        for (int i = 0; i < N; ++i) {
            int y = (mt[i] & UPPER_MASK) | (mt[(i + 1) % N] & LOWER_MASK);
            int value = mt[(i + M) % N] ^ (y >>> 1);
            if ((y & 1) != 0)
                value ^= MATRIX_A;
            mt[i] = value;
        }
        index = 0;
    }
}
